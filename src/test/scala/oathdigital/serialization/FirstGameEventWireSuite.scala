package oathdigital.serialization

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupEvent.FirstGameCompleted
import oathdigital.setup.FirstGameSetupFixture._

class FirstGameEventWireSuite extends munit.FunSuite {
  private val rules = new FirstGameSetupRules(catalog)

  test("v2 serialized replay equals command state and preserves ordering") {
    val (commandState, events) = execute(rules)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    val json = FirstGameEventWire
      .encodeStream("first-game-001", catalogRef, records)
      .toOption
      .get
    val decoded = FirstGameEventWire.decodeStream(json).toOption.get
    val replayRecords = decoded.map { envelope =>
      RecordedEvent(envelope.sequence, envelope.event)
    }

    assertEquals(decoded.map(_.formatVersion).distinct, Vector(2))
    assertEquals(
      decoded.map(_.eventType),
      Vector(
        FirstGameEventWire.FirstGameStartedType,
        FirstGameEventWire.PawnPlacedType,
        FirstGameEventWire.AdviserChosenType,
        FirstGameEventWire.PawnPlacedType,
        FirstGameEventWire.AdviserChosenType,
        FirstGameEventWire.PawnPlacedType,
        FirstGameEventWire.AdviserChosenType,
        FirstGameEventWire.FirstGameCompletedType
      )
    )
    assertEquals(
      new EventReplayEngine(rules).replay(replayRecords),
      Right(commandState)
    )
  }

  test("v2 rejects catalog disagreement and non-contiguous order") {
    val events = execute(rules)._2
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    assert(
      FirstGameEventWire
        .encodeStream(
          "game",
          catalogRef.copy(version = "other"),
          records
        )
        .left
        .toOption
        .get
        .isInstanceOf[WireError.CatalogMismatch]
    )
    assert(
      FirstGameEventWire
        .encodeStream(
          "game",
          catalogRef,
          records.updated(1, records(1).copy(index = 3))
        )
        .left
        .toOption
        .get
        .isInstanceOf[WireError.InvalidSequence]
    )
  }

  test("single-event and batch writers preserve nonzero absolute sequences") {
    val single = FirstGameEventWire
      .encodeEvent("game", catalogRef, 41L, FirstGameCompleted)
      .toOption
      .get
    assertEquals(
      FirstGameEventWire.decode(single).toOption.get.sequence,
      41L
    )

    val events = execute(rules)._2.take(2)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(41L + index, event)
    }
    val json = FirstGameEventWire
      .encodeStream("game", catalogRef, 41L, records)
      .toOption
      .get
    val decoded = FirstGameEventWire.decodeStream(json).toOption.get

    assertEquals(decoded.map(_.sequence), Vector(41L, 42L))
    assertEquals(decoded.map(_.event), events)
  }

  test("format version and sequence reject fractional and nonfinite numbers") {
    Vector(2.9, Double.NaN, Double.PositiveInfinity).foreach { number =>
      val value = completedValue()
      value("formatVersion") = ujson.Num(number)
      assertEquals(
        FirstGameEventWire.decode(value),
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
        FirstGameEventWire.decode(value),
        Left(WireError.WrongType("$.sequence", "expected an integer"))
      )
    }
  }

  test("numeric boundaries reject negatives and unsafe integer overflow") {
    val rangeMessage =
      s"must be between 0 and ${FirstGameEventWire.MaxSafeSequence} inclusive"

    Vector(
      -1d,
      (FirstGameEventWire.MaxSafeSequence + 1L).toDouble
    ).foreach { number =>
      val value = completedValue()
      value("sequence") = ujson.Num(number)
      assertEquals(
        FirstGameEventWire.decode(value),
        Left(WireError.InvalidValue("$.sequence", rangeMessage))
      )
    }

    val negativeVersion = completedValue()
    negativeVersion("formatVersion") = ujson.Num(-1)
    assertEquals(
      FirstGameEventWire.decode(negativeVersion),
      Left(WireError.InvalidValue("$.formatVersion", rangeMessage))
    )

    val overflowVersion = completedValue()
    overflowVersion("formatVersion") =
      ujson.Num((FirstGameEventWire.MaxSafeSequence + 1L).toDouble)
    assertEquals(
      FirstGameEventWire.decode(overflowVersion),
      Left(WireError.InvalidValue("$.formatVersion", rangeMessage))
    )

    assert(
      FirstGameEventWire
        .encodeEvent("game", catalogRef, -1L, FirstGameCompleted)
        .isLeft
    )
    assert(
      FirstGameEventWire
        .encodeEvent(
          "game",
          catalogRef,
          FirstGameEventWire.MaxSafeSequence + 1L,
          FirstGameCompleted
        )
        .isLeft
    )

    val boundary = FirstGameEventWire
      .encodeEvent(
        "game",
        catalogRef,
        FirstGameEventWire.MaxSafeSequence,
        FirstGameCompleted
      )
      .toOption
      .get
    assertEquals(
      FirstGameEventWire.decode(boundary).toOption.get.sequence,
      FirstGameEventWire.MaxSafeSequence
    )
  }

  private def completedValue(): ujson.Value =
    FirstGameEventWire
      .encodeEvent("game", catalogRef, 0L, FirstGameCompleted)
      .toOption
      .get
}
