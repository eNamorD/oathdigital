package oathdigital.serialization

import oathdigital.gameplay.setup.FirstGameSetupFixture.{chronicle, orders}
import oathdigital.model._
import oathdigital.model.OathEvent.{RoundEnded, WarExhaustionResolved}
import oathdigital.serialization.WalkerWireEvents.{assertStreamRoundTrips,
  encodedStreamThatRoundTrips}
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Single game facts on the wire: round end, victory, state-based events and
  * the GameStarted Chronicle. */
class GameEventFactsWireSuite extends munit.FunSuite:
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

  test("state-based events are written at format version 1"):
    val events = Vector[OathEvent](
      OathEvent.BanditsRefilled(Vector(SiteId("site:a") -> 1)),
      OathEvent.UsurperFlipped(PlayerId("red")))
    val encoded = encodedStreamThatRoundTrips("state-based", events)
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
