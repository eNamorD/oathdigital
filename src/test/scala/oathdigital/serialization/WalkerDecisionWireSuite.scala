package oathdigital.serialization

import oathdigital.model._
import oathdigital.gameplay.walker.{ChoicePayload, FollowUpSkipped,
  FollowUpStarted, WalkerCompleted, WalkerParked, WalkerStepRecorded}
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, DistributeAnswer,
  PartitionAnswer}
import oathdigital.serialization.WalkerWireEvents.{assertStreamRoundTrips,
  encodedStream, encodedStreamThatRoundTrips}
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Unwrap.accepted

/** Walker decision answers on the wire: every answer and option reference kind
  * round trips on a recorded step and on a park, and deleted legacy tags are
  * refused. */
class WalkerDecisionWireSuite extends munit.FunSuite:
  test("a choose-many answer round trips through the journal"):
    val player = PlayerId("red")
    val answer = DecisionAnswer.ChooseManyAnswer(Vector(
      DecisionOptionRef.Site(SiteId("a")), DecisionOptionRef.Site(SiteId("c"))))
    val events = Vector[OathEvent](WalkerStepRecorded("1",
      ChoicePayload("challenge.ribbon-site", answer, player), Vector.empty,
      Vector.empty))
    assertStreamRoundTrips("forge", events)

  test("a choose-amount answer round trips through the journal"):
    val player = PlayerId("red")
    val events = Vector[OathEvent](WalkerStepRecorded("2",
      ChoicePayload("challenge.amount", DecisionAnswer.ChooseAmountAnswer(4),
        player), Vector.empty, Vector.empty))
    assertStreamRoundTrips("forge", events)

  test("walker choice steps round trip the negotiation answers"):
    val red = PlayerId("red"); val blue = PlayerId("blue")
    val terms = NegotiationTerms(
      Vector(NegotiationTransfer(blue, 2, Vector(RelicId("r1")))),
      Vector(
        NegotiationDisclosure(blue,
          NegotiationDisclosureRef.Adviser(red, VisionId("v1"))),
        NegotiationDisclosure(blue,
          NegotiationDisclosureRef.HeldRelic(red, RelicId("r2"))),
        NegotiationDisclosure(blue,
          NegotiationDisclosureRef.SiteRelic(SiteId("s1"), RelicId("r3")))))
    val events = Vector[DecisionAnswer](DecisionAnswer.ProposeTerms(terms),
      DecisionAnswer.AcceptDeal, DecisionAnswer.DeclineDeal).map(answer =>
      WalkerStepRecorded("0.0.0", ChoicePayload("negotiation.deal", answer,
        red), Vector.empty, Vector.empty): OathEvent)
    assertStreamRoundTrips("forge", events)

  test("every generic walker decision answer round trips on the step and on " +
      "the park"):
    // The answer fact rides a walker `ChoicePayload` and the parked
    // `answered` vector, so both directions of `DecisionAnswerCodec` are
    // reached by any journalled walker procedure.
    val player = PlayerId("red")
    val partition = PartitionAnswer(Vector("1", "2", "3")
      .map(id => DecisionOptionRef.Denizen(DenizenId(s"denizen:$id")))
      .zip(Vector("pay-favor", "pay-secret", "pay-favor"))
      .map { case (option, section) => DecisionPlacement(option, section) })
    val chooseOne = ChooseOneAnswer(
      DecisionOptionRef.Relic(CatalogNames.relic("Sticky Fire")))
    val distribute = DistributeAnswer(Vector(
      DistributeAmount(DecisionOptionRef.FavorBank(Suit.Arcane), 0),
      DistributeAmount(DecisionOptionRef.FavorBank(Suit.Nomad), 3)))
    val answered = Vector(Answered("forge.assignment", partition, player),
      Answered("recover.relic", chooseOne, player),
      Answered("league-treaty.distribute", distribute, player))
    val events = Vector[OathEvent](
      WalkerStepRecorded("1",
        ChoicePayload("forge.assignment", partition, player), Vector.empty,
        Vector.empty),
      WalkerStepRecorded("2",
        ChoicePayload("recover.relic", chooseOne, player), Vector.empty,
        Vector.empty),
      WalkerStepRecorded("3",
        ChoicePayload("league-treaty.distribute", distribute, player),
        Vector.empty, Vector.empty),
      WalkerParked(ActionRef.Forge, Vector("2"), answered,
        Vector.empty, Vector.empty))
    assertStreamRoundTrips("forge", events)

  test("a parked Travel round trips its start selection"):
    // Batch-1 Task 5: the only action that makes a start selection does not
    // park, so this park is synthetic -- it exists to round-trip the field,
    // which no production journal exercises yet.
    val events = Vector[OathEvent](
      WalkerParked(ActionRef.Travel, Vector("0"), Vector.empty,
        Vector.empty, Vector(DecisionOptionRef.Site(SiteId("site:dest")))))
    assertStreamRoundTrips("forge", events)

  test("a park round trips its start facts, and a park without them reads " +
      "as having none"):
    val park = WalkerParked(ActionRef.Travel, Vector("0"), Vector.empty,
      Vector.empty, Vector.empty)
    assert(encodedStreamThatRoundTrips("facts", Vector[OathEvent](
      park.copy(startFacts = Vector(DecisionOptionRef.Site(
        SiteId("site:source")))))).contains("\"startFacts\""))
    def dropFacts(value: ujson.Value): ujson.Value = value match
      case ujson.Obj(fields) => ujson.Obj.from(fields.toSeq.collect:
        case (key, inner) if key != "startFacts" => key -> dropFacts(inner))
      case ujson.Arr(items) => ujson.Arr.from(items.map(dropFacts))
      case other => other
    val withoutFacts = ujson.write(dropFacts(ujson.read(
      encodedStream("no-facts", Vector[OathEvent](park)))))
    assert(!withoutFacts.contains("startFacts"), withoutFacts)
    assertEquals(accepted(GameEventWire.decodeStream(withoutFacts))
      .map(_.event), Vector[OathEvent](park))

  test("a procedure reference round-trips with its family, and a reference " +
      "under the wrong family or an unknown family is rejected"):
    val events = Vector[OathEvent](
      WalkerParked(PhaseTransitionRef.EndWake, Vector("0"), Vector.empty,
        Vector.empty, Vector.empty),
      WalkerCompleted(ActionRef.Travel),
      // The triggered family (item 10 of the final fix brief): no
      // production journal exercised it here before, so this is the only
      // place the Oathkeeper procedure's wire spelling is pinned.
      WalkerParked(TriggeredProcedureRef.Oathkeeper, Vector("0"),
        Vector.empty, Vector.empty, Vector.empty),
      WalkerCompleted(TriggeredProcedureRef.Oathkeeper),
      WalkerCompleted(ActionRef.UsePower(PowerId("denizen.silver-tongue"))))
    val encoded = encodedStreamThatRoundTrips("families", events)
    assert(ujson.read(encoded).arr.head.toString.contains(
      "\"family\":\"phase-transition\""))
    assert(ujson.read(encoded).arr(2).toString.contains(
      "\"family\":\"triggered\""))

    Vector("action", "made-up").foreach { family =>
      // `encodeStream` writes with `indent = 2` (a space after the colon),
      // unlike the compact re-serialization the assertion above checks --
      // the tamper has to match the string actually on the wire here.
      val tampered = encoded.replace("\"family\": \"phase-transition\"",
        s"""\"family\": \"$family\"""")
      assertEquals(GameEventWire.decodeStream(tampered),
        Left(WireError.InvalidValue("$[0].payload.procedure",
          s"unknown walker procedure '$family/end-wake'")), family)
    }

  test("a follow-up's start and skip round trip with their entry"):
    val entry = FollowUp(PlayerId("p1"), ActionRef.Travel,
      PowerSourceRef.Card(CatalogNames.denizen("Second Wind")))
    val encoded = encodedStreamThatRoundTrips("follow-ups", Vector[OathEvent](
      FollowUpStarted(entry), FollowUpSkipped(entry)))
    assert(encoded.contains("\"walker.follow-up-started\""), encoded)
    assert(encoded.contains("\"walker.follow-up-skipped\""), encoded)

  test("a follow-up naming something other than an action is refused"):
    val entry = FollowUp(PlayerId("p1"), ActionRef.Travel,
      PowerSourceRef.Card(CatalogNames.denizen("Second Wind")))
    val encoded = encodedStreamThatRoundTrips("follow-up-family",
      Vector[OathEvent](FollowUpStarted(entry)))
    val tampered = encoded.replace("\"family\": \"action\"",
      "\"family\": \"phase-transition\"").replace("\"key\": \"travel\"",
      "\"key\": \"end-wake\"")
    assertEquals(GameEventWire.decodeStream(tampered).left.toOption,
      Some(WireError.InvalidValue("$[0].payload.followUp.action",
        "a follow-up is an action, not end-wake")))

  test("every option reference kind round trips through a recorded answer"):
    val player = PlayerId("red")
    val refs = Vector[DecisionOptionRef](
      DecisionOptionRef.Button("continue"),
      DecisionOptionRef.Player(PlayerId("blue")),
      DecisionOptionRef.Site(SiteId("site:s1")),
      DecisionOptionRef.Denizen(DenizenId("denizen:d1")),
      DecisionOptionRef.Relic(CatalogNames.relic("Sticky Fire")),
      DecisionOptionRef.Vision(VisionId("vision:v1")),
      DecisionOptionRef.Edifice(
        CatalogNames.edifice("Hall of Ministers")),
      DecisionOptionRef.Deck(CardDeck.Relic),
      DecisionOptionRef.FavorBank(Suit.Hearth),
      DecisionOptionRef.RelicSlot(PlayerId("blue"), 0),
      DecisionOptionRef.AdviserSlot(PlayerId("blue"), 1),
      DecisionOptionRef.SiteSlot(SiteId("site:s1"), 2),
      DecisionOptionRef.AdviserPlay(PlayerId("blue"), 0, faceup = true),
      DecisionOptionRef.AdviserPlay(PlayerId("blue"), 2, faceup = false),
      DecisionOptionRef.RevealedVision(PlayerId("blue")),
      DecisionOptionRef.DiscardPile(Region.Hinterland),
      DecisionOptionRef.Banner(Banner.PeoplesFavor))
    val events = refs.zipWithIndex.map { case (ref, index) =>
      WalkerStepRecorded(index.toString,
        ChoicePayload("d", ChooseOneAnswer(ref), player), Vector.empty,
        Vector.empty): OathEvent }
    assertStreamRoundTrips("refs", events)

  test("a journalled answer carrying a deleted legacy tag is rejected with a " +
      "typed decode failure"):
    val player = PlayerId("red")
    val event: OathEvent = WalkerStepRecorded("1",
      ChoicePayload("recover.choice",
        ChooseOneAnswer(DecisionOptionRef.Button("continue")), player),
      Vector.empty, Vector.empty)
    val encoded = encodedStream("legacy", Vector(event))
    Vector("recover-choice", "recover-relic", "forge-assignment").foreach { tag =>
      val mutated = ujson.read(encoded).arr
      mutated.head("payload")("step")("payload")("kind") = tag
      assertEquals(GameEventWire.decodeStream(ujson.write(mutated)),
        Left(WireError.InvalidValue("$[0].payload.step.payload.kind",
          s"unknown walker decision answer '$tag'")), tag)
    }
