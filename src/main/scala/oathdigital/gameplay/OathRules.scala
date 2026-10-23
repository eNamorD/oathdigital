package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.consent.ConsentEvolution
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.model._
import oathdigital.gameplay.powerresolver.{PhasePowers}
import oathdigital.gameplay.walker.{ActionBoundaryRan, FollowUpSkipped,
  LogNoted, ProcedureDequeued, ProcedureWalker, WalkerCompleted, WalkerDice,
  WalkerParked, WalkerPowers, WalkerProcedureRegistry, WalkerStepRecorded}
import oathdigital.gameplay._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState._
import oathdigital.model.OathViolation._

/** Deterministic aggregate boundary for setup and gameplay routing.
  *
  * `walkerPowerCatalog` is the full set of `ContributingPower`s this instance
  * may offer to a walker command (`WalkerPowerCatalog.default` in production)
  * -- a constructor parameter so a suite can drive a real command against a
  * real power. `OathRules.walkerPowers` is the per-command projection of that
  * catalog: restrictions are gathered from it at command entry and its
  * transforms fold the tree during the walk.
  * `walkerTree` is the same injection seam for the action tree a walker
  * command walks; production derives it from the action's own module.
  */
final class OathRules(protected val catalog: ExecutableCatalog,
    protected val warExhaustionRandomPort: WarExhaustionRandomPort =
      WarExhaustionRandomPort.random,
    protected val walkerPowerCatalog: WalkerPowers = WalkerPowers.empty,
    protected val walkerTree: OathRules.WalkerTreeSource =
      OathRules.declaredWalkerTree,
    protected val phasePowerCatalog: PhasePowers = PhasePowers.empty,
    protected val walkerDice: WalkerDice = WalkerDice.unavailable)
    extends EventEvolution[OathState, OathEvent, OathViolation]
    with OathRulesWalker with OathDriver:
  override val initialState: OathState = NoGame

  /** The global operation restrictions outside a walker action: the catalog's
    * printed ones and the automatic powers', with no modifiers, since no
    * action is running. State-based effects run under them. */
  protected lazy val outsideActions: Vector[OperationRestriction] =
    WalkerPowers.selected(walkerPowerCatalog, Vector.empty).operationRestrictions

  override def evolve(
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] =
    event match
      case event: WalkerStepRecorded => ProcedureWalker.applyRecorded(state, event)
      case event: WalkerParked => ProcedureWalker.applyRecorded(state, event)
      case event: WalkerCompleted => ProcedureWalker.applyRecorded(state, event)
      case event: LogNoted => ProcedureWalker.applyRecorded(state, event)
      case event: ProcedureDequeued =>
        ProcedureWalker.applyRecorded(state, event)
      case ActionBoundaryRan =>
        ProcedureWalker.applyRecorded(state, ActionBoundaryRan)
      case event: FollowUpSkipped => ProcedureWalker.applyRecorded(state, event)
      // `WalkerEvent` is open, so an event type this match does not know
      // rejects here instead of escaping as a `MatchError`.
      case other: WalkerEvent =>
        Left(InvalidEventOrder(s"unsupported walker event: ${other.productPrefix}"))
      case event: BanditsRefilled => StateBasedEvaluation.evolve(catalog, state, event, outsideActions)
      case event: UsurperFlipped => StateBasedEvaluation.evolve(catalog, state, event, outsideActions)
      case event: UsurperVictory => StateBasedEvaluation.evolve(catalog, state, event, outsideActions)
      case event: VisionVictory => StateBasedEvaluation.evolve(catalog, state, event, outsideActions)
      case event: RoundEnded => StateBasedEvaluation.evolve(catalog, state, event, outsideActions)
      case event: WarExhaustionResolved =>
        StateBasedEvaluation.evolve(catalog, state, event, outsideActions)
      case event: ConsentEvent => state match
        case Ready(ready) => ConsentEvolution.evolve(ready, event)
          .map(Ready.apply)
        case _ => Left(GameNotStarted)
      // Undo's facts (undo design, "Storage"). A batch's opening fact
      // changes nothing; a marker rewinds or restores, which only the
      // application's journal fold can do.
      case _: CommandAccepted => Right(state)
      case _: StepTakenBack | _: StepRestored => Left(InvalidEventOrder(
        "a void marker replays only through the journal fold"))
      case GameStarted(chronicle, orders) =>
        state match
          case NoGame =>
            oathdigital.gameplay.setup.GameStartRules
              .evolve(catalog, chronicle, orders).map(Ready.apply)
          case _ => Left(GameAlreadyExists)

  /** Builds and evolves `GameStarted`, then immediately runs the triggered
    * `Setup` procedure to its first park or its end -- both land in the
    * same command, so a client sees one command produce however many
    * `WalkerStepRecorded` facts Setup's first player's turn takes (2026-09-21
    * Chronicle design, slice 2).
    */
  def beginGame(state: OathState, chronicle: Chronicle, orders: SetupOrders)
      : Either[OathViolation, OathTransition] = state match
    case NoGame =>
      val event = GameStarted(chronicle, orders)
      for
        started <- GameplayTransition(state, Vector(event))(evolve)
        withSetup <- startTriggered(started, TriggeredProcedureRef.Setup)
      yield withSetup
    case _ => Left(GameAlreadyExists)

object OathRules:
  /** How a walker command derives its procedure tree. `starting`
    * distinguishes a fresh start, which runs the procedure's start gates,
    * from resume reconstruction.
    */
  type WalkerTreeSource = (ExecutableCatalog, ProcedureRef, ReadyGame,
    PlayerId, Vector[DecisionOptionRef], Boolean) => Either[OathViolation,
    Operation]

  /** Production tree source: every registered procedure declares its own
    * tree via [[oathdigital.gameplay.walker.WalkerProcedureRegistry]] (Task
    * 8) -- this is no longer an exhaustive match on [[oathdigital.model.ActionRef]], so an
    * unregistered procedure rejects with a typed `Left` instead of a
    * `MatchError`.
    */
  val declaredWalkerTree: WalkerTreeSource =
    (catalog, procedure, ready, actor, args, starting) =>
      if starting then WalkerProcedureRegistry.build(procedure, catalog, ready,
        actor, args)
      else WalkerProcedureRegistry.rebuild(procedure, catalog, ready, actor,
        args)
