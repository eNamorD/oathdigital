package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerCompleted,
  WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Ending the Wake phase, on the generic walker (batch-1 Task 7).
  *
  * Ported from the deleted `WakeSuite`, which drove the deleted `Wake`
  * object. Its live gates are asserted here; the Vision and Oathkeeper gates
  * it also checked no longer exist;
  * what is new is the pair of facts that separate a phase transition from an
  * action: the player lands in Act action selection, and the Act action
  * boundary does NOT run on the way there.
  */
class EndWakeProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val parked = new ParkedDecisionAssertions(catalog)

  /** The first game just after Setup, in its first player's Wake. With
    * `sharedEnemy`, the other two players stand at the active player's site,
    * so an Oathkeeper or a combat check at that site would have someone to
    * reach. */
  private def firstGameWake(sharedEnemy: Boolean = false): OathState =
    val active = Look(initialReady).active
    val activeSite = Look(initialReady).pawn(active)
    val others = Vector(p1, p2, p3).filterNot(_ == active)
    Ready(if sharedEnemy then others.foldLeft(Table.from(initialReady))(
      _.pawn(_, activeSite)).unchecked else initialReady)

  private def endWake(state: OathState, actor: PlayerId) =
    rules.startWalker(state, PhaseTransitionRef.EndWake, actor)

  private def activePlayer(state: OathState): PlayerId = Look(state).active

  test("ending Wake journals a phase change and enters Act action selection"):
    val state = firstGameWake(sharedEnemy = true)
    val active = activePlayer(state)
    val ended = accepted(endWake(state, active))
    assertEquals(ended.events.map(_.productPrefix),
      Vector("WalkerStepRecorded", "WalkerCompleted"))
    assertEquals(ended.events.collect {
      case step: WalkerStepRecorded => step.ops
    }.flatten, Vector[oathdigital.model.CoreOperation](
      EnterPhase(Phase.Act)))
    assertEquals(ended.events.last,
      WalkerCompleted(PhaseTransitionRef.EndWake): OathEvent)
    parked.assertResumed(ended.state, Phase.Act, active)
    assertEquals(Outcomes.ready(ended.state).game.current.walkerProcedure, None)

  test("ending Wake does not run the Act action boundary"):
    // The boundary runs only after a completed ACTION, decided by the
    // procedure reference's family (Task 8); End Wake is a phase transition,
    // not an action, so it never runs one -- whatever phase it starts or
    // finishes in. Bandit refill is the boundary's most visible half, so the
    // board is set up to make it fire and the first assertion proves it
    // would; without that, the second passes for the wrong reason. Ancient
    // City, capacity 3, is emptied of its bandits.
    val state = Ready(Table.from(Outcomes.ready(firstGameWake()))
      .bandits("Ancient City", 0).unchecked)

    assert(StateBasedEvaluation.banditRefill(catalog, state)
      .toOption.flatten.nonEmpty,
      "precondition: the Act boundary would refill bandits in this state")
    val ended = accepted(endWake(state, activePlayer(state)))
    assertEquals(ended.events.collect { case event: BanditsRefilled =>
      event }, Vector.empty)
    assertEquals(ended.events.collect {
      case WalkerCompleted(TriggeredProcedureRef.Oathkeeper) => ()
      case parked: WalkerParked
          if parked.procedure == TriggeredProcedureRef.Oathkeeper => ()
    }, Vector.empty)

  test("ending Wake rejects a player who is not the active one"):
    // p2 is the first game's first player, so the Wake is p2's, not p1's.
    val state = firstGameWake(sharedEnemy = true)
    assertEquals(activePlayer(state), p2)
    assert(rejected(endWake(state, p1)).isInstanceOf[WrongPlayer])

  test("ending Wake outside the Wake is refused"):
    val state = firstGameWake()
    val active = activePlayer(state)
    val ended = accepted(endWake(state, active)).state
    assertEquals(rejected(endWake(ended, active)),
      WrongPhase(Phase.Wake, Phase.Act): OathViolation)

  test("ending Wake selects nothing"):
    val state = firstGameWake()
    val active = activePlayer(state)
    assertEquals(rules.startWalker(state, PhaseTransitionRef.EndWake, active,
      Vector.empty, Vector(DecisionOptionRef.Button("favor"))).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "ending Wake selects nothing, got button/favor")))

  test("what the projection offers is what the command accepts"):
    // The projector offers `endWake` unconditionally inside the Wake phase,
    // and the procedure gates on nothing the projector has not already
    // scoped. That is only true while both stay that way, which is what this
    // pins: a gate added to one side and not the other shows up here.
    val inWake = firstGameWake()
    val inAct = Ready(Table.from(Outcomes.ready(inWake))
      .backInPhase(Phase.Act).unchecked)
    Vector(inWake, firstGameWake(sharedEnemy = true), inAct).foreach { state =>
      val projection = new oathdigital.application.GameProjector(catalog)
        .project("end-wake", oathdigital.application.LoadedGame(state, 9),
          activePlayer(state))
      assertEquals(projection.legalControls.contains("endWake"),
        endWake(state, activePlayer(state)).isRight, "endWake")
    }

  test("replaying End Wake's events reproduces its state"):
    val initial = firstGameWake()
    val ended = accepted(endWake(initial, activePlayer(initial)))
    val replay = ended.events.foldLeft[Either[OathViolation, OathState]](
      Right(initial)) { case (next, event) => next.flatMap(rules.evolve(_, event)) }
    assertEquals(replay, Right(ended.state))

  test("a doubled End Wake is rejected at its second copy's first event"):
    // A doubled End Wake is still a detectable corruption, and it is
    // `EnterPhase` that detects it rather than a phase gate in an evolve:
    // entering the phase the turn is already in is rejected, so the repeat
    // fails at the first event of its second copy.
    val initial = firstGameWake()
    val ended = accepted(endWake(initial, activePlayer(initial)))
    val setupEvents = setupThroughWake().events
    // End Wake journals two events: its step and its completion.
    assertEquals(ended.events.size, 2)
    val corrupt = new EventReplayEngine(rules).replay(
      (setupEvents ++ ended.events ++ ended.events).zipWithIndex.map {
        case (event, index) => RecordedEvent(index.toLong, event)
      })
    assertEquals(rejected(corrupt).index,
      (setupEvents.size + ended.events.size).toLong)
