package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.actions.{MinorActions, MinorActionCommand}
import oathdigital.gameplay.phases.rest.{TurnBoundary,
  WarExhaustionRandomPort}
import oathdigital.model._
import oathdigital.gameplay.oathkeeper.{OathkeeperOutcome, OathkeeperRules}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.{PhasePowers}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted,
  WalkerDice, WalkerParked, WalkerPowers, WalkerProcedureRegistry, WalkerStepRecorded}
import oathdigital.gameplay._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState._
import oathdigital.model.OathViolation._

/** Deterministic aggregate boundary for setup and gameplay routing.
  *
  * `walkerPowerCatalog` is the full set of `ContributingPower`s this instance
  * may offer to a walker command (empty in production until Task 5 registers
  * a real power) -- a constructor parameter so a suite can drive a real
  * command against a real power. `OathRules.walkerPowers` (Task 4) is the
  * per-command projection of that catalog: restrictions are gathered from it
  * at command entry and its transforms fold the tree during the walk.
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
    with OathRulesWalker {
  override val initialState: OathState = NoGame

  /** Walker-ownership invariant: while a walker procedure is parked, only
    * its resume commands run. `GameApplicationService.applyCommand` refuses
    * other commands first; this keeps the rules boundary honest for every
    * other caller.
    */
  private def unlessWalkerPending(state: OathState)(
      handled: => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition] = state match {
    case Ready(ready) if ready.game.current.walkerPending.nonEmpty ||
        ready.game.current.walkerProcedure.nonEmpty =>
      Left(InvalidEventOrder("a walker procedure is already pending"))
    case _ => handled
  }

  def handle(state: OathState, command: MinorActionCommand)
      : Either[OathViolation, OathTransition] = unlessWalkerPending(state) {
    MinorActions.handle(catalog, state, command).flatMap(completeAction _)
  }

  override def evolve(
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] =
    event match {
      case recorded: IgnoredRulesRecorded => state match {
        case Ready(ready) => (if (recorded.action == ActionKind.WhenPlayed)
          recorded.diagnostics.map(_.source).distinct.foldLeft[
            Either[OathViolation, Vector[IgnoredRuleDiagnostic]]](Right(Vector.empty)) {
              case (Right(found), source) => PowerRuntime.ignoredAtSource(
                catalog, ready, recorded.playerId, recorded.action, source)
                .map(found ++ _)
              case (failure @ Left(_), _) => failure
            }
          else PowerRuntime.ignored(catalog, ready,
            recorded.playerId, recorded.action)).flatMap(expected =>
          Either.cond(expected == recorded.diagnostics, state,
            InvalidEventOrder("ignored-rule diagnostics do not match authoritative discovery")))
        case _ => Left(GameNotStarted)
      }
      case event: WalkerStepRecorded => ProcedureWalker.applyRecorded(state, event)
      case event: WalkerParked => ProcedureWalker.applyRecorded(state, event)
      case event: WalkerCompleted => ProcedureWalker.applyRecorded(state, event)
      case event: SiteRelicsPeeked => MinorActions.evolve(catalog, state, event)
      case event: OwnedRelicRevealed => MinorActions.evolve(catalog, state, event)
      case event: WarbandsMoved => MinorActions.evolve(catalog, state, event)
      case event: BanditsRefilled => StateBasedEvaluation.evolve(catalog, state, event)
      case event: UsurperFlipped => StateBasedEvaluation.evolve(catalog, state, event)
      case event: UsurperVictory => StateBasedEvaluation.evolve(catalog, state, event)
      case event: VisionVictory => StateBasedEvaluation.evolve(catalog, state, event)
      case event: RoundEnded => StateBasedEvaluation.evolve(catalog, state, event)
      case event: WarExhaustionResolved =>
        StateBasedEvaluation.evolve(catalog, state, event)
      case GameStarted(chronicle, orders) =>
        state match {
          case NoGame =>
            oathdigital.gameplay.setup.GameStartRules
              .evolve(catalog, chronicle, orders).map(Ready)
          case _ => Left(GameAlreadyExists)
        }
    }

  /** Builds and evolves `GameStarted`, then immediately runs the triggered
    * `Setup` procedure to its first park or its end -- both land in the
    * same command, so a client sees one command produce however many
    * `WalkerStepRecorded` facts Setup's first player's turn takes (2026-09-21
    * Chronicle design, slice 2).
    */
  def beginGame(state: OathState, chronicle: Chronicle, orders: SetupOrders)
      : Either[OathViolation, OathTransition] = state match {
    case NoGame =>
      val event = GameStarted(chronicle, orders)
      for {
        started <- GameplayTransition(state, Vector(event),
          OathContinue.AwaitingSetupPawn(orders.firstPlayer,
            DecisionId(oathdigital.gameplay.setup.SetupProcedure
              .pawnDecisionId(orders.firstPlayer))))(evolve)
        withSetup <- startTriggered(started, TriggeredProcedureRef.Setup)
      } yield withSetup
    case _ => Left(GameAlreadyExists)
  }

  protected def completeAction(transition: OathTransition)
      : Either[OathViolation, OathTransition] =
    recordBoundaryFallback(transition)
      .flatMap(appendEvaluation(_, StateBasedEvaluation.banditRefill(catalog, _)))
      .flatMap(oathkeeperStep)

  protected def turnBoundary(transition: OathTransition)
      : Either[OathViolation, OathTransition] = {
    val rounded = transition.state match {
      case Ready(ready) if ready.game.current.turn.phase == Phase.RoundEnd =>
        TurnBoundary.finishRound(catalog, transition, warExhaustionRandomPort)
      case _ => Right(transition)
    }
    rounded.flatMap(next => next.state match {
      case Ready(ready) if ready.game.current.result.nonEmpty => Right(next)
      case _ => enterWake(next)
    })
  }

  protected def restPowerUsable(ready: ReadyGame, player: PlayerId): Boolean =
    PhasePowerProcedure.usable(catalog, ready, player, phasePowerCatalog).nonEmpty

  /** The boundary decides only WHETHER the title changes; the triggered
    * procedure performs the change, so every title change is one walker step.
    */
  private def oathkeeperStep(transition: OathTransition)
      : Either[OathViolation, OathTransition] = transition.state match {
    case Ready(ready) => StateBasedEvaluation.supported(transition.state)
      .flatMap(_ => OathkeeperRules.outcome(ready) match {
        case OathkeeperOutcome.NoChange => Right(transition)
        case _ => startTriggered(transition, TriggeredProcedureRef.Oathkeeper)
      })
    case _ => Right(transition)
  }

  private def recordBoundaryFallback(transition: OathTransition) =
    transition.state match {
      case Ready(ready) =>
        val actor = ready.game.current.turn.activePlayer
        PowerRuntime.ignored(catalog, ready, actor,
          ActionKind.ActionBoundary).map { diagnostics =>
          if (diagnostics.isEmpty) transition else transition.copy(events =
            transition.events :+ IgnoredRulesRecorded(actor,
              ActionKind.ActionBoundary, diagnostics))
        }
      case _ => Right(transition)
    }

  protected def withFallback(state: OathState, actor: PlayerId,
      action: ActionKind)(
      operation: => Either[OathViolation, OathTransition])
      : Either[OathViolation, OathTransition] =
    state match {
      case Ready(ready) => PowerRuntime.ignored(catalog, ready, actor, action)
        .flatMap { diagnostics => operation.map { transition =>
          if (diagnostics.isEmpty) transition
          else transition.copy(events = IgnoredRulesRecorded(actor, action,
            diagnostics) +: transition.events)
        }}
      case _ => operation
    }

  private def enterWake(transition: OathTransition): Either[OathViolation, OathTransition] = {
    def append(current: OathTransition, event: OathEvent) =
      evolve(current.state, event).map(next => current.copy(state = next,
        events = current.events :+ event, continue = event match {
          case UsurperVictory(winner) => OathContinue.GameFinished(winner)
          case VisionVictory(winner, _) => OathContinue.GameFinished(winner)
          case _ => current.continue
        }))
    val prepared = transition.state match {
      case Ready(ready) => PowerRuntime.ignored(catalog, ready,
        ready.game.current.turn.activePlayer, ActionKind.Wake).map { diagnostics =>
        if (diagnostics.isEmpty) transition else transition.copy(events =
          transition.events :+ IgnoredRulesRecorded(
            ready.game.current.turn.activePlayer, ActionKind.Wake, diagnostics))
      }
      case _ => Right(transition)
    }
    prepared.flatMap(current => StateBasedEvaluation.atWake(current.state).flatMap {
      case Some(win: UsurperVictory) => append(current, win)
      case Some(flip: UsurperFlipped) => append(current, flip).flatMap { after =>
        StateBasedEvaluation.visionAtWake(after.state).flatMap {
          case Some(vision) => append(after, vision)
          case None => Right(after)
        }
      }
      case None => StateBasedEvaluation.visionAtWake(current.state).flatMap {
        case Some(vision) => append(current, vision)
        case None => Right(current)
      }
      case Some(other) => Left(InvalidEventOrder(
        s"unexpected Wake evaluation event: $other"))
    })
  }

  private def appendEvaluation(transition: OathTransition,
      evaluate: OathState => Either[OathViolation, Option[OathEvent]]) =
    evaluate(transition.state).flatMap {
      case None => Right(transition)
      case Some(event) => evolve(transition.state, event).map(next =>
        transition.copy(state = next, events = transition.events :+ event))
    }
}

object OathRules {
  /** How a walker command derives its procedure tree. `starting`
    * distinguishes a fresh start, which runs the procedure's start gates,
    * from resume reconstruction.
    */
  type WalkerTreeSource = (ExecutableCatalog, ProcedureRef, ReadyGame,
    PlayerId, Vector[DecisionOptionRef], Boolean) => Either[OathViolation,
    Operation]

  /** Production tree source: every registered procedure declares its own
    * tree via [[oathdigital.gameplay.walker.WalkerProcedureRegistry]] (Task
    * 8) -- this is no longer an exhaustive match on [[ActionRef]], so an
    * unregistered procedure rejects with a typed `Left` instead of a
    * `MatchError`.
    */
  val declaredWalkerTree: WalkerTreeSource =
    (catalog, procedure, ready, actor, args, starting) =>
      if (starting) WalkerProcedureRegistry.build(procedure, catalog, ready,
        actor, args)
      else WalkerProcedureRegistry.rebuild(procedure, catalog, ready, actor,
        args)
}
