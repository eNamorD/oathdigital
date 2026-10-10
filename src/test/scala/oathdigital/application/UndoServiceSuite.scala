package oathdigital.application

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.powers.economy.GolemLegions
import oathdigital.gameplay.powers.recover.RelicWorship
import oathdigital.gameplay.powers.travel.Tents
import oathdigital.model._
import oathdigital.model.OathEvent.StepTakenBack
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Taking back command batches through the service (undo design,
  * "Rulings"). */
class UndoServiceSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def rejected(violation: OathViolation) =
    Left(GameApplicationError.CommandRejected(violation))

  /** The walk waits at the Travel's destination decision, the one the
    * mover's pick answered. */
  private def assertAtTravelDestination(state: OathState)(
      using munit.Location): Unit =
    state match
      case OathState.Ready(ready) =>
        assertEquals(ready.game.current.walkerProcedure,
          Some(ActionRef.Travel))
        // The destination follows the modifier decision, which asked
        // nothing.
        assertEquals(ready.game.current.walkerPending,
          Some(PendingTree(Vector("1", "0"), Vector.empty)))
      case other => fail(s"expected a ready game, got $other")

  test("an undo restores the state before the batch, and a reload agrees"):
    val (service, repository) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    val undone = accepted(service.handle("g", moved.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, travel.started.state)
    assertEquals(undone.events, Vector(StepTakenBack(
      travel.started.nextSequence, moved.nextSequence, p1)))
    assertEquals(undone.nextSequence, moved.nextSequence + 1L)
    assertEquals(undone.undo.map(step => (step.from, step.until)),
      Some((0L, travel.started.nextSequence)))
    assertEquals(undone.logRevision, Some(moved.nextSequence))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load("g").map(_.map(_.state)),
      Right(Some(travel.started.state)))

  test("undo chains back across an action's end into the action before"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    val once = accepted(service.handle("g", second.picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(once.state, second.started.state)
    val twice = accepted(service.handle("g", once.nextSequence,
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
    val undone = accepted(game.service.handle("tie",
      game.accepted.nextSequence, GameCommand.Undo(park.mover)))
    assertAtTravelDestination(undone.state)

  test("an off-turn issuer takes back their answer, then the actor theirs"):
    val park = ParkedServiceFixture.oathkeeperTiePark("tie")
    val game = park.game
    val answered = accepted(game.service.handle("tie",
      game.accepted.nextSequence,
      ParkedServiceFixture.holderPicksLeader(park, park.secondLeader)))
    assertEquals(game.service.handle("tie", answered.nextSequence,
      GameCommand.Undo(park.mover)),
      rejected(OathViolation.UndoNotIssuer(park.mover, park.holder)))
    val holderUndone = accepted(game.service.handle("tie",
      answered.nextSequence, GameCommand.Undo(park.holder)))
    assertEquals(holderUndone.state, game.accepted.state)
    val moverUndone = accepted(game.service.handle("tie",
      holderUndone.nextSequence, GameCommand.Undo(park.mover)))
    assertAtTravelDestination(moverUndone.state)

  test("a marker that does not name the latest live batch fails replay"):
    val (service, repository) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val moved = travel.picked
    // The pick's batch opens at travel.started.nextSequence; the marker
    // claims it opened one event earlier.
    val forged = UndoJournals.rewritten(
      UndoJournals.events(repository, "g") :+
        StepTakenBack(travel.started.nextSequence - 1L, moved.nextSequence,
          p1), "g")
    assertEquals(UndoJournals.replayFailureIndex(service, forged, "g"),
      moved.nextSequence)

  test("Undo takes back a Travel modifier pick, and then the start"):
    val withTents = table.pawn(p1, "Ancient City").adviser(p1, "Tents")
      .favor(p1, 1)
    val (service, _) = withTents.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val picked = accepted(service.handle("g", started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision("travel.modifiers",
        ModifierPlay.pick(Tents.id)))))
    val unpicked = accepted(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(unpicked.state, started.state)
    val undone = accepted(service.handle("g", unpicked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, withTents.state)

  test("Undo takes back a Muster modifier pick, and then the start"):
    // No card stands at p1's site, so Start waits for Golem Legions.
    val withGolem = table.adviser(p1, "Golem Legions")
    val (service, _) = withGolem.service()
    val started = accepted(service.handle("g", 0L,
      GameCommand.StartWalker(ActionRef.Muster,
        StartPayload(p1))))
    val picked = accepted(service.handle("g", started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision("muster.modifiers",
        ModifierPlay.pick(GolemLegions.id)))))
    val unpicked = accepted(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(unpicked.state, started.state)
    val undone = accepted(service.handle("g", unpicked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, withGolem.state)

  test("Undo takes back a Recover modifier pick, and then the start"):
    // Catacombs and Relic Worship can both be picked, so picking one asks
    // again instead of rolling.
    val withBoth = table.pawn(p1, "Fair Isle").secrets(p1, faceUp = 2)
      .denizen("Catacombs", at = "Fair Isle").adviser(p1, "Relic Worship")
    val (service, _) = withBoth.service()
    val started = accepted(service.handle("g", 0L,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(p1))))
    val picked = accepted(service.handle("g", started.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision("recover.modifiers",
        ModifierPlay.pick(RelicWorship.id)))))
    val unpicked = accepted(service.handle("g", picked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(unpicked.state, started.state)
    val undone = accepted(service.handle("g", unpicked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(undone.state, withBoth.state)
