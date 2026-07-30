package oathdigital.setup

import java.nio.file.Paths

import oathdigital.catalog.{
  CatalogLoadRequest,
  CatalogLoader,
  CatalogSelection,
  ExecutableCatalog
}
import oathdigital.engine.{
  EventAppendResult,
  EventReplayEngine,
  InMemoryEventJournal,
  RecordedEvent
}
import oathdigital.model.{CatalogRef, LineageId, PlayerId, SiteId}
import oathdigital.setup.SetupCommand.{BeginSetup, PlacePawn}
import oathdigital.setup.SetupContinue.{AwaitingPawn, Finished}
import oathdigital.setup.SetupEvent.{
  PawnPlaced,
  SetupCompleted,
  SetupStarted
}
import oathdigital.setup.SetupState.{Completed, InProgress, NotStarted}
import oathdigital.setup.SetupViolation._

class SetupRulesSuite extends munit.FunSuite {
  private val expectedCatalog =
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
          Some(expectedCatalog)
        )
      )
      .toOption
      .get
  private val rules = new SetupRules(catalog)
  private val sites = catalog.sites.take(8).map(_.id)
  private val participants = Vector(
    SetupParticipant(PlayerId("p1"), LineageId("l1")),
    SetupParticipant(PlayerId("p2"), LineageId("l2")),
    SetupParticipant(PlayerId("p3"), LineageId("l3"))
  )
  private val begin = BeginSetup(participants, expectedCatalog, sites)

  test("successful start pins catalog, participants, and a 2/3/3 layout") {
    val transition = rules.handle(NotStarted, begin).toOption.get

    assertEquals(transition.events, Vector(SetupStarted(
      participants,
      expectedCatalog,
      sites
    )))
    assertEquals(transition.continue, AwaitingPawn(PlayerId("p1")))
    val progress = transition.state.asInstanceOf[InProgress]
    assertEquals(progress.layout.cradle, sites.take(2))
    assertEquals(progress.layout.provinces, sites.slice(2, 5))
    assertEquals(progress.layout.hinterland, sites.slice(5, 8))
  }

  test("malformed layouts and participants remain typed violations") {
    assertEquals(
      rules.handle(
        NotStarted,
        begin.copy(participants = Vector.empty)
      ),
      Left(ParticipantsEmpty)
    )
    assertEquals(
      rules.handle(
        NotStarted,
        begin.copy(participants = participants :+ participants.head)
      ),
      Left(DuplicatePlayer(PlayerId("p1")))
    )
    assertEquals(
      rules.handle(
        NotStarted,
        begin.copy(
          participants = participants.updated(
            1,
            SetupParticipant(PlayerId("other"), LineageId("l1"))
          )
        )
      ),
      Left(DuplicateLineage(LineageId("l1")))
    )
    assertEquals(
      rules.handle(NotStarted, begin.copy(orderedSites = sites.take(7))),
      Left(InvalidSiteCount(7))
    )
    assertEquals(
      rules.handle(
        NotStarted,
        begin.copy(orderedSites = sites.updated(7, sites.head))
      ),
      Left(DuplicateSite(sites.head))
    )
    assertEquals(
      rules.handle(
        NotStarted,
        begin.copy(catalog = CatalogRef("other", "version"))
      ),
      Left(IncompatibleCatalog(
        expectedCatalog,
        CatalogRef("other", "version")
      ))
    )
  }

  test("placement validates actor and known/in-play destination") {
    val progress = rules.handle(NotStarted, begin).toOption.get.state

    assertEquals(
      rules.handle(progress, PlacePawn(PlayerId("p2"), sites.head)),
      Left(WrongPlayer(PlayerId("p1"), PlayerId("p2")))
    )
    assertEquals(
      rules.handle(
        progress,
        PlacePawn(PlayerId("p1"), SiteId("site:unknown"))
      ),
      Left(UnknownSite(SiteId("site:unknown")))
    )
    val knownOutOfPlay = catalog.sites.drop(8).head.id
    assertEquals(
      rules.handle(
        progress,
        PlacePawn(PlayerId("p1"), knownOutOfPlay)
      ),
      Left(SiteNotInPlay(knownOutOfPlay))
    )
  }

  test("final placement completes automatically and shared sites are legal") {
    val started = rules.handle(NotStarted, begin).toOption.get
    val first = rules
      .handle(started.state, PlacePawn(PlayerId("p1"), sites.head))
      .toOption
      .get
    val second = rules
      .handle(first.state, PlacePawn(PlayerId("p2"), sites.head))
      .toOption
      .get
    val third = rules
      .handle(second.state, PlacePawn(PlayerId("p3"), sites.head))
      .toOption
      .get

    assertEquals(second.continue, AwaitingPawn(PlayerId("p3")))
    assertEquals(
      third.events,
      Vector(PawnPlaced(PlayerId("p3"), sites.head), SetupCompleted)
    )
    assertEquals(third.continue, Finished)
    assert(third.state.isInstanceOf[Completed])
  }

  test("event journal appends batches atomically in order and conflicts") {
    val journal = new InMemoryEventJournal[SetupEvent]
    val started = SetupStarted(participants, expectedCatalog, sites)

    assertEquals(
      journal.append(0, Vector(started)),
      EventAppendResult.Appended(Vector(RecordedEvent(0, started)))
    )
    assertEquals(
      journal.append(0, Vector(PawnPlaced(PlayerId("p1"), sites.head))),
      EventAppendResult.Conflict(0, 1)
    )
    assertEquals(
      journal.append(
        1,
        Vector(PawnPlaced(PlayerId("p1"), sites.head), SetupCompleted)
      ),
      EventAppendResult.Appended(
        Vector[RecordedEvent[SetupEvent]](
          RecordedEvent(1, PawnPlaced(PlayerId("p1"), sites.head)),
          RecordedEvent(2, SetupCompleted)
        )
      )
    )
    assertEquals(journal.read(0).map(_.index), Vector(0L, 1L, 2L))
  }

  test("emitted event replay exactly equals command-driven state") {
    val commands = Vector(
      PlacePawn(PlayerId("p1"), sites(0)),
      PlacePawn(PlayerId("p2"), sites(1)),
      PlacePawn(PlayerId("p3"), sites(1))
    )
    val initial = rules.handle(NotStarted, begin).toOption.get
    val (finalState, events) =
      commands.foldLeft(initial.state -> initial.events) {
        case ((state, accumulated), command) =>
          val transition = rules.handle(state, command).toOption.get
          transition.state -> (accumulated ++ transition.events)
      }
    val recorded =
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }

    assertEquals(
      new EventReplayEngine(rules).replay(recorded),
      Right(finalState)
    )
  }

  test("corrupt event replay reports the failing event index") {
    val corrupt = Vector[RecordedEvent[SetupEvent]](
      RecordedEvent(0, SetupStarted(participants, expectedCatalog, sites)),
      RecordedEvent(1, PawnPlaced(PlayerId("p2"), sites.head))
    )

    val failure = new EventReplayEngine(rules).replay(corrupt).left.toOption.get
    assertEquals(failure.index, 1L)
    assertEquals(
      failure.violation,
      WrongPlayer(PlayerId("p1"), PlayerId("p2"))
    )
  }
}
