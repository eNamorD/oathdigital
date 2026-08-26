package oathdigital.protocol

class CommandProtocolSuite extends munit.FunSuite {
  import GameIntent._

  private val examples: Vector[GameIntent] = Vector(
    PlacePawn("site:a"), TakeWealth("favor"), EndWake, BeginRest, FinishRest,
    Travel("site:b"), Muster(EconomyTarget("denizen", "d1")),
    Trade(EconomyTarget("edifice", "e1"), "secret"),
    BeginSearch(SearchSource("world", None)), BeginRecover, BeginForge,
    CompleteForge("forge-1", Vector(ForgeAssignment("site:a", "d1", "favor"))),
    BeginChallenge("peoples-favor"), ChooseChallengeSecretSite("c1", "site:a"),
    CompleteChallenge("c1", 2), PlaceBannerResource("darkest-secret", 1),
    DiscardFacedownAdviser(WorldCard("denizen", "d1")),
    PlayFacedownAdviser(WorldCard("denizen", "d1"), Placement("site", None)),
    RevealVision("v1"), PlayConspiracy(Some(ConspiracyTarget.RelicSlot("p2", 0))),
    ChooseConspiracySecretSite("c2", "site:b"), PeekSiteRelics,
    RevealOwnedRelic("r1"), MoveWarbands(toSite = true, 2),
    BeginNegotiation(Vector("p2", "p3")),
    ReplaceNegotiationTerms("n1", NegotiationTerms(
      Vector(NegotiationTransfer("p2", 1, Vector("r1"))),
      Vector(NegotiationDisclosure("p2", NegotiationInformation.Adviser(
        "p1", WorldCard("denizen", "d1")))))),
    AcceptNegotiation("n1"), DeclineNegotiation("n1"), AddRecoverDice("r1"),
    StopRecover("r1"), BeginCampaignConquest(Vector("site:a", "site:b"), 3),
    BeginCampaignRaid(Vector(CampaignRaidTarget.Pawn("p2"),
      CampaignRaidTarget.Relic("p2", "r1"),
      CampaignRaidTarget.Banner("p2", "peoples-favor")), 3),
    ChooseCampaignPlan("cp1", CampaignPlanSource.Adviser("p1", "d1")),
    FinishCampaignPlans("cp1"), ChooseCampaignSacrifice("cp1", 1),
    PlaceCampaignForce("cp1", Vector(CampaignForceAllocation("site:a", 2))),
    RelocateCampaignRaidPawn("cp1", "site:c"),
    ChooseOathkeeperRecipient("o1", "p2"),
    ResolveCardDecision("d1", DecisionResolution.Search(
      WorldCard("vision", "v1"), Vector(WorldCard("denizen", "d2")),
      Placement("adviser-face-down", Some(CardRef("denizen", "d3")))))
  )

  test("every actorless command intent round trips through the shared codec") {
    examples.zipWithIndex.foreach { case (intent, index) =>
      val request = ActorlessCommandRequest(index.toLong, intent)
      assertEquals(ActorlessCommandCodec.decode(ActorlessCommandCodec.encode(request)),
        Right(request), intent.toString)
    }
  }

  test("actor injection is rejected at command identity fields") {
    Vector("playerId", "actor", "actorId", "actorPlayerId").foreach { field =>
      val json = s"""{"expectedNextSequence":0,"intent":{"type":"endWake","$field":"spoof"}}"""
      val failure = ActorlessCommandCodec.decode(json).left.toOption.get
      assertEquals(failure.path, s"$$.intent.$field")
      assert(failure.isInstanceOf[ProtocolDecodeFailure.ActorInjection])
    }
  }

  test("malformed fields and structural duplicates retain exact paths") {
    assertEquals(ActorlessCommandCodec.decode("{").left.toOption.get.path, "$")
    val missing = """{"expectedNextSequence":8,"intent":{"type":"travel"}}"""
    assertEquals(ActorlessCommandCodec.decode(missing).left.toOption.get.path,
      "$.intent.destinationSiteId")
    val duplicate = """{"expectedNextSequence":0,"intent":{"type":"beginNegotiation","participantPlayerIds":["p2","p2"]}}"""
    assertEquals(ActorlessCommandCodec.decode(duplicate).left.toOption.get.path,
      "$.intent.participantPlayerIds")
  }

  test("ordered modifier transport preserves click order and rejects duplicates") {
    val modifiers = Vector(
      ModifierInvocation("adviser", "d2", None, "denizen.second"),
      ModifierInvocation("site-card", "d1", Some("s1"), "denizen.first"))
    val request = ActorlessCommandRequest(9, Travel("s2"), modifiers)
    assertEquals(ActorlessCommandCodec.decode(ActorlessCommandCodec.encode(request)),
      Right(request))
    val duplicate = request.copy(orderedModifiers = Vector(modifiers.head,
      modifiers.head))
    assertEquals(ActorlessCommandCodec.decode(ActorlessCommandCodec.encode(duplicate))
      .left.toOption.get.path, "$.orderedModifiers")
  }

  test("major-action preview protocol is actorless and round trips a response") {
    val request = MajorActionPreviewCodec.decode(
      """{"expectedNextSequence":7,"action":"trade","baseParameters":{"resource":"favor"},"orderedModifiers":[]}""")
    assertEquals(request, Right(MajorActionPreviewRequest(7, "trade",
      Map("resource" -> "favor"))))
    val encoded = MajorActionPreviewCodec.encode(MajorActionPreviewResponse(7,
      "trade", Vector.empty, Vector.empty,
      Vector(PreviewTarget("denizen:d1", 1, "D1"))))
    assert(ujson.read(encoded)("targets").arr.nonEmpty)
  }
}
