package oathdigital.serialization

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.setup._
import oathdigital.model._
import oathdigital.setup.OathEvent.{FirstGameCompleted, WakeEnded,
  RestCompleted, RestStarted, SearchCompleted, SearchStarted, Traveled,
  WealthTaken}
import oathdigital.setup.FirstGameSetupFixture._

class GameEventWireSuite extends munit.FunSuite {
  private val rules = new FirstGameSetupRules(catalog)

  test("v5 Rest events round-trip all authoritative transition facts") {
    val events = Vector[OathEvent](
      RestStarted(PlayerId("red")),
      RestCompleted(PlayerId("red"), Map(Suit.Beast -> 2, Suit.Order -> 1),
        returnedSecrets = 3, refreshedSupply = 6, PlayerId("blue"), 2)
    )
    val encoded = GameEventWire.encodeStream("rest", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }).toOption.get
    val decoded = GameEventWire.decodeStream(encoded).toOption.get

    assertEquals(decoded.map(_.formatVersion), Vector(5, 5))
    assertEquals(decoded.map(_.event), events)
    assertEquals(decoded.map(_.eventType), Vector(
      GameEventWire.RestStartedType, GameEventWire.RestCompletedType))
  }

  test("v2 serialized replay equals command state and preserves ordering") {
    val (commandState, events) = execute(rules)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    val json = GameEventWire
      .encodeStream("first-game-001", catalogRef, records)
      .toOption
      .get
    val decoded = GameEventWire.decodeStream(json).toOption.get
    val replayRecords = decoded.map { envelope =>
      RecordedEvent(envelope.sequence, envelope.event)
    }

    assertEquals(decoded.map(_.formatVersion).distinct, Vector(2))
    assertEquals(
      decoded.map(_.eventType),
      Vector(
        GameEventWire.FirstGameStartedType,
        GameEventWire.PawnPlacedType,
        GameEventWire.AdviserChosenType,
        GameEventWire.PawnPlacedType,
        GameEventWire.AdviserChosenType,
        GameEventWire.PawnPlacedType,
        GameEventWire.AdviserChosenType,
        GameEventWire.FirstGameCompletedType
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
      GameEventWire
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
      GameEventWire
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

  test("decoded v2 streams reject duplicate missing and out-of-order positions") {
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
  }

  test("malformed unsupported and unknown v2 envelopes fail explicitly") {
    assert(GameEventWire.decodeStream("{").left.toOption.get
      .isInstanceOf[WireError.MalformedJson])

    val unsupported = completedValue()
    unsupported("formatVersion") = 1
    assertEquals(
      GameEventWire.decode(unsupported),
      Left(WireError.UnsupportedFormatVersion("$.formatVersion", 1, 2))
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
  }

  test("single-event and batch writers preserve nonzero absolute sequences") {
    val single = GameEventWire
      .encodeEvent("game", catalogRef, 41L, FirstGameCompleted)
      .toOption
      .get
    assertEquals(
      GameEventWire.decode(single).toOption.get.sequence,
      41L
    )

    val events = execute(rules)._2.take(2)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(41L + index, event)
    }
    val json = GameEventWire
      .encodeStream("game", catalogRef, 41L, records)
      .toOption
      .get
    val decoded = GameEventWire.decodeStream(json).toOption.get

    assertEquals(decoded.map(_.sequence), Vector(41L, 42L))
    assertEquals(decoded.map(_.event), events)
  }

  test("mixed contiguous v2 setup and v3 gameplay records round trip") {
    val setupEvents = execute(rules)._2
    val gameplay = Vector(
      WealthTaken(PlayerId("p2"), sites.head, WakeResource.Favor),
      WakeEnded(PlayerId("p2"))
    )
    val events = setupEvents ++ gameplay
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    val decoded = GameEventWire.decodeStream(
      GameEventWire.encodeStream("mixed", catalogRef, records)
        .toOption.get).toOption.get

    assertEquals(decoded.map(_.formatVersion),
      Vector.fill(setupEvents.size)(2) ++ Vector(3, 3))
    assertEquals(decoded.map(_.eventType).takeRight(2),
      Vector("gameplay.take-wealth", "gameplay.wake-ended"))
    assertEquals(decoded.map(_.event), events)

    val wrongVersion = GameEventWire.encodeEvent(
      "mixed", catalogRef, 8L, gameplay.head).toOption.get
    wrongVersion("formatVersion") = 2
    assert(GameEventWire.decode(wrongVersion).isLeft)
  }

  test("v3 traveled has exact discriminator payload and mixed compatibility") {
    val event = Traveled(
      PlayerId("p2"), SiteId("source"), SiteId("destination"), 3)
    val encoded = GameEventWire.encodeEvent(
      "travel", catalogRef, 10L, event).toOption.get
    assertEquals(encoded("formatVersion").num.toInt, 3)
    assertEquals(encoded("eventType").str, "gameplay.traveled")
    assertEquals(encoded("payload")("sourceSiteId").str, "source")
    assertEquals(encoded("payload")("destinationSiteId").str, "destination")
    assertEquals(encoded("payload")("supplySpent").num.toInt, 3)
    assertEquals(GameEventWire.decode(encoded).toOption.get.event, event)

    val events = execute(rules)._2 ++ Vector(
      WakeEnded(PlayerId("p2")), event)
    val records = events.zipWithIndex.map { case (value, index) =>
      RecordedEvent(index.toLong, value)
    }
    val decoded = GameEventWire.decodeStream(
      GameEventWire.encodeStream("travel", catalogRef, records)
        .toOption.get).toOption.get
    assertEquals(decoded.map(_.formatVersion).takeRight(2), Vector(3, 3))
    assertEquals(decoded.map(_.eventType).takeRight(2),
      Vector("gameplay.wake-ended", "gameplay.traveled"))

    encoded("payload")("supplySpent") = -1
    assert(GameEventWire.decode(encoded).isLeft)
  }

  test("v4 Search events round trip exact hidden outcome and player choices") {
    val drawn = Vector[WorldCardId](DenizenId("denizen:a"), VisionId("vision:b"))
    val started = SearchStarted(PlayerId("p2"), DecisionId("search-9"),
      SearchSource.WorldDeck, Region.Cradle, 3, drawn)
    val completed = SearchCompleted(PlayerId("p2"), DecisionId("search-9"),
      drawn.head, Vector(drawn(1)),
      SearchPlacement.Adviser(Orientation.FaceDown, None))
    val encoded = Vector(started, completed).zipWithIndex.map {
      case (event, index) => GameEventWire.encodeEvent(
        "search", catalogRef, 9L + index, event).toOption.get
    }
    assertEquals(encoded.map(_("formatVersion").num.toInt), Vector(4, 4))
    assertEquals(encoded.map(_("eventType").str),
      Vector("gameplay.search-started", "gameplay.search-completed"))
    assertEquals(encoded.map(value => GameEventWire.decode(value)
      .toOption.get.event), Vector(started, completed))
    val fixture = scala.io.Source.fromResource(
      "serialization/search-event-stream-v4.json").mkString.trim
    assertEquals(ujson.read(fixture), ujson.Arr.from(encoded))

    encoded.head("payload")("drawn")(0)("id") = "denizen:tampered"
    assert(GameEventWire.decode(encoded.head).isRight)
    // Wire decoding preserves the recorded outcome; authoritative replay is
    // responsible for rejecting disagreement with the deck.
  }

  test("format version and sequence reject fractional and nonfinite numbers") {
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
  }

  test("numeric boundaries reject negatives and unsafe integer overflow") {
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
        .encodeEvent("game", catalogRef, -1L, FirstGameCompleted)
        .isLeft
    )
    assert(
      GameEventWire
        .encodeEvent(
          "game",
          catalogRef,
          GameEventWire.MaxSafeSequence + 1L,
          FirstGameCompleted
        )
        .isLeft
    )

    val boundary = GameEventWire
      .encodeEvent(
        "game",
        catalogRef,
        GameEventWire.MaxSafeSequence,
        FirstGameCompleted
      )
      .toOption
      .get
    assertEquals(
      GameEventWire.decode(boundary).toOption.get.sequence,
      GameEventWire.MaxSafeSequence
    )
  }

  private def completedValue(): ujson.Value =
    GameEventWire
      .encodeEvent("game", catalogRef, 0L, FirstGameCompleted)
      .toOption
      .get
}
