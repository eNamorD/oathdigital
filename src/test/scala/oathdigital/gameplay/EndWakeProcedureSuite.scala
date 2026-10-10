package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerCompleted,
  WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{Look, MenuPlay, Outcomes, Table}
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

  /** p1's Wake on the quiet table, p1 at Ancient City. `turn` only sets the
    * phase; End Wake reads nothing about the board, so no other fact is
    * stated here. */
  private def p1Wake: Table = Table.start.turn(p1, Phase.Wake)
    .pawn(p1, "Ancient City")

  private def endWake(state: OathState, actor: PlayerId) =
    MenuPlay.started(rules, state, actor, PhaseTransitionRef.EndWake)

  test("ending Wake journals a phase change and enters Act action selection"):
    // Both other players stand at p1's site: End Wake must journal only its
    // own two events and stay legal, since nothing in it reads other pawns.
    val state = p1Wake.pawn(p2, "Ancient City").pawn(p3, "Ancient City").state
    val ended = accepted(endWake(state, p1))
    // The Act menu is parked once End Wake is done.
    assertEquals(ended.events.map(_.productPrefix),
      Vector("WalkerStepRecorded", "WalkerCompleted", "WalkerParked"))
    assertEquals(ended.events.collect {
      case step: WalkerStepRecorded => step.ops
    }.flatten, Vector[oathdigital.model.CoreOperation](
      EnterPhase(Phase.Act)))
    assert(ended.events.contains(WalkerCompleted(PhaseTransitionRef.EndWake)),
      ended.events)
    parked.assertResumed(ended.state, Phase.Act, p1)
    assertEquals(MenuPlay.procedureBeyondMenu(Outcomes.ready(ended.state)),
      None)

  test("ending Wake does not run the Act action boundary"):
    // The boundary runs only after a completed ACTION, decided by the
    // procedure reference's family (Task 8); End Wake is a phase transition,
    // not an action, so it never runs one -- whatever phase it starts or
    // finishes in. Bandit refill is the boundary's most visible half, so the
    // board is set up to make it fire and the first assertion proves it
    // would; without that, the second passes for the wrong reason. Ancient
    // City, capacity 3, is emptied of its bandits.
    val state = p1Wake.bandits("Ancient City", 0).state

    assert(StateBasedEvaluation.banditRefill(catalog, state)
      .toOption.flatten.nonEmpty,
      "precondition: the Act boundary would refill bandits in this state")
    val ended = accepted(endWake(state, p1))
    assertEquals(ended.events.collect { case event: BanditsRefilled =>
      event }, Vector.empty)
    assertEquals(ended.events.collect {
      case WalkerCompleted(TriggeredProcedureRef.Oathkeeper) => ()
      case parked: WalkerParked
          if parked.procedure == TriggeredProcedureRef.Oathkeeper => ()
    }, Vector.empty)

  test("ending Wake rejects a player who is not the active one"):
    val state = p1Wake.state
    assertEquals(rejected(endWake(state, p2)),
      WrongPlayer(p1, p2): OathViolation)

  test("ending Wake selects nothing"):
    assertEquals(rules.startWalker(p1Wake.state, PhaseTransitionRef.EndWake, p1,
      Vector(DecisionOptionRef.Button("favor"))).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "ending Wake selects nothing, got button/favor")))

  test("the Wake menu offers End Wake exactly when the command accepts it"):
    // End Wake reads nothing about the board, so enemy pawns at the site do
    // not close the option.
    Vector(p1Wake, p1Wake.pawn(p2, "Ancient City").pawn(p3, "Ancient City"))
      .foreach { table =>
        val offered = MenuPlay.options(table.phaseMenuParked.state, catalog)
        assertEquals(offered.find(_.ref == PhaseMenu.Menu.Wake.endingOption)
          .map(_.available), Some(endWake(table.state, p1).isRight))
      }

  test("replaying End Wake's events reproduces its state"):
    val initial = p1Wake.state
    val ended = accepted(endWake(initial, p1))
    val replay = ended.events.foldLeft[Either[OathViolation, OathState]](
      Right(initial)) { case (next, event) => next.flatMap(rules.evolve(_, event)) }
    assertEquals(replay, Right(ended.state))

  test("a doubled End Wake is rejected at its second copy's first event"):
    // Replays the real setup on purpose: the subject is a journal that holds
    // the setup's real events followed by a doubled End Wake, so the corrupt
    // position is counted from the setup's length.
    // A doubled End Wake is still a detectable corruption, and it is
    // `EnterPhase` that detects it rather than a phase gate in an evolve:
    // entering the phase the turn is already in is rejected, so the repeat
    // fails at the first event of its second copy.
    val initial = Ready(initialReady)
    val ended = accepted(endWake(initial, Look(initial).active))
    val setupEvents = setupThroughWake().events
    // End Wake journals its step and its completion, then the Act menu's
    // park.
    assertEquals(ended.events.size, 3)
    val corrupt = new EventReplayEngine(rules).replay(
      (setupEvents ++ ended.events ++ ended.events).zipWithIndex.map {
        case (event, index) => RecordedEvent(index.toLong, event)
      })
    assertEquals(rejected(corrupt).index,
      (setupEvents.size + ended.events.size).toLong)
