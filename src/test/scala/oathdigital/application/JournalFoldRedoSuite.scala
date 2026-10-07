package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathEvent.StepRestored
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}

/** The redo stack, as the service folds its journal (undo design, "Replay:
  * one forward fold" and "Redo"). Restore markers are written by journal
  * surgery here; the Redo command comes in its own suite. */
class JournalFoldRedoSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def travel(actor: PlayerId, to: PlayerId) = GameCommand.StartWalker(
    ActionRef.Travel, StartPayload(actor, Vector.empty,
      Vector(DecisionOptionRef.Site(Table.quietPawnSite(to)))))
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)
  private def range(step: UndoStep) = (step.issuer, step.from, step.until)

  /** `repository`'s game "g" as a fresh service loads it. */
  private def reloadedGame(repository: EventStreamRepository): LoadedGame =
    new GameApplicationService(catalog, repository, genesis = table.state)
      .load("g").fold(error => fail(s"load failed: $error"), identity)
      .getOrElse(fail("no game"))

  /** The journal index a fresh service's load of "g" fails at, if any. */
  private def replayFailureIndex(repository: EventStreamRepository)
      : Option[Long] =
    new GameApplicationService(catalog, repository, genesis = table.state)
      .load("g") match
      case Left(GameApplicationError.ReplayFailure(index, _)) => Some(index)
      case _ => None

  test("each undo offers its batch back, the latest undone on top"):
    val (service, _) = table.service()
    val first = accept(service.handle("g", 0L, travel(p1, p3)))
    val second = accept(service.handle("g", first.nextSequence, travel(p1, p2)))
    assertEquals(second.redo, None)
    val once = accept(service.handle("g", second.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(once.redo.map(range),
      Some((p1, first.nextSequence, second.nextSequence)))
    val twice = accept(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(twice.redo.map(range), Some((p1, 0L, first.nextSequence)))
    assert(twice.redo.flatMap(_.note).nonEmpty,
      "a Travel posts a line its issuer sees")

  test("a new command batch empties the redo stack"):
    val (service, _) = table.service()
    val moved = accept(service.handle("g", 0L, travel(p1, p3)))
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    assert(undone.redo.nonEmpty, "the undone Travel waits to be restored")
    val again = accept(service.handle("g", undone.nextSequence, travel(p1, p2)))
    assertEquals(again.redo, None)

  test("a restore marker brings back its batch's state and its undo"):
    val (service, repository) = table.service()
    val moved = accept(service.handle("g", 0L, travel(p1, p3)))
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val loaded = reloadedGame(UndoJournals.rewritten(
      UndoJournals.events(repository, "g") :+
        StepRestored(0L, moved.nextSequence, p1), "g"))
    assertEquals(loaded.state, moved.state)
    assertEquals(loaded.undo.map(range), Some((p1, 0L, moved.nextSequence)))
    assertEquals(loaded.redo, None)
    assertEquals(loaded.logRevision, Some(undone.nextSequence))

  test("a restore marker that does not name the latest undone batch fails replay"):
    val (service, repository) = table.service()
    val moved = accept(service.handle("g", 0L, travel(p1, p3)))
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val events = UndoJournals.events(repository, "g")
    assertEquals(replayFailureIndex(UndoJournals.rewritten(
        events :+ StepRestored(1L, moved.nextSequence, p1), "g")),
      Some(undone.nextSequence))
    assertEquals(replayFailureIndex(UndoJournals.rewritten(
        events :+ StepRestored(0L, moved.nextSequence, p2), "g")),
      Some(undone.nextSequence))

  test("a restore marker after a new command batch fails replay"):
    val (service, repository) = table.service()
    val moved = accept(service.handle("g", 0L, travel(p1, p3)))
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val again = accept(service.handle("g", undone.nextSequence, travel(p1, p2)))
    assertEquals(replayFailureIndex(UndoJournals.rewritten(
        UndoJournals.events(repository, "g") :+
          StepRestored(0L, moved.nextSequence, p1), "g")),
      Some(again.nextSequence))

  test("a restore marker that names a deeper batch fails replay"):
    val (service, repository) = table.service()
    val first = accept(service.handle("g", 0L, travel(p1, p3)))
    val second = accept(service.handle("g", first.nextSequence, travel(p1, p2)))
    val once = accept(service.handle("g", second.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accept(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    // The first batch is on top of the redo stack; the second lies under it.
    assertEquals(replayFailureIndex(UndoJournals.rewritten(
        UndoJournals.events(repository, "g") :+
          StepRestored(first.nextSequence, second.nextSequence, p1), "g")),
      Some(twice.nextSequence))

  test("a new command after a redo empties what was left to redo"):
    val (service, _) = table.service()
    val first = accept(service.handle("g", 0L, travel(p1, p3)))
    val second = accept(service.handle("g", first.nextSequence, travel(p1, p2)))
    val once = accept(service.handle("g", second.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accept(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accept(service.handle("g", twice.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(redone.redo.map(range),
      Some((p1, first.nextSequence, second.nextSequence)))
    val again = accept(service.handle("g", redone.nextSequence, travel(p1, p2)))
    assertEquals(again.redo, None)
    assertEquals(again.undo.map(_.from), Some(redone.nextSequence))
