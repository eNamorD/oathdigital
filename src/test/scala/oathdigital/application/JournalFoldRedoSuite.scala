package oathdigital.application

import oathdigital.model._
import oathdigital.model.OathEvent.StepRestored
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** The redo stack, as the service folds its journal (undo design, "Replay:
  * one forward fold" and "Redo"). Restore markers are written by journal
  * surgery here; the Redo command comes in its own suite. */
class JournalFoldRedoSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def range(step: UndoStep) = (step.issuer, step.from, step.until)
  private def pickOther(to: PlayerId) = ParkedServiceFixture
    .pickTravelDestination(p1, Table.quietPawnSite(to))

  test("each undo offers its batch back, the latest undone on top"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    assertEquals(second.picked.redo, None)
    val once = accepted(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(once.redo.map(range), Some((p1, second.started.nextSequence,
      second.picked.nextSequence)))
    val twice = accepted(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(twice.redo.map(range), Some((p1, first.picked.nextSequence,
      second.started.nextSequence)))

  test("a restore marker that does not name the latest undone batch fails replay"):
    val (service, repository) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accepted(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val events = UndoJournals.events(repository, "g")
    // The undone batch opened at travel.started.nextSequence; this marker
    // claims it opened one event earlier.
    val openedEarlier = StepRestored(travel.started.nextSequence - 1L,
      moved.nextSequence, p1)
    assertEquals(UndoJournals.replayFailureIndex(service,
        UndoJournals.rewritten(events :+ openedEarlier, "g"), "g"),
      undone.nextSequence)
    // p2 never took the batch back.
    val wrongIssuer = StepRestored(travel.started.nextSequence,
      moved.nextSequence, p2)
    assertEquals(UndoJournals.replayFailureIndex(service,
        UndoJournals.rewritten(events :+ wrongIssuer, "g"), "g"),
      undone.nextSequence)

  test("a restore marker after a new command batch fails replay"):
    val (service, repository) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accepted(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val again = accepted(service.handle("g", undone.nextSequence, pickOther(p2)))
    assertEquals(UndoJournals.replayFailureIndex(service,
        UndoJournals.rewritten(UndoJournals.events(repository, "g") :+
          StepRestored(travel.started.nextSequence, moved.nextSequence, p1),
          "g"), "g"),
      again.nextSequence)

  test("a restore marker that names a deeper batch fails replay"):
    val (service, repository) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    val once = accepted(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accepted(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    // The second start is on top of the redo stack; the second pick lies
    // under it.
    assertEquals(UndoJournals.replayFailureIndex(service,
        UndoJournals.rewritten(UndoJournals.events(repository, "g") :+
          StepRestored(second.started.nextSequence,
            second.picked.nextSequence, p1), "g"), "g"),
      twice.nextSequence)

  test("a new command after a redo empties what was left to redo"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    val once = accepted(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accepted(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accepted(service.handle("g", twice.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(redone.redo.map(range), Some((p1, second.started.nextSequence,
      second.picked.nextSequence)))
    val again = accepted(service.handle("g", redone.nextSequence, pickOther(p2)))
    assertEquals(again.redo, None)
    assertEquals(again.undo.map(_.from), Some(redone.nextSequence))
