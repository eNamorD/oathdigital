package oathdigital.application

import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathEvent.StepRestored
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}

/** Restoring taken-back command batches through the service (undo design,
  * "Redo"). */
class RedoServiceSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)
  private def rejected(violation: OathViolation) =
    Left(GameApplicationError.CommandRejected(violation))
  private def holderAnswer(park: ParkedServiceFixture.OathkeeperTiePark) =
    GameCommand.ResolveWalker(park.holder, TreeDecision(
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(park.secondLeader))))

  test("a redo restores the state after the batch, and a reload agrees"):
    val (service, repository) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accept(service.handle("g", undone.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(redone.state, moved.state)
    assertEquals(redone.events, Vector(StepRestored(
      travel.started.nextSequence, moved.nextSequence, p1)))
    assertEquals(redone.nextSequence, undone.nextSequence + 1L)
    assertEquals(redone.undo.map(step => (step.from, step.until)),
      Some((travel.started.nextSequence, moved.nextSequence)))
    assertEquals(redone.redo, None)
    assertEquals(redone.logRevision, Some(undone.nextSequence))
    val reloaded = new GameApplicationService(catalog, repository,
      genesis = table.state)
    assertEquals(reloaded.load("g").map(_.map(_.state)),
      Right(Some(moved.state)))

  test("redos restore in the reverse order of the undos"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    val once = accept(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    val twice = accept(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    val firstBack = accept(service.handle("g", twice.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(firstBack.state, second.started.state)
    val secondBack = accept(service.handle("g", firstBack.nextSequence,
      GameCommand.Redo(p1)))
    assertEquals(secondBack.state, second.picked.state)

  test("a restored batch can be taken back again"):
    val (service, _) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val redone = accept(service.handle("g", undone.nextSequence,
      GameCommand.Redo(p1)))
    val again = accept(service.handle("g", redone.nextSequence,
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
    val moved = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
      .picked
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    val again = accept(service.handle("g", undone.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, Table.quietPawnSite(p2))))
    assertEquals(service.handle("g", again.nextSequence, GameCommand.Redo(p1)),
      rejected(OathViolation.NothingToRedo))

  test("only the batch's issuer may restore it"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val answered = accept(game.service.handle("tie", game.accepted.nextSequence,
      holderAnswer(park)))
    val undone = accept(game.service.handle("tie", answered.nextSequence,
      GameCommand.Undo(park.holder)))
    assertEquals(game.service.handle("tie", undone.nextSequence,
      GameCommand.Redo(park.mover)),
      rejected(OathViolation.RedoNotIssuer(park.mover, park.holder)))
    val redone = accept(game.service.handle("tie", undone.nextSequence,
      GameCommand.Redo(park.holder)))
    assertEquals(redone.state, answered.state)

  test("the actor restores a step that left another player's question"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val undone = accept(game.service.handle("tie", game.accepted.nextSequence,
      GameCommand.Undo(park.mover)))
    val redone = accept(game.service.handle("tie", undone.nextSequence,
      GameCommand.Redo(park.mover)))
    assertEquals(redone.state, game.accepted.state)
