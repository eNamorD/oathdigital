package oathdigital.serialization

import oathdigital.gameplay.setup.FirstGameSetupFixture.{chronicle, orders}
import oathdigital.model._
import oathdigital.model.OathEvent.{RoundEnded, WarExhaustionResolved}
import oathdigital.serialization.WalkerWireEvents.{assertStreamRoundTrips,
  encodedStreamThatRoundTrips}
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Single game facts on the wire: ignored rules, round end, victory, minor
  * actions and the GameStarted Chronicle. */
class GameEventFactsWireSuite extends munit.FunSuite:
  test("ignored-rule diagnostics round trip durable source timing and reason"):
    val event = OathEvent.IgnoredRulesRecorded(PlayerId("red"),
      ActionKind.Rest, Vector(IgnoredRuleDiagnostic(
        RuleSourceRef.Adviser(PlayerId("red"), DenizenId("insomnia")),
        "denizen.insomnia", ActionKind.Rest, RuleTiming.Trigger,
        "reviewed-unimplemented-pre-alpha-fallback")))
    val encoded = accepted(GameEventWire.encodeEvent("g", 0, event))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("round ending and War Exhaustion preserve outcome and random domain"):
    val p1 = PlayerId("p1")
    val p2 = PlayerId("p2")
    val events = Vector[OathEvent](RoundEnded(8, None),
      WarExhaustionResolved(p2, VictoryKind.RandomSelection, None,
        Vector(p1, p2)))
    events.zipWithIndex.foreach { case (event, index) =>
      val encoded = accepted(GameEventWire.encodeEvent("war", index, event))
      val decoded = accepted(GameEventWire.decode(encoded))
      assertEquals(decoded.formatVersion, GameEventWire.FormatVersion)
      assertEquals(decoded.event, event)
    }

  test("a Vision victory event round trips"):
    val events = Vector[OathEvent](OathEvent.VisionVictory(PlayerId("red"),
      VisionId("vision:vision-of-faith")))
    assertStreamRoundTrips("visions", events)

  test("minor actions preserve private identities and prior force facts, " +
      "written at format version 1"):
    val events = Vector[OathEvent](
      OathEvent.SiteRelicsPeeked(PlayerId("red"), SiteId("site:a"),
        Vector(RelicId("R1"), RelicId("R2"))),
      OathEvent.OwnedRelicRevealed(PlayerId("red"), RelicId("R3")),
      OathEvent.WarbandsMoved(PlayerId("red"), SiteId("site:a"),
        toSite = false, 2, 3, 4))
    val encoded = encodedStreamThatRoundTrips("minor", events)
    // Every event is written at format version 1, the one version the codec
    // supports.
    assertEquals(
      ujson.read(encoded).arr.map(_("formatVersion").num.toInt).toVector,
      Vector.fill(events.size)(1))

  test("GameStarted round trips its Chronicle and dealt SetupOrders"):
    val event = OathEvent.GameStarted(chronicle, orders)
    val encoded = accepted(GameEventWire.encodeEvent("goal", 0L, event))
    assertEquals(encoded("payload")("chronicle")("worldDeck").arr.size,
      chronicle.worldDeck.size)
    assertEquals(accepted(GameEventWire.decode(encoded)).event, event)

  test("GameStarted refuses an unknown stored item kind with a typed " +
      "WireError"):
    val event = OathEvent.GameStarted(chronicle, orders)
    val invalid = accepted(GameEventWire.encodeEvent("goal", 0L, event))
    val storedIndex = chronicle.atlasBox.indexWhere(_.items.nonEmpty)
    val storedBox = invalid("payload")("chronicle")("atlasBox")(storedIndex)
    storedBox("items")(0)("kind") = "unknown"
    val failure = rejected(GameEventWire.decode(invalid))
    assert(failure match {
      case WireError.InvalidValue(path, _) => path.endsWith(".kind")
      case _ => false
    }, failure.toString)
