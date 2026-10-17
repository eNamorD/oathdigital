package oathdigital.serialization

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog,
  setupThroughWake}
import oathdigital.model._
import oathdigital.model.OathEvent.{UsurperFlipped, UsurperVictory}
import oathdigital.serialization.WalkerWireEvents.encodedStream
import oathdigital.testkit.Unwrap.{accepted, as, rejected}

/** The event envelope: its fields, ordering, sequences and format version, and
  * the explicit failures for malformed, unsupported and unknown envelopes. */
class GameEventWireEnvelopeSuite extends munit.FunSuite:
  test("an envelope carries no catalog, and a stored catalog field is ignored"):
    val event = OathEvent.BanditsRefilled(Vector.empty)
    val encoded = ujson.read(accepted(
      GameEventWire.encodeEvent("game", 0L, event)))
    assert(!encoded.obj.contains("catalog"))
    val stored = ujson.copy(encoded)
    stored.obj("catalog") = ujson.Obj("ruleset" -> "oath-new-foundations",
      "version" -> "2026.08.29-pre5")
    assertEquals(GameEventWire.decode(stored).map(_.event), Right(event))

  test("serialized replay equals command state and preserves ordering"):
    // Replays the real setup: a wire round trip of a real journal is the
    // subject.
    val setup = setupThroughWake()
    val json = encodedStream("first-game-001", setup.events)
    val decoded = accepted(GameEventWire.decodeStream(json))
    val replayRecords = decoded.map { envelope =>
      RecordedEvent(envelope.sequence, envelope.event)
    }

    // Every event is written at format version 1, the one version the codec
    // supports.
    assertEquals(decoded.map(_.formatVersion).distinct, Vector(1))
    // Setup runs as an ordinary triggered walker procedure (2026-09-21
    // Chronicle design, slice 2): one `GameStarted`, then an interleaving of
    // `walker.step-recorded`/`walker.parked` facts for every player's pawn
    // placement and adviser choice, ending in `walker.completed` once the
    // tree reaches `BeginTurn(firstPlayer, Wake)`.
    val types = decoded.map(_.eventType)
    assertEquals(types.head, GameEventWire.GameStartedType)
    assertEquals(types.last, GameEventWire.WalkerCompletedType)
    assert(types.tail.init.forall(t =>
      t == GameEventWire.WalkerStepRecordedType ||
        t == GameEventWire.WalkerParkedType ||
        t == GameEventWire.PowerNotedType))
    assertEquals(
      new EventReplayEngine(new OathRules(catalog)).replay(replayRecords),
      Right(setup.state)
    )

  test("serialized events are rejected when their order is not contiguous"):
    // The real setup's events serve as a journal long enough to reorder.
    val events = setupThroughWake().events
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    // The second record is at position 3, where position 1 was expected.
    assertEquals(
      GameEventWire.encodeStream(
        "game",
        records.updated(1, records(1).copy(index = 3))
      ),
      Left(WireError.InvalidSequence("$[1].sequence", 1L, 3L))
    )

  test("decoded streams reject duplicate missing and out-of-order positions"):
    // InvalidSequence(path, expected position, position found).
    Vector(
      // The second record repeats position 0: position 1 expected, 0 found.
      Vector(0L, 0L) -> WireError.InvalidSequence("$[1].sequence", 1L, 0L),
      // Position 1 is missing: position 1 expected, 2 found.
      Vector(0L, 2L) -> WireError.InvalidSequence("$[1].sequence", 1L, 2L),
      // The stream starts at 1, so position 2 is expected next, 0 found.
      Vector(1L, 0L) -> WireError.InvalidSequence("$[1].sequence", 2L, 0L)
    ).foreach { case (positions, expected) =>
      val values = positions.map { sequence =>
        val value = encodedBanditsRefilled()
        value("sequence") = ujson.Num(sequence.toDouble)
        value
      }
      assertEquals(
        GameEventWire.decodeStream(ujson.write(ujson.Arr.from(values))),
        Left(expected)
      )
    }

  test("malformed unsupported and unknown envelopes fail explicitly"):
    // The message is the JSON parser's own wording, so only the case and the
    // path are pinned.
    assertEquals(as[WireError.MalformedJson](
      rejected(GameEventWire.decodeStream("{"))).path, "$")

    val unsupported = encodedBanditsRefilled()
    unsupported("formatVersion") = 2
    // Version 2 found, 1 supported.
    assertEquals(
      GameEventWire.decode(unsupported),
      Left(WireError.UnsupportedFormatVersion("$.formatVersion", 2, 1))
    )

    val unknown = encodedBanditsRefilled()
    unknown("eventType") = "scala.internal.Event"
    assertEquals(
      GameEventWire.decode(unknown),
      Left(WireError.UnknownEventType(
        "$.payload.eventType",
        "scala.internal.Event"
      ))
    )

  test("single-event and batch writers preserve nonzero absolute sequences"):
    // The real setup's first two events serve as a batch to number from 41.
    val single = accepted(GameEventWire
      .encodeEvent("game", 41L, OathEvent.BanditsRefilled(Vector.empty)))
    assertEquals(
      accepted(GameEventWire.decode(single)).sequence,
      41L
    )

    val events = setupThroughWake().events.take(2)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(41L + index, event)
    }
    val json = accepted(GameEventWire.encodeStream("game", 41L, records))
    val decoded = accepted(GameEventWire.decodeStream(json))

    assertEquals(decoded.map(_.sequence), Vector(41L, 42L))
    assertEquals(decoded.map(_.event), events)

  test("contiguous setup and gameplay records round trip at format " +
      "version 1"):
    // Replays the real setup: a wire round trip of a real journal is the
    // subject.
    val setupEvents = setupThroughWake().events
    // Any two gameplay events serve. They used to be the Wake phase's
    // own; the Wake phase no longer has any, since both taking wealth and
    // ending Wake are journalled as walker steps (Task 7).
    val gameplay = Vector(
      UsurperFlipped(PlayerId("p2")),
      UsurperVictory(PlayerId("p2"))
    )
    val events = setupEvents ++ gameplay
    val decoded = accepted(
      GameEventWire.decodeStream(encodedStream("mixed", events)))

    // Every event is written at format version 1, the one version the codec
    // supports.
    assertEquals(decoded.map(_.formatVersion), Vector.fill(events.size)(1))
    assertEquals(decoded.map(_.eventType).takeRight(2),
      Vector("gameplay.usurper-flipped", "gameplay.usurper-victory"))
    assertEquals(decoded.map(_.event), events)

  test("a gameplay event stamped with format version 2 is refused"):
    val wrongVersion = accepted(GameEventWire.encodeEvent(
      "mixed", 8L, UsurperFlipped(PlayerId("p2"))))
    wrongVersion("formatVersion") = 2
    // Version 2 found, 1 supported.
    assertEquals(GameEventWire.decode(wrongVersion),
      Left(WireError.UnsupportedFormatVersion("$.formatVersion", 2, 1)))

  test("format version and sequence reject fractional and nonfinite numbers"):
    Vector(2.9, Double.NaN, Double.PositiveInfinity).foreach { number =>
      val value = encodedBanditsRefilled()
      value("formatVersion") = ujson.Num(number)
      assertEquals(
        GameEventWire.decode(value),
        Left(
          WireError.WrongType(
            "$.formatVersion",
            "expected an integer"
          )
        )
      )
    }

    Vector(0.5, Double.NaN, Double.PositiveInfinity).foreach { number =>
      val value = encodedBanditsRefilled()
      value("sequence") = ujson.Num(number)
      assertEquals(
        GameEventWire.decode(value),
        Left(WireError.WrongType("$.sequence", "expected an integer"))
      )
    }

  test("numeric boundaries reject negatives and unsafe integer overflow"):
    val rangeMessage =
      s"must be between 0 and ${GameEventWire.MaxSafeSequence} inclusive"

    Vector(
      -1d,
      (GameEventWire.MaxSafeSequence + 1L).toDouble
    ).foreach { number =>
      val value = encodedBanditsRefilled()
      value("sequence") = ujson.Num(number)
      assertEquals(
        GameEventWire.decode(value),
        Left(WireError.InvalidValue("$.sequence", rangeMessage))
      )
    }

    val negativeVersion = encodedBanditsRefilled()
    negativeVersion("formatVersion") = ujson.Num(-1)
    assertEquals(
      GameEventWire.decode(negativeVersion),
      Left(WireError.InvalidValue("$.formatVersion", rangeMessage))
    )

    val overflowVersion = encodedBanditsRefilled()
    overflowVersion("formatVersion") =
      ujson.Num((GameEventWire.MaxSafeSequence + 1L).toDouble)
    assertEquals(
      GameEventWire.decode(overflowVersion),
      Left(WireError.InvalidValue("$.formatVersion", rangeMessage))
    )

    Vector(-1L, GameEventWire.MaxSafeSequence + 1L).foreach { sequence =>
      assertEquals(
        GameEventWire.encodeEvent("game", sequence,
          OathEvent.BanditsRefilled(Vector.empty)),
        Left(WireError.InvalidValue("$.sequence", rangeMessage))
      )
    }

    val boundary = accepted(
      GameEventWire.encodeEvent(
        "game",
        GameEventWire.MaxSafeSequence,
        OathEvent.BanditsRefilled(Vector.empty)
      )
    )
    assertEquals(
      accepted(GameEventWire.decode(boundary)).sequence,
      GameEventWire.MaxSafeSequence
    )

  private def encodedBanditsRefilled(): ujson.Value =
    accepted(GameEventWire
      .encodeEvent("game", 0L, OathEvent.BanditsRefilled(Vector.empty)))
