package oathdigital.protocol

class CommandProtocolSuite extends munit.FunSuite {
  import GameIntent._

  private val examples: Vector[GameIntent] = Vector(
    PlacePawn("site:a"), TakeWealth("favor"), EndWake, BeginRest, FinishRest,
    ResolveRestPower("rest-1", Vector(RestFavorAllocation(
      RestFavorSource("relic-slot", "site:a", "0"), 2)), "hearth"),
    DeclineRestPower("rest-1"),
    Travel("site:b"), Muster(EconomyTarget("denizen", "d1")),
    Trade(EconomyTarget("edifice", "e1"), "secret"),
    BeginSearch(SearchSource("world", None)), BeginForge,
    CompleteForge("forge-1", Vector(ForgeAssignment("site:a", "d1", "favor"))),
    BeginChallenge("peoples-favor"), ChooseChallengeSecretSite("c1", "site:a"),
    CompleteChallenge("c1", 2), PlaceBannerResource("darkest-secret", 1),
    ResolveFacedownAdviser(WorldCard("denizen", "d1"), None),
    ResolveFacedownAdviser(WorldCard("denizen", "d1"),
      Some(Placement("adviser-face-up", None))),
    RevealVision("v1"), PlayConspiracy(Some(ConspiracyTarget.RelicSlot("p2", 0))),
    PeekSiteRelics,
    RevealOwnedRelic("r1"), MoveWarbands(toSite = true, 2),
    BeginNegotiation(Vector("p2", "p3")),
    ReplaceNegotiationTerms("n1", NegotiationTerms(
      Vector(NegotiationTransfer("p2", 1, Vector("r1"))),
      Vector(NegotiationDisclosure("p2", NegotiationInformation.Adviser(
        "p1", WorldCard("denizen", "d1")))))),
    AcceptNegotiation("n1"), DeclineNegotiation("n1"),
    BeginCampaignConquest(Vector("site:a", "site:b"), 3),
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
      Placement("adviser-face-down", Some(CardRef("denizen", "d3"))))),
    StartWalker("recover", Vector.empty),
    StartWalker("recover", Vector("denizen.catacombs")),
    RollWalker("recover.pool"),
    ResolveWalker("recover.choice", DecisionPayloadWire.RecoverChoiceWire("continue")),
    ResolveWalker("recover.choice", DecisionPayloadWire.RecoverChoiceWire("stop")),
    ResolveWalker("recover.relic", DecisionPayloadWire.RecoverRelicWire("relic-1")),
    StartWalker("forge", Vector.empty),
    ResolveWalker("forge.assignment", DecisionPayloadWire.ForgeAssignmentWire(
      Vector(ForgeAssignment("site:a", "d1", "favor"),
        ForgeAssignment("site:a", "d2", "favor"),
        ForgeAssignment("site:a", "d3", "secret"))))
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

  test("retired facedown adviser wire intents are rejected") {
    Vector(
      """{"expectedNextSequence":0,"intent":{"type":"discardFacedownAdviser","adviser":{"kind":"denizen","id":"d1"}}}""",
      """{"expectedNextSequence":0,"intent":{"type":"playFacedownAdviser","adviser":{"kind":"denizen","id":"d1"},"placement":{"kind":"site","replace":null}}}"""
    ).foreach { json =>
      val failure = ActorlessCommandCodec.decode(json).left.toOption.get
      assertEquals(failure.path, "$.intent.type")
      assert(failure.message.contains("unknown intent type"))
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

  test("an unknown decision-payload kind decodes to a typed error, not an exception") {
    val json = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker",""" +
      """"decisionId":"recover.choice","payload":{"kind":"recover-teleport"}}}"""
    val failure = ActorlessCommandCodec.decode(json).left.toOption.get
    assertEquals(failure.path, "$.intent.payload.kind")
    assert(failure.isInstanceOf[ProtocolDecodeFailure.InvalidValue])
    assert(failure.message.contains("unknown decision payload"))
  }

  test("a Forge assignment payload naming one denizen twice is rejected at " +
      "its exact path") {
    val row = """{"siteId":"site:a","denizenId":"d1","resource":"favor"}"""
    val json = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker",""" +
      s""""decisionId":"forge.assignment","payload":{"kind":"forge-assignment",""" +
      s""""assignments":[$row,$row]}}}"""
    val failure = ActorlessCommandCodec.decode(json).left.toOption.get
    assertEquals(failure.path, "$.intent.payload.assignments")
    // The engine rejects a duplicated target too (`ForgeProcedure`'s
    // `Decide.validate`); catching it here keeps the transport's own
    // duplicate rule the same shape as `completeForge`'s was.
    assert(failure.isInstanceOf[ProtocolDecodeFailure.InvalidValue])
  }

  test("an unknown walker intent type is rejected without throwing") {
    val json = """{"expectedNextSequence":0,"intent":{"type":"teleportWalker"}}"""
    val failure = ActorlessCommandCodec.decode(json).left.toOption.get
    assertEquals(failure.path, "$.intent.type")
    assert(failure.message.contains("unknown intent type"))
  }

  test("major-action preview protocol is actorless and round trips a response") {
    val request = MajorActionPreviewRequest(7, "trade",
      Map("resource" -> "favor"), Vector(
        ModifierInvocation("adviser", "d2", None, "denizen.second")))
    assertEquals(MajorActionPreviewCodec.decode(
      MajorActionPreviewCodec.encodeRequest(request)), Right(request))
    val response = MajorActionPreviewResponse(7, "trade",
      Vector(PreviewModifier("adviser:p1:d2", "denizen.second", "Second")),
      Vector(PreviewIgnoredRule("site-card:s1:d1", "denizen.first", "start",
        "not implemented")), Vector(PreviewTarget("denizen:d1", 1, "D1")))
    assertEquals(MajorActionPreviewCodec.decodeResponse(
      MajorActionPreviewCodec.encode(response)), Right(response))
  }
}
