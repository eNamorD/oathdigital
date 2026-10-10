package oathdigital.protocol

import oathdigital.testkit.Unwrap.rejected

class CommandProtocolSuite extends munit.FunSuite:
  import GameIntent._

  private val examples: Vector[GameIntent] = Vector(
    Undo, Redo,
    RollWalker("recover.pool"),
    ResolveWalker("recover.choice",
      DecisionAnswerWire.ChooseOneWire("button", "continue")),
    ResolveWalker("recover.choice",
      DecisionAnswerWire.ChooseOneWire("button", "stop")),
    ResolveWalker("recover.relic",
      DecisionAnswerWire.ChooseOneWire("relic", "relic-1")),
    ResolveWalker("forge.assignment", DecisionAnswerWire.PartitionWire(
      Vector(DecisionPlacementWire("denizen", "d1", "pay-favor"),
        DecisionPlacementWire("denizen", "d2", "pay-favor"),
        DecisionPlacementWire("denizen", "d3", "pay-secret")))),
    ResolveWalker("rest.distribution", DecisionAnswerWire.DistributeWire(Vector(
      DistributeAmountWire("favor-bank", "arcane", 0),
      DistributeAmountWire("favor-bank", "nomad", 3)))),
    ResolveWalker("challenge.ribbon-site", DecisionAnswerWire.ChooseManyWire(
      Vector(DecisionOptionWire("site", "a"), DecisionOptionWire("site", "c")))),
    ResolveWalker("challenge.amount", DecisionAnswerWire.ChooseAmountWire(4)),
    ResolveWalker("negotiation.deal", DecisionAnswerWire.ProposeTermsWire(
      NegotiationTerms(Vector(NegotiationTransfer("blue", 2, Vector("r1"))),
        Vector(NegotiationDisclosure("blue",
          NegotiationInformation.HeldRelic("red", "r2")))))),
    ResolveWalker("negotiation.deal", DecisionAnswerWire.AcceptDealWire),
    ResolveWalker("negotiation.deal", DecisionAnswerWire.DeclineDealWire),
    RequestConsent("travel.destination", WalkerStartArgWire("site",
      "site:deep-woods")),
    AnswerConsent("always"),
    SetConsentPreference("site.narrow-pass.pass", "blue", "case-by-case")
  )

  test("a walker answer carrying a deleted legacy tag is rejected"):
    val legacy = ujson.Obj("kind" -> "recover-choice", "choice" -> "continue")
    Vector("recover-choice", "recover-relic", "forge-assignment").foreach { tag =>
      val payload = legacy.value.toMap.updated("kind", ujson.Str(tag))
      assertEquals(CommandNestedCodecs.decodeDecisionAnswerWire(
        ujson.Obj.from(payload), "$.payload"),
        Left(ProtocolDecodeFailure.InvalidValue("$.payload.kind",
          s"unknown decision answer '$tag'")), tag)
    }

  test("every actorless command intent round trips through the shared codec"):
    examples.zipWithIndex.foreach { case (intent, index) =>
      val request = ActorlessCommandRequest(index.toLong, intent)
      assertEquals(ActorlessCommandCodec.decode(ActorlessCommandCodec.encode(request)),
        Right(request), intent.toString)
    }

  test("actor injection is rejected at command identity fields"):
    Vector("playerId", "actor", "actorId", "actorPlayerId").foreach { field =>
      val json = s"""{"expectedNextSequence":0,"intent":{"type":"undo","$field":"spoof"}}"""
      assertEquals(rejected(ActorlessCommandCodec.decode(json)),
        ProtocolDecodeFailure.ActorInjection(s"$$.intent.$field"), field)
    }

  test("the retired Campaign and start intents are rejected as unknown types"):
    Vector("beginCampaignConquest", "beginCampaignRaid", "chooseCampaignPlan",
      "finishCampaignPlans", "chooseCampaignSacrifice", "placeCampaignForce",
      "relocateCampaignRaidPawn", "startWalker", "usePower", "beginRest")
      .foreach { tag =>
      val json = s"""{"expectedNextSequence":0,"intent":{"type":"$tag"}}"""
      val failure = rejected(ActorlessCommandCodec.decode(json))
      assertEquals(failure.path, "$.intent.type", tag)
      assert(failure.message.contains("unknown intent type"), tag)
    }

  test("retired facedown adviser wire intents are rejected"):
    Vector(
      """{"expectedNextSequence":0,"intent":{"type":"beginSearch","source":"world","region":null}}""",
      """{"expectedNextSequence":0,"intent":{"type":"resolveFacedownAdviser","adviser":{"kind":"denizen","id":"d1"},"placement":null}}""",
      """{"expectedNextSequence":0,"intent":{"type":"discardFacedownAdviser","adviser":{"kind":"denizen","id":"d1"}}}""",
      """{"expectedNextSequence":0,"intent":{"type":"playFacedownAdviser","adviser":{"kind":"denizen","id":"d1"},"placement":{"kind":"site","replace":null}}}"""
    ).foreach { json =>
      val failure = rejected(ActorlessCommandCodec.decode(json))
      assertEquals(failure.path, "$.intent.type")
      assert(failure.message.contains("unknown intent type"))
    }

  test("malformed fields and structural duplicates retain exact paths"):
    assertEquals(rejected(ActorlessCommandCodec.decode("{")).path, "$")
    // A consent target is a one-element list on the wire, so the path of a
    // malformed one is indexed.
    val missing = """{"expectedNextSequence":8,"intent":{"type":"requestConsent",""" +
      """"decisionId":"travel.destination","target":{"optionKind":"site"}}}"""
    assertEquals(rejected(ActorlessCommandCodec.decode(missing)).path,
      "$.intent.target[0].optionId")
    val duplicate = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker","decisionId":"negotiation.deal","payload":{"kind":"propose-terms","terms":{"transfers":[{"recipientPlayerId":"p2","favor":1,"relicIds":[]},{"recipientPlayerId":"p2","favor":2,"relicIds":[]}],"disclosures":[]}}}}"""
    assertEquals(rejected(ActorlessCommandCodec.decode(duplicate)).path,
      "$.intent.payload.terms.transfers")

  test("an unknown decision-payload kind decodes to a typed error, not an exception"):
    val json = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker",""" +
      """"decisionId":"recover.choice","payload":{"kind":"recover-teleport"}}}"""
    assertEquals(rejected(ActorlessCommandCodec.decode(json)),
      ProtocolDecodeFailure.InvalidValue("$.intent.payload.kind",
        "unknown decision answer 'recover-teleport'"))

  test("a partition payload placing one option twice is rejected at its " +
      "exact path"):
    val row = """{"optionKind":"denizen","optionId":"d1","sectionKey":"pay-favor"}"""
    val json = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker",""" +
      s""""decisionId":"forge.assignment","payload":{"kind":"partition",""" +
      s""""placements":[$row,$row]}}}"""
    // The engine rejects a duplicated placement too (`DecisionQueries`);
    // catching it at the transport keeps the two rules the same shape.
    assertEquals(rejected(ActorlessCommandCodec.decode(json)),
      ProtocolDecodeFailure.InvalidValue("$.intent.payload.placements",
        "duplicate value 'denizen/d1'"))

  test("a choose-many wire answer rejects duplicate options and unknown keys"):
    val duplicate = ujson.Obj("kind" -> "choose-many", "options" -> ujson.Arr(
      ujson.Obj("optionKind" -> "site", "optionId" -> "a"),
      ujson.Obj("optionKind" -> "site", "optionId" -> "a")))
    assertEquals(CommandNestedCodecs.decodeDecisionAnswerWire(duplicate, "$"),
      Left(ProtocolDecodeFailure.InvalidValue("$.options",
        "duplicate value 'site/a'")))
    val extra = ujson.Obj("kind" -> "choose-amount", "amount" -> 2, "x" -> 1)
    assertEquals(CommandNestedCodecs.decodeDecisionAnswerWire(extra, "$"),
      Left(ProtocolDecodeFailure.UnexpectedField("$.x")))

  test("a distribute payload naming one option twice is rejected at its path"):
    val row = """{"optionKind":"favor-bank","optionId":"nomad","amount":1}"""
    val json = """{"expectedNextSequence":0,"intent":{"type":"resolveWalker",""" +
      """"decisionId":"d","payload":{"kind":"distribute",""" +
      s""""amounts":[$row,$row]}}}"""
    assertEquals(rejected(ActorlessCommandCodec.decode(json)).path,
      "$.intent.payload.amounts")

  test("an unknown walker intent type is rejected without throwing"):
    val json = """{"expectedNextSequence":0,"intent":{"type":"teleportWalker"}}"""
    val failure = rejected(ActorlessCommandCodec.decode(json))
    assertEquals(failure.path, "$.intent.type")
    assert(failure.message.contains("unknown intent type"))
