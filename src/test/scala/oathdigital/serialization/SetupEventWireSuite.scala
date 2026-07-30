package oathdigital.serialization

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import oathdigital.catalog.{
  CatalogLoadRequest,
  CatalogLoader,
  CatalogSelection,
  ExecutableCatalog
}
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model.{CatalogRef, LineageId, PlayerId}
import oathdigital.serialization.WireError._
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupEvent
import oathdigital.setup.SetupEvent.{PawnPlaced, SetupCompleted, SetupStarted}
import oathdigital.setup.{SetupParticipant, SetupRules, SetupState}

class SetupEventWireSuite extends munit.FunSuite {
  private val catalogRef =
    CatalogRef("oath-new-foundations", "2026.07.27-pre2")
  private val catalog: ExecutableCatalog =
    CatalogLoader
      .load(
        Paths.get(
          "docs/catalog/new-foundations-component-catalog.json"
        ),
        CatalogLoadRequest(
          CatalogSelection(
            setupCards = true,
            supplyBoards = true,
            sites = true
          ),
          Some(catalogRef)
        )
      )
      .toOption
      .get
  private val rules = new SetupRules(catalog)
  private val participants = Vector(
    SetupParticipant(PlayerId("p1"), LineageId("l1")),
    SetupParticipant(PlayerId("p2"), LineageId("l2")),
    SetupParticipant(PlayerId("p3"), LineageId("l3"))
  )
  private val sites = catalog.sites.take(8).map(_.id)
  private val events: Vector[SetupEvent] = Vector(
    SetupStarted(participants, catalogRef, sites),
    PawnPlaced(PlayerId("p1"), sites(0)),
    PawnPlaced(PlayerId("p2"), sites(1)),
    PawnPlaced(PlayerId("p3"), sites(1)),
    SetupCompleted
  )
  private val recorded = events.zipWithIndex.map { case (event, index) =>
    RecordedEvent(index.toLong, event)
  }
  private val fixturePath =
    Paths.get("src/test/resources/serialization/setup-event-stream-v1.json")

  test("all setup event payloads round trip with ordered data intact") {
    val encoded = SetupEventWire
      .encodeStream("game-round-trip", catalogRef, recorded)
      .toOption
      .get
    val decoded = SetupEventWire.decodeStream(encoded).toOption.get

    assertEquals(decoded.map(_.sequence), Vector(0L, 1L, 2L, 3L, 4L))
    assertEquals(decoded.map(_.event), events)
    val started = decoded.head.event.asInstanceOf[SetupStarted]
    assertEquals(started.participants, participants)
    assertEquals(started.orderedSites, sites)
  }

  test("version one encoding exactly matches the checked-in golden JSON") {
    val encoded = SetupEventWire
      .encodeStream("game-golden-001", catalogRef, recorded)
      .toOption
      .get
    val golden =
      Files.readString(fixturePath, StandardCharsets.UTF_8).stripTrailing

    assertEquals(encoded, golden)
    assertEquals(
      SetupEventWire.decodeStream(golden).toOption.get.map(_.event),
      events
    )
  }

  test("malformed, missing, and wrong-typed JSON report structured paths") {
    assert(SetupEventWire.decodeStream("{").left.toOption.get
      .isInstanceOf[MalformedJson])

    val missing = baseEnvelope()
    missing.obj.remove("gameId")
    assertEquals(
      decodeOne(missing).left.toOption.get,
      MissingField("$[0].gameId", "field is required")
    )

    val wrong = baseEnvelope()
    wrong("sequence") = "zero"
    assertEquals(
      decodeOne(wrong).left.toOption.get,
      WrongType("$[0].sequence", "expected an integer")
    )

    val invalidId = baseEnvelope()
    invalidId("gameId") = " "
    assertEquals(
      decodeOne(invalidId).left.toOption.get,
      InvalidValue("$[0].gameId", "gameId must not be blank")
    )
  }

  test("unsupported versions and unknown event types fail explicitly") {
    val version = baseEnvelope()
    version("formatVersion") = 2
    assertEquals(
      decodeOne(version).left.toOption.get,
      UnsupportedFormatVersion("$[0].formatVersion", 2, 1)
    )

    val unknown = baseEnvelope()
    unknown("eventType") = "setup.scala.SetupStarted"
    assertEquals(
      decodeOne(unknown).left.toOption.get,
      UnknownEventType("$[0].eventType", "setup.scala.SetupStarted")
    )
  }

  test("sequence accepts the JSON safe-integer maximum on encode and decode") {
    val envelope = completedEnvelope(SetupEventWire.MaxSafeSequence)
    val encoded = SetupEventWire.encode(envelope).toOption.get

    assertEquals(
      encoded("sequence").num.toLong,
      SetupEventWire.MaxSafeSequence
    )
    assertEquals(SetupEventWire.decode(encoded), Right(envelope))
  }

  test("sequence rejects values above the JSON safe-integer maximum") {
    val overflow = SetupEventWire.MaxSafeSequence + 1
    val expected = InvalidValue(
      "$.sequence",
      s"must be between 0 and ${SetupEventWire.MaxSafeSequence} inclusive"
    )

    assertEquals(
      SetupEventWire.encode(completedEnvelope(overflow)),
      Left(expected)
    )

    val encoded = SetupEventWire
      .encode(completedEnvelope(SetupEventWire.MaxSafeSequence))
      .toOption
      .get
    encoded("sequence") = ujson.Num(overflow.toDouble)
    assertEquals(SetupEventWire.decode(encoded), Left(expected))
  }

  test("sequence rejects negative values on encode and decode") {
    val expected = InvalidValue(
      "$.sequence",
      s"must be between 0 and ${SetupEventWire.MaxSafeSequence} inclusive"
    )

    assertEquals(
      SetupEventWire.encode(completedEnvelope(-1)),
      Left(expected)
    )

    val encoded = SetupEventWire
      .encode(completedEnvelope(0))
      .toOption
      .get
    encoded("sequence") = ujson.Num(-1)
    assertEquals(SetupEventWire.decode(encoded), Left(expected))
  }

  test("envelope and SetupStarted payload catalogs cannot disagree") {
    val mismatch = baseEnvelope()
    mismatch("payload")("catalog")("version") = "other"

    assertEquals(
      decodeOne(mismatch).left.toOption.get,
      CatalogMismatch(
        "$[0].payload.catalog",
        catalogRef,
        CatalogRef(catalogRef.ruleset, "other")
      )
    )

    val mismatchedEvent: Vector[RecordedEvent[SetupEvent]] = recorded.updated(
      0,
      RecordedEvent[SetupEvent](
        0,
        SetupStarted(
          participants,
          CatalogRef(catalogRef.ruleset, "other"),
          sites
        )
      )
    )
    assert(
      SetupEventWire
        .encodeStream("game", catalogRef, mismatchedEvent)
        .left
        .toOption
        .get
        .isInstanceOf[CatalogMismatch]
    )
  }

  test("stream catalog and game identity are consistent") {
    val stream = ujson.read(
      SetupEventWire
        .encodeStream("game", catalogRef, recorded)
        .toOption
        .get
    )
    stream(1)("catalog")("version") = "other"
    assert(
      SetupEventWire
        .decodeStream(ujson.write(stream))
        .left
        .toOption
        .get
        .isInstanceOf[CatalogMismatch]
    )

    stream(1)("catalog")("version") = catalogRef.version
    stream(1)("gameId") = "another-game"
    assertEquals(
      SetupEventWire.decodeStream(ujson.write(stream)).left.toOption.get,
      InvalidValue(
        "$[1].gameId",
        "must match stream game ID 'game'"
      )
    )
  }

  test("duplicate, missing, and out-of-order sequence positions are rejected") {
    Vector(
      Vector(0L, 0L) -> InvalidSequence("$[1].sequence", 1, 0),
      Vector(0L, 2L) -> InvalidSequence("$[1].sequence", 1, 2),
      Vector(1L, 0L) -> InvalidSequence("$[0].sequence", 0, 1)
    ).foreach { case (positions, expected) =>
      val json = ujson.Arr.from(positions.map { position =>
        val envelope = baseEnvelope()
        envelope("sequence") = ujson.Num(position.toDouble)
        envelope
      })
      assertEquals(
        SetupEventWire.decodeStream(ujson.write(json)).left.toOption.get,
        expected
      )
    }

    val corruptRecords = Vector(
      RecordedEvent[SetupEvent](0, events.head),
      RecordedEvent[SetupEvent](2, events(1))
    )
    assertEquals(
      SetupEventWire
        .encodeStream("game", catalogRef, corruptRecords)
        .left
        .toOption
        .get,
      InvalidSequence("$[1].sequence", 1, 2)
    )
  }

  test("serialized event replay exactly reconstructs command-generated state") {
    val begin = BeginSetup(participants, catalogRef, sites)
    val started =
      rules.handle(SetupState.NotStarted, begin).toOption.get
    val commands = Vector(
      PlacePawn(PlayerId("p1"), sites(0)),
      PlacePawn(PlayerId("p2"), sites(1)),
      PlacePawn(PlayerId("p3"), sites(1))
    )
    val (commandState, emitted) =
      commands.foldLeft(started.state -> started.events) {
        case ((state, accumulated), command) =>
          val transition = rules.handle(state, command).toOption.get
          transition.state -> (accumulated ++ transition.events)
      }
    val commandRecords =
      emitted.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }
    val json = SetupEventWire
      .encodeStream("game-replay", catalogRef, commandRecords)
      .toOption
      .get
    val replayRecords = SetupEventWire
      .decodeStream(json)
      .toOption
      .get
      .map(envelope => RecordedEvent(envelope.sequence, envelope.event))

    assertEquals(
      new EventReplayEngine(rules).replay(replayRecords),
      Right(commandState)
    )
  }

  private def decodeOne(value: ujson.Value) =
    SetupEventWire.decodeStream(ujson.write(ujson.Arr(value)))

  private def completedEnvelope(sequence: Long): SetupEventEnvelope =
    SetupEventEnvelope(
      SetupEventWire.FormatVersion,
      "game",
      sequence,
      catalogRef,
      SetupEventWire.SetupCompletedType,
      SetupCompleted
    )

  private def baseEnvelope(): ujson.Value =
    ujson.Obj(
      "formatVersion" -> 1,
      "gameId" -> "game",
      "sequence" -> 0,
      "catalog" -> ujson.Obj(
        "ruleset" -> catalogRef.ruleset,
        "version" -> catalogRef.version
      ),
      "eventType" -> SetupEventWire.SetupStartedType,
      "payload" -> ujson.Obj(
        "participants" -> ujson.Arr(
          ujson.Obj("playerId" -> "p1", "lineageId" -> "l1")
        ),
        "catalog" -> ujson.Obj(
          "ruleset" -> catalogRef.ruleset,
          "version" -> catalogRef.version
        ),
        "orderedSites" -> ujson.Arr.from(
          sites.map(site => ujson.Str(site.value))
        )
      )
    )
}
