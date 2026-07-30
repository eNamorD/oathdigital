package oathdigital.serialization

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.setup._
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
}
