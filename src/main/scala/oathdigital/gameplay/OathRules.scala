package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.actions.{Campaign, CampaignCommand,
  CampaignLosingForceRegistry, Challenge, ChallengeCommand}
import oathdigital.gameplay.actions.{MinorActions, MinorActionCommand}
import oathdigital.gameplay.actions.Visions
import oathdigital.gameplay.actions.{Negotiation, NegotiationCommand}
import oathdigital.gameplay.phases.rest.{TurnBoundary,
  WarExhaustionRandomPort}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.oathkeeper.{OathkeeperOutcome, OathkeeperRules}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powerresolver.{PhasePowers}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted,
  WalkerParked, WalkerPowers, WalkerProcedureRegistry, WalkerStepRecorded}
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
    campaignLosingForceRegistry: CampaignLosingForceRegistry =
      CampaignLosingForceRegistry.default,
    protected val warExhaustionRandomPort: WarExhaustionRandomPort =
      WarExhaustionRandomPort.random,
    protected val walkerPowerCatalog: WalkerPowers = WalkerPowers.empty,
    protected val walkerTree: OathRules.WalkerTreeSource =
      OathRules.declaredWalkerTree,
    protected val phasePowerCatalog: PhasePowers = PhasePowers.empty)
    extends EventEvolution[OathState, OathEvent, OathViolation]
    with OathRulesWalker {
  private val setup = new FirstGameSetupRules(catalog)

  override val initialState: OathState = setup.initialState

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

  def handle(state: OathState, command: ChallengeCommand)
      : Either[OathViolation, OathTransition] = unlessWalkerPending(state) {
    (command match {
      case begin: ChallengeCommand.Begin => withFallback(state, begin.player,
        MajorActionKind.Challenge)(Challenge.handle(catalog, state, command))
      case _ => Challenge.handle(catalog, state, command)
    }).flatMap { transition => command match {
      case _: ChallengeCommand.Complete | _: ChallengeCommand.PlaceResource =>
        completeAction(transition)
      case _ => Right(transition)
    }}
  }

  def handle(state: OathState, command: MinorActionCommand)
      : Either[OathViolation, OathTransition] = unlessWalkerPending(state) {
    MinorActions.handle(catalog, state, command).flatMap(completeAction _)
  }

  def handle(state: OathState, command: NegotiationCommand)
      : Either[OathViolation, OathTransition] = unlessWalkerPending(state) {
    Negotiation.handle(catalog, state, command).flatMap { transition => command match {
      case _: NegotiationCommand.Accept if (transition.state match {
        case Ready(ready) => ready.game.current.pending.isEmpty
        case _ => false
      }) => completeAction(transition)
      case _: NegotiationCommand.Decline => completeAction(transition)
      case _ => Right(transition)
    }}
  }

  def handle(state: OathState, command: CampaignCommand)
      : Either[OathViolation, OathTransition] = unlessWalkerPending(state) {
    (command match {
      case begin: CampaignCommand.Start => withFallback(state, begin.playerId,
        MajorActionKind.Campaign)(Campaign.handle(catalog, state, command,
          campaignLosingForceRegistry))
      case begin: CampaignCommand.StartRaid => withFallback(state, begin.playerId,
        MajorActionKind.Campaign)(Campaign.handle(catalog, state, command,
          campaignLosingForceRegistry))
      case _ => Campaign.handle(catalog, state, command, campaignLosingForceRegistry)
    })
      .flatMap { transition =>
      command match {
        case _: CampaignCommand.Place | _: CampaignCommand.RelocateRaidPawn =>
          completeAction(transition)
        case _: CampaignCommand.Sacrifice if (transition.state match {
          case Ready(ready) => ready.game.current.pending.isEmpty
          case _ => false
        }) => completeAction(transition)
        case _ => Right(transition)
      }
    }
  }

  override def evolve(
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] =
    event match {
      case recorded: IgnoredRulesRecorded => state match {
        case Ready(ready) => (if (recorded.action == MajorActionKind.WhenPlayed)
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
      case event: BannerChallengeStarted => Challenge.evolve(catalog, state, event)
      case event: BannerRibbonChoiceMade => Challenge.evolve(catalog, state, event)
      case event: BannerChallengeCompleted => Challenge.evolve(catalog, state, event)
      case event: BannerResourcePlaced => Challenge.evolve(catalog, state, event)
      case event: SiteRelicsPeeked => MinorActions.evolve(catalog, state, event)
      case event: OwnedRelicRevealed => MinorActions.evolve(catalog, state, event)
      case event: WarbandsMoved => MinorActions.evolve(catalog, state, event)
      case event: VisionRevealed => Visions.evolve(catalog, state, event)
      case event: ConspiracyStarted => Visions.evolve(catalog, state, event)
      case event: ConspiracyCompleted => Visions.evolve(catalog, state, event)
      case event: NegotiationStarted => Negotiation.evolve(catalog, state, event)
      case event: NegotiationTermsReplaced => Negotiation.evolve(catalog, state, event)
      case event: NegotiationAccepted => Negotiation.evolve(catalog, state, event)
      case event: NegotiationDeclined => Negotiation.evolve(catalog, state, event)
      case event: NegotiationCompleted => Negotiation.evolve(catalog, state, event)
      case event: CampaignStarted => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignPlanChosen => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignPlansFinished => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignSacrificed => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignConquered => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignRaided => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: CampaignRaidPawnRelocated => Campaign.evolve(catalog, state, event,
        campaignLosingForceRegistry)
      case event: BanditsRefilled => StateBasedEvaluation.evolve(catalog, state, event)
      case event: UsurperFlipped => StateBasedEvaluation.evolve(catalog, state, event)
      case event: UsurperVictory => StateBasedEvaluation.evolve(catalog, state, event)
      case event: VisionVictory => StateBasedEvaluation.evolve(catalog, state, event)
      case event: RoundEnded => StateBasedEvaluation.evolve(catalog, state, event)
      case event: WarExhaustionResolved =>
        StateBasedEvaluation.evolve(catalog, state, event)
      case setupEvent => setup.evolve(state, setupEvent)
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
          MajorActionKind.ActionBoundary).map { diagnostics =>
          if (diagnostics.isEmpty) transition else transition.copy(events =
            transition.events :+ IgnoredRulesRecorded(actor,
              MajorActionKind.ActionBoundary, diagnostics))
        }
      case _ => Right(transition)
    }

  protected def withFallback(state: OathState, actor: PlayerId,
      action: MajorActionKind)(
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
        ready.game.current.turn.activePlayer, MajorActionKind.Wake).map { diagnostics =>
        if (diagnostics.isEmpty) transition else transition.copy(events =
          transition.events :+ IgnoredRulesRecorded(
            ready.game.current.turn.activePlayer, MajorActionKind.Wake, diagnostics))
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
