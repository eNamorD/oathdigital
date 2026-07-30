package oathdigital.application

import java.nio.file.Paths

import oathdigital.catalog.{
  CatalogLoadRequest,
  CatalogLoader,
  CatalogSelection,
  ExecutableCatalog
}
import oathdigital.model.{CatalogRef, LineageId, PlayerId}
import oathdigital.serialization.{
  SetupEventEnvelope,
  SetupEventWire
}
import oathdigital.serialization.WireError.MalformedJson
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupEvent.{PawnPlaced, SetupCompleted, SetupStarted}
import oathdigital.setup.{SetupEvent, SetupParticipant}
import oathdigital.setup.SetupState.{Completed, InProgress}
import oathdigital.setup.SetupViolation.WrongPlayer

class SetupApplicationServiceSuite extends munit.FunSuite {
  private val catalogRef =
    CatalogRef("oath-new-foundations", "2026.07.27-pre2")
  private val catalog: ExecutableCatalog =
    CatalogLoader
      .load(
        Paths.get("docs/catalog/new-foundations-component-catalog.json"),
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
  private val sites = catalog.sites.take(8).map(_.id)
  private val participants = Vector(
    SetupParticipant(PlayerId("p1"), LineageId("l1")),
    SetupParticipant(PlayerId("p2"), LineageId("l2")),
    SetupParticipant(PlayerId("p3"), LineageId("l3"))
  )
  private val begin = BeginSetup(participants, catalogRef, sites)

  private def encode(
      gameId: String,
      sequence: Long,
      event: SetupEvent
  ): String = {
    val eventType = event match {
      case _: SetupStarted => SetupEventWire.SetupStartedType
      case _: PawnPlaced => SetupEventWire.PawnPlacedType
      case SetupCompleted => SetupEventWire.SetupCompletedType
    }
    ujson.write(
      SetupEventWire
        .encode(SetupEventEnvelope(
          SetupEventWire.FormatVersion,
          gameId,
          sequence,
          catalogRef,
          eventType,
          event
        ))
        .toOption
        .get
    )
  }

  test("creates a stream and handles a later command from replayed state") {
    val repository = new InMemoryEventStreamRepository
    val service = new SetupApplicationService(catalog, repository)

    val created = service.handle("game-1", begin).toOption.get
    assertEquals(created.nextSequence, 1L)
    assert(created.state.isInstanceOf[InProgress])

    val placed =
      service.handle("game-1", PlacePawn(PlayerId("p1"), sites.head))
        .toOption
        .get
    assertEquals(
      placed.emittedEvents,
      Vector(PawnPlaced(PlayerId("p1"), sites.head))
    )
    assertEquals(placed.nextSequence, 2L)
    assertEquals(
      repository.load("game-1").toOption.flatten.get.nextSequence,
      2L
    )
  }

  test("a fresh service reconstructs state solely from stored events") {
    val repository = new InMemoryEventStreamRepository
    new SetupApplicationService(catalog, repository)
      .handle("game-replay", begin)
    new SetupApplicationService(catalog, repository)
      .handle("game-replay", PlacePawn(PlayerId("p1"), sites(0)))

    val result = new SetupApplicationService(catalog, repository)
      .handle("game-replay", PlacePawn(PlayerId("p2"), sites(1)))
      .toOption
      .get
    val state = result.state.asInstanceOf[InProgress]

    assertEquals(state.pawnPlacements.map(_.playerId),
      Vector(PlayerId("p1"), PlayerId("p2")))
  }

  test("invalid commands append no records") {
    val repository = new InMemoryEventStreamRepository
    val service = new SetupApplicationService(catalog, repository)
    service.handle("game-invalid", begin)
    val before = repository.load("game-invalid").toOption.flatten.get.records

    val rejected =
      service.handle("game-invalid", PlacePawn(PlayerId("p2"), sites.head))

    assert(rejected.left.toOption.get
      .isInstanceOf[SetupApplicationError.CommandRejected])
    assertEquals(
      repository.load("game-invalid").toOption.flatten.get.records,
      before
    )
  }

  test("creation semantics distinguish missing streams and duplicate games") {
    val repository = new InMemoryEventStreamRepository
    val service = new SetupApplicationService(catalog, repository)

    assertEquals(
      service.handle("missing", PlacePawn(PlayerId("p1"), sites.head)),
      Left(SetupApplicationError.StreamNotFound("missing"))
    )
    service.handle("duplicate", begin)
    assertEquals(
      service.handle("duplicate", begin),
      Left(SetupApplicationError.DuplicateGame("duplicate"))
    )
  }

  test("a concurrent append is returned as a typed sequence conflict") {
    val underlying = new InMemoryEventStreamRepository
    val initial = new SetupApplicationService(catalog, underlying)
    initial.handle("game-conflict", begin)

    val racing = new EventStreamRepository {
      private var raced = false

      override def load(gameId: String) = underlying.load(gameId)

      override def append(
          gameId: String,
          expected: ExpectedStream,
          records: Vector[String]
      ) = {
        if (!raced) {
          raced = true
          underlying.append(
            gameId,
            ExpectedStream.AtNextSequence(1),
            records
          )
        }
        underlying.append(gameId, expected, records)
      }
    }

    assertEquals(
      new SetupApplicationService(catalog, racing)
        .handle("game-conflict", PlacePawn(PlayerId("p1"), sites.head)),
      Left(SetupApplicationError.SequenceConflict(1L, 2L))
    )
  }

  test("malformed stored events fail decoding before command handling") {
    val repository = new InMemoryEventStreamRepository
    repository.seed("game-malformed", Vector("{"))

    val result = new SetupApplicationService(catalog, repository)
      .handle("game-malformed", PlacePawn(PlayerId("p1"), sites.head))

    assert(result.left.toOption.get match {
      case SetupApplicationError.DecodeFailure(_: MalformedJson) => true
      case _ => false
    })
    assertEquals(
      repository.load("game-malformed").toOption.flatten.get.records,
      Vector("{")
    )
  }

  test("repository stream identity mismatch fails without append") {
    var appendCalls = 0
    val repository = new EventStreamRepository {
      override def load(gameId: String) =
        Right(Some(StoredEventStream(
          "game-b",
          Vector(encode("game-b", 0, SetupStarted(
            participants,
            catalogRef,
            sites
          )))
        )))

      override def append(
          gameId: String,
          expected: ExpectedStream,
          records: Vector[String]
      ) = {
        appendCalls += 1
        Right(RepositoryAppendResult.Appended(1, records.size))
      }
    }

    assertEquals(
      new SetupApplicationService(catalog, repository)
        .handle("game-a", PlacePawn(PlayerId("p1"), sites.head)),
      Left(SetupApplicationError.RepositoryStreamIdentityMismatch(
        "game-a",
        "game-b"
      ))
    )
    assertEquals(appendCalls, 0)
  }

  test("decoded envelope identity mismatch fails without append") {
    var appendCalls = 0
    val repository = new EventStreamRepository {
      override def load(gameId: String) =
        Right(Some(StoredEventStream(
          gameId,
          Vector(encode("game-b", 0, SetupStarted(
            participants,
            catalogRef,
            sites
          )))
        )))

      override def append(
          gameId: String,
          expected: ExpectedStream,
          records: Vector[String]
      ) = {
        appendCalls += 1
        Right(RepositoryAppendResult.Appended(1, records.size))
      }
    }

    assertEquals(
      new SetupApplicationService(catalog, repository)
        .handle("game-a", PlacePawn(PlayerId("p1"), sites.head)),
      Left(SetupApplicationError.EventStreamIdentityMismatch(
        "game-a",
        "game-b"
      ))
    )
    assertEquals(appendCalls, 0)
  }

  test("append acknowledgment reports a count mismatch distinctly") {
    val repository = new EventStreamRepository {
      override def load(gameId: String) = Right(None)

      override def append(
          gameId: String,
          expected: ExpectedStream,
          records: Vector[String]
      ) = Right(RepositoryAppendResult.Appended(0L, 2))
    }

    assertEquals(
      new SetupApplicationService(catalog, repository)
        .handle("game-count", begin),
      Left(SetupApplicationError.AppendCountMismatch(1, 2))
    )
  }

  test("load and append storage failures remain typed") {
    val loadFailure = new EventStreamRepository {
      override def load(gameId: String) =
        Left(RepositoryFailure.StorageFailure("load unavailable"))
      override def append(
          gameId: String,
          expected: ExpectedStream,
          records: Vector[String]
      ) = fail("append must not be called after load failure")
    }
    assertEquals(
      new SetupApplicationService(catalog, loadFailure)
        .handle("game-storage", begin),
      Left(SetupApplicationError.StorageFailure("load unavailable"))
    )

    val appendFailure = new EventStreamRepository {
      override def load(gameId: String) = Right(None)
      override def append(
          gameId: String,
          expected: ExpectedStream,
          records: Vector[String]
      ) = Left(RepositoryFailure.StorageFailure("append unavailable"))
    }
    assertEquals(
      new SetupApplicationService(catalog, appendFailure)
        .handle("game-storage", begin),
      Left(SetupApplicationError.StorageFailure("append unavailable"))
    )
  }

  test("semantically invalid stored events fail replay without append") {
    val repository = new InMemoryEventStreamRepository
    val started = encode(
      "game-corrupt",
      0,
      SetupStarted(participants, catalogRef, sites)
    )
    val wrongPawn =
      encode("game-corrupt", 1, PawnPlaced(PlayerId("p2"), sites.head))
    repository.seed("game-corrupt", Vector(started, wrongPawn))
    val before =
      repository.load("game-corrupt").toOption.flatten.get.records

    assertEquals(
      new SetupApplicationService(catalog, repository)
        .handle("game-corrupt", PlacePawn(PlayerId("p1"), sites.head)),
      Left(SetupApplicationError.ReplayFailure(
        1L,
        WrongPlayer(PlayerId("p1"), PlayerId("p2"))
      ))
    )
    assertEquals(
      repository.load("game-corrupt").toOption.flatten.get.records,
      before
    )
  }

  test("final command appends its ordered two-event batch atomically") {
    val repository = new InMemoryEventStreamRepository
    val service = new SetupApplicationService(catalog, repository)
    service.handle("game-complete", begin)
    service.handle("game-complete", PlacePawn(PlayerId("p1"), sites.head))
    service.handle("game-complete", PlacePawn(PlayerId("p2"), sites.head))

    val completed = service
      .handle("game-complete", PlacePawn(PlayerId("p3"), sites.head))
      .toOption
      .get

    assertEquals(
      completed.emittedEvents,
      Vector(PawnPlaced(PlayerId("p3"), sites.head), SetupCompleted)
    )
    assertEquals(completed.nextSequence, 5L)
    assert(completed.state.isInstanceOf[Completed])
    assertEquals(
      repository.load("game-complete").toOption.flatten.get.nextSequence,
      5L
    )
  }
}
