package oathdigital.application

import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathEvent.StepTakenBack
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}

/** Taking back command batches through the service (undo design,
  * "Rulings"). */
class UndoServiceSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)
  private def rejected(violation: OathViolation) =
    Left(GameApplicationError.CommandRejected(violation))
  private def holderAnswer(park: ParkedServiceFixture.OathkeeperTiePark) =
    GameCommand.ResolveWalker(park.holder, TreeDecision(
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(park.secondLeader))))

  test("an undo restores the state before the batch, and a reload agrees"):
    val (service, repository) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accept(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, travel.started.state)
    assertEquals(undone.events, Vector(StepTakenBack(
      travel.started.nextSequence, moved.nextSequence, p1)))
    assertEquals(undone.nextSequence, moved.nextSequence + 1L)
    assertEquals(undone.undo.map(step => (step.from, step.until)),
      Some((0L, travel.started.nextSequence)))
    assertEquals(undone.logRevision, Some(moved.nextSequence))
    val reloaded = new GameApplicationService(catalog, repository,
      genesis = table.state)
    assertEquals(reloaded.load("g").map(_.map(_.state)),
      Right(Some(travel.started.state)))

  test("undo chains back across an action's end into the action before"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    val once = accept(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(once.state, second.started.state)
    val twice = accept(service.handle("g", once.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(twice.state, first.picked.state)

  test("only the batch's issuer may take it back"):
    val (service, repository) = table.service()
    val moved = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
      .picked
    assertEquals(service.handle("g", moved.nextSequence, GameCommand.Undo(p2)),
      rejected(OathViolation.UndoNotIssuer(p2, p1)))
    assertEquals(repository.load("g").map(_.map(_.nextSequence)),
      Right(Some(moved.nextSequence)))

  test("with nothing taken, there is nothing to undo"):
    val (service, repository) = table.service()
    assertEquals(service.handle("g", 0L, GameCommand.Undo(p1)),
      rejected(OathViolation.NothingToUndo))
    assertEquals(repository.load("g"), Right(None))

  test("the actor may undo while the walker waits on another player"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val undone = accept(game.service.handle("tie", game.accepted.nextSequence,
      GameCommand.Undo(park.mover)))
    undone.state match
      case OathState.Ready(ready) =>
        // Back at the destination decision the mover's pick answered.
        assertEquals(ready.game.current.walkerProcedure,
          Some(ActionRef.Travel))
        assertEquals(ready.game.current.walkerPending,
          Some(PendingTree(Vector("0"), Vector.empty)))
      case other => fail(s"expected a ready game, got $other")

  test("an off-turn issuer takes back their answer, then the actor theirs"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val answered = accept(game.service.handle("tie", game.accepted.nextSequence,
      holderAnswer(park)))
    assertEquals(game.service.handle("tie", answered.nextSequence,
      GameCommand.Undo(park.mover)),
      rejected(OathViolation.UndoNotIssuer(park.mover, park.holder)))
    val holderUndone = accept(game.service.handle("tie", answered.nextSequence,
      GameCommand.Undo(park.holder)))
    assertEquals(holderUndone.state, game.accepted.state)
    accept(game.service.handle("tie", holderUndone.nextSequence,
      GameCommand.Undo(park.mover)))

  test("a marker that does not name the latest live batch fails replay"):
    val (service, repository) = table.service()
    val moved = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
      .picked
    val forged = UndoJournals.rewritten(
      UndoJournals.events(repository, "g") :+
        StepTakenBack(1L, moved.nextSequence, p1), "g")
    val reloaded = new GameApplicationService(catalog, forged,
      genesis = table.state)
    assert(reloaded.load("g") match
      case Left(GameApplicationError.ReplayFailure(index, _)) =>
        index == moved.nextSequence
      case _ => false, s"expected a replay failure: ${reloaded.load("g")}")
