package oathdigital.serialization

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.setup._
import oathdigital.model._
import oathdigital.model.OathEvent.{UsurperFlipped, UsurperVictory}
import oathdigital.gameplay.setup.FirstGameSetupFixture._

/** The event envelope: its fields, ordering, sequences and format version, and
  * the explicit failures for malformed, unsupported and unknown envelopes. */
class GameEventWireEnvelopeSuite extends munit.FunSuite:
  test("an envelope carries no catalog, and a stored catalog field is ignored"):
    val event = OathEvent.BanditsRefilled(Vector.empty)
    val encoded = ujson.read(GameEventWire.encodeEvent("game", 0L, event)
      .toOption.get)
    assert(!encoded.obj.contains("catalog"))
    val stored = ujson.copy(encoded)
    stored.obj("catalog") = ujson.Obj("ruleset" -> "oath-new-foundations",
      "version" -> "2026.08.29-pre5")
    assertEquals(GameEventWire.decode(stored).map(_.event), Right(event))

  test("v2 serialized replay equals command state and preserves ordering"):
    val SetupResult(commandState, events) = setupThroughWake()
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    val json = GameEventWire
      .encodeStream("first-game-001", records)
      .toOption
      .get
    val decoded = GameEventWire.decodeStream(json).toOption.get
    val replayRecords = decoded.map { envelope =>
      RecordedEvent(envelope.sequence, envelope.event)
    }

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
        t == GameEventWire.WalkerParkedType))
    assertEquals(
      new EventReplayEngine(new OathRules(catalog)).replay(replayRecords),
      Right(commandState)
    )

  test("v2 rejects non-contiguous order"):
    val events = setupThroughWake().events
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    assert(
      GameEventWire
        .encodeStream(
          "game",
          records.updated(1, records(1).copy(index = 3))
        )
        .left
        .toOption
        .get
        .isInstanceOf[WireError.InvalidSequence]
    )

  test("decoded v2 streams reject duplicate missing and out-of-order positions"):
    Vector(
      Vector(0L, 0L) -> WireError.InvalidSequence("$[1].sequence", 1L, 0L),
      Vector(0L, 2L) -> WireError.InvalidSequence("$[1].sequence", 1L, 2L),
      Vector(1L, 0L) -> WireError.InvalidSequence("$[1].sequence", 2L, 0L)
    ).foreach { case (positions, expected) =>
      val values = positions.map { sequence =>
        val value = completedValue()
        value("sequence") = ujson.Num(sequence.toDouble)
        value
      }
      assertEquals(
        GameEventWire.decodeStream(ujson.write(ujson.Arr.from(values))),
        Left(expected)
      )
    }

  test("malformed unsupported and unknown v2 envelopes fail explicitly"):
    assert(GameEventWire.decodeStream("{").left.toOption.get
      .isInstanceOf[WireError.MalformedJson])

    val unsupported = completedValue()
    unsupported("formatVersion") = 2
    assertEquals(
      GameEventWire.decode(unsupported),
      Left(WireError.UnsupportedFormatVersion("$.formatVersion", 2, 1))
    )

    val unknown = completedValue()
    unknown("eventType") = "scala.internal.Event"
    assertEquals(
      GameEventWire.decode(unknown),
      Left(WireError.UnknownEventType(
        "$.payload.eventType",
        "scala.internal.Event"
      ))
    )

  test("single-event and batch writers preserve nonzero absolute sequences"):
    val single = GameEventWire
      .encodeEvent("game", 41L, OathEvent.BanditsRefilled(Vector.empty))
      .toOption
      .get
    assertEquals(
      GameEventWire.decode(single).toOption.get.sequence,
      41L
    )

    val events = setupThroughWake().events.take(2)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(41L + index, event)
    }
    val json = GameEventWire
      .encodeStream("game", 41L, records)
      .toOption
      .get
    val decoded = GameEventWire.decodeStream(json).toOption.get

    assertEquals(decoded.map(_.sequence), Vector(41L, 42L))
    assertEquals(decoded.map(_.event), events)

  test("mixed contiguous v2 setup and v3 gameplay records round trip"):
    val setupEvents = setupThroughWake().events
    // Any two v3 gameplay events serve. They used to be the Wake phase's
    // own; the Wake phase no longer has any, since both taking wealth and
    // ending Wake are journalled as walker steps (Task 7).
    val gameplay = Vector(
      UsurperFlipped(PlayerId("p2")),
      UsurperVictory(PlayerId("p2"))
    )
    val events = setupEvents ++ gameplay
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    val decoded = GameEventWire.decodeStream(
      GameEventWire.encodeStream("mixed", records)
        .toOption.get).toOption.get

    assertEquals(decoded.map(_.formatVersion), Vector.fill(events.size)(1))
    assertEquals(decoded.map(_.eventType).takeRight(2),
      Vector("gameplay.usurper-flipped", "gameplay.usurper-victory"))
    assertEquals(decoded.map(_.event), events)

    val wrongVersion = GameEventWire.encodeEvent(
      "mixed", 8L, gameplay.head).toOption.get
    wrongVersion("formatVersion") = 2
    assert(GameEventWire.decode(wrongVersion).isLeft)

  test("format version and sequence reject fractional and nonfinite numbers"):
    Vector(2.9, Double.NaN, Double.PositiveInfinity).foreach { number =>
      val value = completedValue()
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
      val value = completedValue()
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
      val value = completedValue()
      value("sequence") = ujson.Num(number)
      assertEquals(
        GameEventWire.decode(value),
        Left(WireError.InvalidValue("$.sequence", rangeMessage))
      )
    }

    val negativeVersion = completedValue()
    negativeVersion("formatVersion") = ujson.Num(-1)
    assertEquals(
      GameEventWire.decode(negativeVersion),
      Left(WireError.InvalidValue("$.formatVersion", rangeMessage))
    )

    val overflowVersion = completedValue()
    overflowVersion("formatVersion") =
      ujson.Num((GameEventWire.MaxSafeSequence + 1L).toDouble)
    assertEquals(
      GameEventWire.decode(overflowVersion),
      Left(WireError.InvalidValue("$.formatVersion", rangeMessage))
    )

    assert(
      GameEventWire
        .encodeEvent("game", -1L, OathEvent.BanditsRefilled(Vector.empty))
        .isLeft
    )
    assert(
      GameEventWire
        .encodeEvent(
          "game",
          GameEventWire.MaxSafeSequence + 1L,
          OathEvent.BanditsRefilled(Vector.empty)
        )
        .isLeft
    )

    val boundary = GameEventWire
      .encodeEvent(
        "game",
        GameEventWire.MaxSafeSequence,
        OathEvent.BanditsRefilled(Vector.empty)
      )
      .toOption
      .get
    assertEquals(
      GameEventWire.decode(boundary).toOption.get.sequence,
      GameEventWire.MaxSafeSequence
    )

  private def completedValue(): ujson.Value =
    GameEventWire
      .encodeEvent("game", 0L, OathEvent.BanditsRefilled(Vector.empty))
      .toOption
      .get
