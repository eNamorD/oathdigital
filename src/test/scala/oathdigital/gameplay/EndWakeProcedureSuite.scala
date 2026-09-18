package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model.OathContinue._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.gameplay.operations.{EnterPhase, Sequence}
import oathdigital.gameplay.phases.wake.EndWakeProcedure
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
import oathdigital.model._

/** Ending the Wake phase, on the generic walker (batch-1 Task 7).
  *
  * Ported from the deleted `WakeSuite`, which drove the deleted `Wake`
  * object. Every gate it asserted is asserted here against the declared tree;
  * what is new is the pair of facts that separate a phase transition from an
  * action: the player lands in Act action selection, and the Act action
  * boundary does NOT run on the way there.
  */
class EndWakeProcedureSuite extends munit.FunSuite {
  private val setupRules = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def ready(sharedEnemy: Boolean = false): OathState = {
    val Ready(value) = execute(setupRules)._1: @unchecked
    val active = value.game.current.turn.activePlayer
    val activeSite = value.game.current.players.find(_.player == active)
      .flatMap(_.pawnSite).get
    val players = value.game.current.players.map { player =>
      if (sharedEnemy && player.player != active)
        player.copy(pawnSite = Some(activeSite))
      else player
    }
    Ready(value.copy(game = value.game.copy(current =
      value.game.current.copy(players = players))))
  }

  private def endWake(state: OathState, actor: PlayerId) =
    rules.startWalker(state, PhaseTransitionRef.EndWake, actor)

  private def activePlayer(state: OathState): PlayerId = {
    val Ready(value) = state: @unchecked
    value.game.current.turn.activePlayer
  }

  test("ending Wake journals a phase change and enters Act action selection") {
    val state = ready(sharedEnemy = true)
    val active = activePlayer(state)
    val accepted = endWake(state, active).toOption.get
    val Ready(value) = accepted.state: @unchecked

    assertEquals(accepted.events.map(_.productPrefix),
      Vector("WalkerStepRecorded", "WalkerCompleted"))
    assertEquals(accepted.events.collect {
      case step: WalkerStepRecorded => step.ops
    }.flatten, Vector[oathdigital.gameplay.operations.CoreOperation](
      EnterPhase(Phase.Act)))
    assertEquals(accepted.events.last,
      WalkerCompleted(PhaseTransitionRef.EndWake): OathEvent)
    assertEquals(accepted.continue, ActActionSelection(active))
    assertEquals(value.game.current.turn.phase, Phase.Act)
    assertEquals(value.game.current.walkerPending, None)
    assertEquals(value.game.current.walkerProcedure, None)
  }

  test("ending Wake does not run the Act action boundary") {
    // The boundary runs only after a completed ACTION, decided by the
    // procedure reference's family (Task 8); End Wake is a phase transition,
    // not an action, so it never runs one -- whatever phase it starts or
    // finishes in. Bandit refill is the boundary's most visible half, so the
    // board is set up to make it fire and the first assertion proves it
    // would; without that, the second passes for the wrong reason.
    val Ready(base) = ready(): @unchecked
    val empty = base.game.current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.capacity > 0)).get
    val state = Ready(base.copy(game = base.game.copy(current =
      base.game.current.copy(map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(empty,
          base.game.current.map.sites(empty).copy(
            forces = SiteForces.Empty)))))))

    assert(StateBasedEvaluation.banditRefill(catalog, state)
      .toOption.flatten.nonEmpty,
      "precondition: the Act boundary would refill bandits in this state")
    val accepted = endWake(state, activePlayer(state)).toOption.get
    assertEquals(accepted.events.collect { case event: BanditsRefilled =>
      event }, Vector.empty)
    assertEquals(accepted.events.collect {
      case WalkerCompleted(TriggeredProcedureRef.Oathkeeper) => ()
      case parked: WalkerParked
          if parked.procedure == TriggeredProcedureRef.Oathkeeper => ()
    }, Vector.empty)
  }

  test("ending Wake remains legal while another player has a revealed Vision") {
    val state = ready()
    val active = activePlayer(state)
    val Ready(value) = state: @unchecked
    val other = value.game.current.players.indexWhere(_.player != active)
    val players = value.game.current.players.updated(other,
      value.game.current.players(other).copy(revealedVision =
        Some(VisionState(VisionId("V1"), Orientation.FaceUp))))
    val visionRevealed = Ready(value.copy(game = value.game.copy(current =
      value.game.current.copy(players = players))))

    val accepted = endWake(visionRevealed, active).toOption.get
    val Ready(after) = accepted.state: @unchecked

    assertEquals(after.game.current.turn.phase, Phase.Act)
    assertEquals(after.game.current.players(other).revealedVision,
      Some(VisionState(VisionId("V1"), Orientation.FaceUp)))
  }

  test("ending Wake rejects a player who is not the active one") {
    assert(endWake(ready(sharedEnemy = true), PlayerId("p1"))
      .left.toOption.get.isInstanceOf[WrongPlayer])
  }

  test("wrong phase and limited Oathkeeper Wake remains playable") {
    val state = ready()
    val active = activePlayer(state)
    val ended = endWake(state, active).toOption.get.state
    assertEquals(endWake(ended, active).left.toOption.get,
      WrongPhase(Phase.Wake, Phase.Act): OathViolation)
    val Ready(value) = state: @unchecked
    val titled = Ready(value.copy(game = value.game.copy(current =
      value.game.current.copy(title =
        OathkeeperState(Some(active), TitleSide.Oathkeeper)))))
    assert(endWake(titled, active).isRight)
  }

  test("ending Wake selects nothing") {
    val state = ready()
    val active = activePlayer(state)
    assert(rules.startWalker(state, PhaseTransitionRef.EndWake, active, Vector.empty,
      Vector(DecisionOptionRef.Button("favor"))).isLeft,
      "a start selection handed to End Wake must be rejected")
  }

  test("the declared tree is one phase change under no window") {
    val Ready(value) = ready(): @unchecked
    assertEquals(EndWakeProcedure.build(catalog, value,
      activePlayer(Ready(value)), Vector.empty),
      Right(Sequence(Vector(EnterPhase(Phase.Act)))))
  }

  test("what the projection offers is what the command accepts") {
    // The projector offers `endWake` unconditionally inside the Wake phase,
    // and the procedure gates on nothing the projector has not already
    // scoped. That is only true while both stay that way, which is what this
    // pins: a gate added to one side and not the other shows up here.
    val inWake = ready()
    val Ready(value) = inWake: @unchecked
    val inAct = Ready(value.copy(game = value.game.copy(current =
      value.game.current.copy(turn = value.game.current.turn.copy(
        phase = Phase.Act)))))
    Vector(inWake, ready(sharedEnemy = true), inAct).foreach { state =>
      val projection = new oathdigital.application.GameProjector(catalog)
        .project("end-wake", oathdigital.application.LoadedGame(state, 9),
          activePlayer(state))
      assertEquals(projection.legalControls.contains("endWake"),
        endWake(state, activePlayer(state)).isRight, "endWake")
    }
  }

  test("command evolution and replay are equal and corrupt index is exact") {
    val initial = ready()
    val active = activePlayer(initial)
    val ended = endWake(initial, active).toOption.get
    val replay = ended.events.foldLeft[Either[OathViolation, OathState]](
      Right(initial)) { case (next, event) => next.flatMap(rules.evolve(_, event)) }
    assertEquals(replay, Right(ended.state))

    // A doubled End Wake is still a detectable corruption, and it is
    // `EnterPhase` that detects it rather than a phase gate in an evolve:
    // entering the phase the turn is already in is rejected, so the repeat
    // fails at the first event of its second copy.
    val setupEvents = execute(setupRules)._2
    assertEquals(ended.events.size, 2)
    val corrupt = new EventReplayEngine(rules).replay(
      (setupEvents ++ ended.events ++ ended.events).zipWithIndex.map {
        case (event, index) => RecordedEvent(index.toLong, event)
      })
    assertEquals(corrupt.left.toOption.get.index,
      (setupEvents.size + ended.events.size).toLong)
  }
}
