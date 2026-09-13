package oathdigital.protocol

class CommandProtocolSuite extends munit.FunSuite {
  import GameIntent._

  private val examples: Vector[GameIntent] = Vector(
    PlacePawn("site:a"), EndWake, BeginRest, FinishRest,
    ResolveRestPower("rest-1", Vector(RestFavorAllocation(
      RestFavorSource("relic-slot", "site:a", "0"), 2)), "hearth"),
    DeclineRestPower("rest-1"),
    Muster(EconomyTarget("denizen", "d1")),
    Trade(EconomyTarget("edifice", "e1"), "secret"),
    BeginSearch(SearchSource("world", None)),
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
    ResolveWalker("recover.choice",
      DecisionAnswerWire.ChooseOneWire("button", "continue")),
    ResolveWalker("recover.choice",
      DecisionAnswerWire.ChooseOneWire("button", "stop")),
    ResolveWalker("recover.relic",
      DecisionAnswerWire.ChooseOneWire("relic", "relic-1")),
    StartWalker("forge", Vector.empty),
    ResolveWalker("forge.assignment", DecisionAnswerWire.PartitionWire(
      Vector(DecisionPlacementWire("denizen", "d1", "pay-favor"),
        DecisionPlacementWire("denizen", "d2", "pay-favor"),
        DecisionPlacementWire("denizen", "d3", "pay-secret"))))
  )

  test("a walker answer carrying a deleted legacy tag is rejected") {
    val legacy = ujson.Obj("kind" -> "recover-choice", "choice" -> "continue")
    Vector("recover-choice", "recover-relic", "forge-assignment").foreach { tag =>
      val payload = legacy.value.toMap.updated("kind", ujson.Str(tag))
      assert(CommandNestedCodecs.decodeDecisionAnswerWire(
        ujson.Obj.from(payload), "$.payload").isLeft, tag)
    }
  }

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
    // Travel's destination moved onto `StartWalker`'s start selection
    // (batch-1 Task 5), so the path a malformed one reports moved with it --
    // still exact, and now indexed because a selection is a list.
    val missing = """{"expectedNextSequence":8,"intent":{"type":"startWalker",""" +
      """"action":"travel","modifiers":[],"startArgs":[{"optionKind":"site"}]}}"""
    assertEquals(ActorlessCommandCodec.decode(missing).left.toOption.get.path,
      "$.intent.startArgs[0].optionId")
    val duplicate = """{"expectedNextSequence":0,"intent":{"type":"beginNegotiation","participantPlayerIds":["p2","p2"]}}"""
    assertEquals(ActorlessCommandCodec.decode(duplicate).left.toOption.get.path,
      "$.intent.participantPlayerIds")
  }

  test("ordered modifier transport preserves click order and rejects duplicates") {
    val modifiers = Vector(
      ModifierInvocation("adviser", "d2", None, "denizen.second"),
      ModifierInvocation("site-card", "d1", Some("s1"), "denizen.first"))
    val request = ActorlessCommandRequest(9, StartWalker("travel",
      Vector.empty, Vector(WalkerStartArgWire("site", "s2"))), modifiers)
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
    assert(failure.message.contains("unknown decision answer"))
  }

  test("a partition payload placing one option twice is rejected at its " +
      "exact path") {
    val row = """{"optionKind":"denizen","optionId":"d1","sectionKey":"pay-favor"}"""
    val json = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker",""" +
      s""""decisionId":"forge.assignment","payload":{"kind":"partition",""" +
      s""""placements":[$row,$row]}}}"""
    val failure = ActorlessCommandCodec.decode(json).left.toOption.get
    assertEquals(failure.path, "$.intent.payload.placements")
    // The engine rejects a duplicated placement too (`DecisionQueries`);
    // catching it at the transport keeps the two rules the same shape.
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
