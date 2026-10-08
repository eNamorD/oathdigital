package oathdigital.application

import oathdigital.model._
import oathdigital.model.OathEvent.StepRestored
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Restoring taken-back command batches through the service (undo design,
  * "Redo"). */
class RedoServiceSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def rejected(violation: OathViolation) =
    Left(GameApplicationError.CommandRejected(violation))

  test("a redo restores the state after the batch, and a reload agrees"):
    val (service, repository) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accepted(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accepted(service.handle("g", undone.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(redone.state, moved.state)
    assertEquals(redone.events, Vector(StepRestored(
      travel.started.nextSequence, moved.nextSequence, p1)))
    assertEquals(redone.nextSequence, undone.nextSequence + 1L)
    assertEquals(redone.undo.map(step => (step.from, step.until)),
      Some((travel.started.nextSequence, moved.nextSequence)))
    assertEquals(redone.redo, None)
    assertEquals(redone.logRevision, Some(undone.nextSequence))
    // A fresh service folds the Redo's marker into the same position.
    val reloaded = ParkedServiceFixture.loaded(
      ParkedServiceFixture.replayingSecond(service, repository).load("g"))
    assertEquals(reloaded.state, moved.state)
    assertEquals(reloaded.undo, redone.undo)
    assertEquals(reloaded.redo, None)
    assertEquals(reloaded.logRevision, Some(undone.nextSequence))

  test("redos restore in the reverse order of the undos"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    val once = accepted(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accepted(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    val firstBack = accepted(service.handle("g", twice.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(firstBack.state, second.started.state)
    val secondBack = accepted(service.handle("g", firstBack.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(secondBack.state, second.picked.state)

  test("a restored batch can be taken back again"):
    val (service, _) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accepted(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accepted(service.handle("g", undone.nextSequence,
      GameCommand.Redo(p1)))
    val again = accepted(service.handle("g", redone.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(again.state, travel.started.state)
    assertEquals(again.redo.map(step => (step.from, step.until)),
      Some((travel.started.nextSequence, moved.nextSequence)))

  test("with nothing taken back, there is nothing to redo"):
    val (service, repository) = table.service()
    assertEquals(service.handle("g", 0L, GameCommand.Redo(p1)),
      rejected(OathViolation.NothingToRedo))
    assertEquals(repository.load("g"), Right(None))
    val moved = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
      .picked
    assertEquals(service.handle("g", moved.nextSequence, GameCommand.Redo(p1)),
      rejected(OathViolation.NothingToRedo))
    assertEquals(repository.load("g").map(_.map(_.nextSequence)),
      Right(Some(moved.nextSequence)))

  test("a new command leaves nothing to redo"):
    val (service, _) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accepted(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.redo.map(step => (step.from, step.until)),
      Some((travel.started.nextSequence, moved.nextSequence)))
    val again = accepted(service.handle("g", undone.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, Table.quietPawnSite(p2))))
    assertEquals(again.redo, None)
    assertEquals(service.handle("g", again.nextSequence, GameCommand.Redo(p1)),
      rejected(OathViolation.NothingToRedo))

  test("only the batch's issuer may restore it"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val answered = accepted(game.service.handle("tie",
      game.accepted.nextSequence,
      ParkedServiceFixture.holderPicksLeader(park, park.secondLeader)))
    val undone = accepted(game.service.handle("tie", answered.nextSequence,
      GameCommand.Undo(park.holder)))
    assertEquals(game.service.handle("tie", undone.nextSequence,
      GameCommand.Redo(park.mover)),
      rejected(OathViolation.RedoNotIssuer(park.mover, park.holder)))
    val redone = accepted(game.service.handle("tie", undone.nextSequence,
      GameCommand.Redo(park.holder)))
    assertEquals(redone.state, answered.state)

  test("the actor restores a step that left another player's question"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val undone = accepted(game.service.handle("tie", game.accepted.nextSequence,
      GameCommand.Undo(park.mover)))
    val redone = accepted(game.service.handle("tie", undone.nextSequence,
      GameCommand.Redo(park.mover)))
    assertEquals(redone.state, game.accepted.state)
