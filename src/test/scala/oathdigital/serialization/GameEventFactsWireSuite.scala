package oathdigital.serialization

import oathdigital.engine.RecordedEvent
import oathdigital.model._
import oathdigital.model.OathEvent.{RoundEnded, WarExhaustionResolved}
import oathdigital.gameplay.setup.FirstGameSetupFixture._

/** Single game facts on the wire: ignored rules, round end, victory, minor
  * actions and the GameStarted Chronicle. */
class GameEventFactsWireSuite extends munit.FunSuite:
  test("ignored-rule diagnostics round trip durable source timing and reason"):
    val event = OathEvent.IgnoredRulesRecorded(PlayerId("red"),
      ActionKind.Rest, Vector(IgnoredRuleDiagnostic(
        RuleSourceRef.Adviser(PlayerId("red"), DenizenId("insomnia")),
        "denizen.insomnia", ActionKind.Rest, RuleTiming.Trigger,
        "reviewed-unimplemented-pre-alpha-fallback")))
    val encoded = GameEventWire.encodeEvent("g", 0, event).toOption.get
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(event))

  test("v13 round ending and War Exhaustion preserve outcome and random domain"):
    val p1 = PlayerId("p1")
    val p2 = PlayerId("p2")
    val events = Vector[OathEvent](RoundEnded(8, None),
      WarExhaustionResolved(p2, VictoryKind.RandomSelection, None,
        Vector(p1, p2)))
    events.zipWithIndex.foreach { case (event, index) =>
      val encoded = GameEventWire.encodeEvent("war", index, event)
        .toOption.get
      val decoded = GameEventWire.decode(encoded).toOption.get
      assertEquals(decoded.formatVersion, GameEventWire.FormatVersion)
      assertEquals(decoded.event, event)
    }

  test("a Vision victory event round trips"):
    val events = Vector[OathEvent](OathEvent.VisionVictory(PlayerId("red"),
      VisionId("vision:vision-of-faith")))
    val encoded = GameEventWire.encodeStream("visions", events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)

  test("v10 minor actions preserve private identities and prior force facts"):
    val events = Vector[OathEvent](
      OathEvent.SiteRelicsPeeked(PlayerId("red"), SiteId("site:a"),
        Vector(RelicId("R1"), RelicId("R2"))),
      OathEvent.OwnedRelicRevealed(PlayerId("red"), RelicId("R3")),
      OathEvent.WarbandsMoved(PlayerId("red"), SiteId("site:a"),
        toSite = false, 2, 3, 4))
    val encoded = GameEventWire.encodeStream("minor", events.zipWithIndex.map { case (event, index) => RecordedEvent(index, event) })
      .toOption.get
    assertEquals(ujson.read(encoded).arr.map(_("formatVersion").num.toInt).toVector,
      Vector.fill(events.size)(1))
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event), events)

  test("GameStarted round trips its Chronicle and dealt SetupOrders"):
    val event = OathEvent.GameStarted(chronicle, orders)
    val encoded = GameEventWire.encodeEvent("goal", 0L, event)
      .toOption.get
    assertEquals(encoded("payload")("chronicle")("worldDeck").arr.size,
      chronicle.worldDeck.size)
    assertEquals(GameEventWire.decode(encoded).toOption.get.event, event)

    val invalid = GameEventWire.encodeEvent("goal", 0L, event)
      .toOption.get
    val storedIndex = chronicle.atlasBox.indexWhere(_.items.nonEmpty)
    invalid("payload")("chronicle")("atlasBox")(storedIndex)("items")(0)("kind") = "unknown"
    assert(GameEventWire.decode(invalid).left.toOption.exists {
      case WireError.InvalidValue(path, _) => path.endsWith(".kind")
      case _ => false
    })
