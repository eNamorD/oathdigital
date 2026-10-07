package oathdigital.application.gamelog

import oathdigital.application.{GameAccepted, GameApplicationError,
  GameApplicationService, GameCommand, StartPayload}
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts.formatter

/** What a reader sees of restored moves (undo design, "Redo" 4): a redo
  * leaves no trace, and the restored lines read as if never taken back. */
class GameLogRestoredSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def travel(to: PlayerId) = GameCommand.StartWalker(ActionRef.Travel,
    StartPayload(p1, Vector.empty,
      Vector(DecisionOptionRef.Site(Table.quietPawnSite(to)))))
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)
  private def log(service: GameApplicationService, viewer: PlayerId)
      : Vector[LogEntry] =
    formatter.format(service.history("g").toOption.flatten
      .getOrElse(fail("no history")).steps, Some(viewer))
  private def withoutMarks(entries: Vector[LogEntry]) =
    entries.map(_.copy(takenBack = None))

  test("a restored move reads as if it had never been taken back"):
    val (service, _) = table.service()
    val moved = accept(service.handle("g", 0L, travel(p3)))
    val before = log(service, p2)
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    accept(service.handle("g", undone.nextSequence, GameCommand.Redo(p1)))
    assertEquals(log(service, p2), before)

  test("a redo lifts only the restored move out of its group"):
    val (service, _) = table.service()
    val first = accept(service.handle("g", 0L, travel(p3)))
    val second = accept(service.handle("g", first.nextSequence, travel(p2)))
    val once = accept(service.handle("g", second.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accept(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    accept(service.handle("g", twice.nextSequence, GameCommand.Redo(p1)))
    val entries = log(service, p1)
    val restored = entries.filter(_.sequence < first.nextSequence)
    val stillBack = entries.filter(entry =>
      entry.sequence >= first.nextSequence &&
        entry.sequence < second.nextSequence)
    assert(restored.nonEmpty && stillBack.nonEmpty, "both Travels post lines")
    assertEquals(restored.flatMap(_.takenBack), Vector.empty)
    assert(stillBack.forall(_.takenBack.nonEmpty),
      s"the second Travel stays taken back: $stillBack")

  test("a move taken back again after its redo reads as before, in one group"):
    val (service, _) = table.service()
    val moved = accept(service.handle("g", 0L, travel(p3)))
    val before = log(service, p2)
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accept(service.handle("g", undone.nextSequence,
      GameCommand.Redo(p1)))
    accept(service.handle("g", redone.nextSequence, GameCommand.Undo(p1)))
    // Nothing is live after the last undo: every line is the Travel's.
    val after = log(service, p2)
    assert(after.forall(_.takenBack.nonEmpty), s"all taken back: $after")
    assertEquals(withoutMarks(after), before)
    assertEquals(after.flatMap(_.takenBack).map(_.group).distinct.size, 1)
