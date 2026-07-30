package oathdigital.application

import java.nio.file.Paths

import oathdigital.catalog.{
  CatalogLoadRequest,
  CatalogLoader,
  CatalogSelection,
  ExecutableCatalog
}
import oathdigital.model.{CatalogRef, LineageId, PlayerId}
import oathdigital.serialization.WireError.MalformedJson
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupEvent.{PawnPlaced, SetupCompleted}
import oathdigital.setup.SetupParticipant
import oathdigital.setup.SetupState.{Completed, InProgress}

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
