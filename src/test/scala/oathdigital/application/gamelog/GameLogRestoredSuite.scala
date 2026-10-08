package oathdigital.application.gamelog

import oathdigital.application.{GameApplicationService, GameCommand,
  ParkedServiceFixture}
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}
import LogScripts.formatter

/** What a reader sees of restored moves (undo design, "Redo" 4): a redo
  * leaves no trace, and the restored lines read as if never taken back. */
class GameLogRestoredSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def log(service: GameApplicationService, viewer: PlayerId)
      : Vector[LogEntry] =
    formatter.format(present(accepted(service.history("g")),
      "a stored game").steps, Some(viewer))
  private def withoutMarks(entries: Vector[LogEntry]) =
    entries.map(_.copy(takenBack = None))

  test("a restored move reads as if it had never been taken back"):
    val (service, _) = table.service()
    val moved = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
      .picked
    val before = log(service, p2)
    val undone = accepted(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    accepted(service.handle("g", undone.nextSequence, GameCommand.Redo(p1)))
    assertEquals(log(service, p2), before)

  test("a redo lifts only the restored move out of its group"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    val once = accepted(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accepted(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    val thrice = accepted(service.handle("g", twice.nextSequence,
      GameCommand.Undo(p1)))
    accepted(service.handle("g", thrice.nextSequence, GameCommand.Redo(p1)))
    val entries = log(service, p1)
    val restored = entries.filter(_.sequence < first.picked.nextSequence)
    val stillBack = entries.filter(entry =>
      entry.sequence >= first.picked.nextSequence &&
        entry.sequence < second.picked.nextSequence)
    assert(restored.nonEmpty && stillBack.nonEmpty, "both Travels post lines")
    assertEquals(restored.flatMap(_.takenBack), Vector.empty)
    assert(stillBack.forall(_.takenBack.nonEmpty),
      s"the second Travel stays taken back: $stillBack")

  test("a move taken back again after its redo reads as before, in one group"):
    val (service, _) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val before = log(service, p2)
    val undone = accepted(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accepted(service.handle("g", undone.nextSequence,
      GameCommand.Redo(p1)))
    accepted(service.handle("g", redone.nextSequence, GameCommand.Undo(p1)))
    // The start stays live after the last undo: every line of the pick is
    // taken back.
    val after = log(service, p2)
    val pick = after.filter(entry =>
      entry.sequence >= travel.started.nextSequence &&
        entry.sequence < moved.nextSequence)
    assert(pick.nonEmpty && pick.forall(_.takenBack.nonEmpty),
      s"the pick is taken back: $after")
    assertEquals(withoutMarks(after), before)
    assertEquals(after.flatMap(_.takenBack).map(_.group).distinct.size, 1)

  test("an older taken-back move stays in its group across a later undo and redo"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val undoneFirst = accepted(service.handle("g", first.picked.nextSequence,
      GameCommand.Undo(p1)))
    val second = accepted(service.handle("g", undoneFirst.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, Table.quietPawnSite(p2))))
    val undoneSecond = accepted(service.handle("g", second.nextSequence,
      GameCommand.Undo(p1)))
    accepted(service.handle("g", undoneSecond.nextSequence,
      GameCommand.Redo(p1)))
    val entries = log(service, p1)
    val older = entries.filter(entry =>
      entry.sequence >= first.started.nextSequence &&
        entry.sequence < first.picked.nextSequence)
    val restored = entries.filter(entry =>
      entry.sequence >= undoneFirst.nextSequence &&
        entry.sequence < second.nextSequence)
    assert(older.nonEmpty && restored.nonEmpty, "both Travels post lines")
    assert(older.forall(_.takenBack.nonEmpty), s"the first stays back: $older")
    assertEquals(restored.flatMap(_.takenBack), Vector.empty)
