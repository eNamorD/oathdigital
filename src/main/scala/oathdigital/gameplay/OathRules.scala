package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.actions.{Campaign, CampaignCommand,
  CampaignLosingForceRegistry, Challenge, ChallengeCommand, Economy, EconomyCommand,
  Search, SearchCommand, Travel,
  TravelCommand}
import oathdigital.gameplay.actions.{MinorActions, MinorActionCommand}
import oathdigital.gameplay.actions.{Visions, VisionCommand}
import oathdigital.gameplay.actions.{Negotiation, NegotiationCommand}
import oathdigital.gameplay.phases.{Rest, RestCommand, Wake, WakeCommand,
  WarExhaustionRandomPort}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.powers.SearchPowers
import oathdigital.gameplay.operations.Operation
import oathdigital.gameplay.powerresolver.{ContributingPower,
  ContributionCollector, PowerCtx}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerActionRegistry,
  WalkerCompleted, WalkerParked, WalkerPowers, WalkerStepRecorded}
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

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
    warExhaustionRandomPort: WarExhaustionRandomPort =
      WarExhaustionRandomPort.random,
    protected val walkerPowerCatalog: WalkerPowers = WalkerPowers.empty,
    protected val walkerTree: OathRules.WalkerTreeSource =
      OathRules.declaredWalkerTree)
    extends EventEvolution[OathState, OathEvent, OathViolation]
    with OathRulesWalker {
  private val setup = new FirstGameSetupRules(catalog)

  override val initialState: OathState = setup.initialState

  def handle(
      state: OathState,
      command: WakeCommand
  ): Either[OathViolation, OathTransition] =
    command match {
      case WakeCommand.EndWake(actor) => withFallback(state, actor,
        MajorActionKind.Wake)(Wake.handle(state, command))
      case _ => Wake.handle(state, command)
    }

  def handle(
      state: OathState,
      command: TravelCommand
  ): Either[OathViolation, OathTransition] =
    command match { case TravelCommand.Travel(actor, _) =>
      withFallback(state, actor, MajorActionKind.Travel)(
        Travel.handle(catalog, state, command)).flatMap(completeAction _)
    }

  def handle(state: OathState, command: EconomyCommand)
      : Either[OathViolation, OathTransition] =
    command match {
      case value: EconomyCommand.Muster => withFallback(state, value.playerId,
        MajorActionKind.Muster)(Economy.handle(catalog, state, command))
        .flatMap(completeAction _)
      case value: EconomyCommand.Trade => withFallback(state, value.playerId,
        MajorActionKind.Trade)(Economy.handle(catalog, state, command))
        .flatMap(completeAction _)
    }

  def handle(
      state: OathState,
      command: SearchCommand
  ): Either[OathViolation, OathTransition] =
    (command match {
      case start: SearchCommand.Start => withFallback(state, start.playerId,
        MajorActionKind.Search)(Search.handle(catalog, state, command))
      case _ => Search.handle(catalog, state, command)
    }).flatMap(transition => recordSearchWhenPlayed(command, transition))
      .flatMap { transition =>
      command match {
        case _: SearchCommand.Complete if (transition.state match {
          case Ready(ready) => ready.game.current.pending.exists {
            case p: PendingProcedure.Conspiracy => p.awaitingTarget
            case _ => false
          }
          case _ => false
        }) => Right(transition.copy(continue = transition.state match {
          case Ready(ready) => ready.game.current.pending.collect {
            case p: PendingProcedure.Conspiracy =>
              OathContinue.AwaitingConspiracyDecision(p.actor, p.decision)
          }.get
          case _ => transition.continue
        }))
        case _: SearchCommand.Complete => completeAction(transition)
        case _ => Right(transition)
      }
    }

  private def recordSearchWhenPlayed(command: SearchCommand,
      transition: OathTransition): Either[OathViolation, OathTransition] =
    (command, transition.state) match {
      case (complete: SearchCommand.Complete, Ready(ready)) =>
        SearchPowers.recordPlayHooks(catalog, transition, ready, complete.playerId,
          complete.kept, complete.placement)
      case _ => Right(transition)
    }

  def handle(state: OathState, command: ChallengeCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case begin: ChallengeCommand.Begin => withFallback(state, begin.player,
        MajorActionKind.Challenge)(Challenge.handle(catalog, state, command))
      case _ => Challenge.handle(catalog, state, command)
    }).flatMap { transition => command match {
      case _: ChallengeCommand.Complete | _: ChallengeCommand.PlaceResource =>
        completeAction(transition)
      case _ => Right(transition)
    }}

  def handle(state: OathState, command: MinorActionCommand)
      : Either[OathViolation, OathTransition] =
    MinorActions.handle(catalog, state, command).flatMap { transition =>
      (command, transition.state) match {
        case (play: MinorActionCommand.PlayFacedownAdviser, Ready(ready)) =>
          SearchPowers.recordPlayHooks(catalog, transition, ready, play.player,
            play.adviser, play.placement)
        case _ => Right(transition)
      }
    }.flatMap(completeAction _)

  def handle(state: OathState, command: VisionCommand)
      : Either[OathViolation, OathTransition] =
    Visions.handle(catalog, state, command).flatMap { transition => command match {
      case _: VisionCommand.PlayConspiracy
          if (transition.state match {
            case Ready(ready) => ready.game.current.pending.isEmpty
            case _ => false
          }) => completeAction(transition)
      case _: VisionCommand.Reveal => completeAction(transition)
      case _ => Right(transition)
    }}

  def handle(state: OathState, command: NegotiationCommand)
      : Either[OathViolation, OathTransition] =
    Negotiation.handle(catalog, state, command).flatMap { transition => command match {
      case _: NegotiationCommand.Accept if (transition.state match {
        case Ready(ready) => ready.game.current.pending.isEmpty
        case _ => false
      }) => completeAction(transition)
      case _: NegotiationCommand.Decline => completeAction(transition)
      case _ => Right(transition)
    }}

  def handle(state: OathState, command: CampaignCommand)
      : Either[OathViolation, OathTransition] =
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

  def handle(state: OathState, command: RestCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case begin: RestCommand.Begin => withFallback(state, begin.playerId,
        MajorActionKind.Rest)(Rest.handle(catalog, state, command,
          warExhaustionRandomPort))
      case _ => Rest.handle(catalog, state, command, warExhaustionRandomPort)
    }).flatMap { transition =>
      command match {
        case _: RestCommand.Finish if (transition.state match {
          case Ready(ready) => ready.game.current.result.nonEmpty
          case _ => false
        }) => Right(transition)
        case _: RestCommand.Finish => enterWake(transition)
        case _ => Right(transition)
      }
    }

  def chooseOathkeeperRecipient(state: OathState, actor: PlayerId,
      decision: DecisionId, recipient: PlayerId) =
    StateBasedEvaluation.chooseRecipient(
      catalog, state, actor, decision, recipient)

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
      case event: WealthTaken => Wake.evolve(state, event)
      case event: WakeEnded => Wake.evolve(state, event)
      case event: Traveled => Travel.evolve(catalog, state, event)
      case event: Mustered => Economy.evolve(catalog, state, event)
      case event: Traded => Economy.evolve(catalog, state, event)
      case event: SearchStarted => Search.evolve(catalog, state, event)
      case event: SearchCompleted => Search.evolve(catalog, state, event)
      case event: WalkerStepRecorded => ProcedureWalker.applyRecorded(state, event)
      case event: WalkerParked => ProcedureWalker.applyRecorded(state, event)
      case event: WalkerCompleted => ProcedureWalker.applyRecorded(state, event)
      case event: BannerChallengeStarted => Challenge.evolve(catalog, state, event)
      case event: BannerRibbonChoiceMade => Challenge.evolve(catalog, state, event)
      case event: BannerChallengeCompleted => Challenge.evolve(catalog, state, event)
      case event: BannerResourcePlaced => Challenge.evolve(catalog, state, event)
      case event: FacedownAdviserDiscarded => MinorActions.evolve(catalog, state, event)
      case event: FacedownAdviserPlayed => MinorActions.evolve(catalog, state, event)
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
      case event: RestStarted => Rest.evolve(catalog, state, event)
      case event: RestPowerEvent => Rest.evolve(catalog, state, event)
      case event: RestCompleted => Rest.evolve(catalog, state, event)
      case event: BanditsRefilled => StateBasedEvaluation.evolve(catalog, state, event)
      case event: OathkeeperChanged => StateBasedEvaluation.evolve(catalog, state, event)
      case event: OathkeeperRecipientChoiceStarted =>
        StateBasedEvaluation.evolve(catalog, state, event)
      case event: OathkeeperRecipientChosen =>
        StateBasedEvaluation.evolve(catalog, state, event)
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
    if (hasResult(transition.state)) Right(transition)
    else recordBoundaryFallback(transition).flatMap(afterDiagnostics =>
      appendEvaluation(afterDiagnostics,
      StateBasedEvaluation.banditRefill(catalog, _)))
      .flatMap { afterRefill =>
        if (hasResult(afterRefill.state)) Right(afterRefill)
        else appendEvaluation(afterRefill, StateBasedEvaluation.afterAction)
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

  private def hasResult(state: OathState): Boolean = state match {
    case Ready(ready) => ready.game.current.result.nonEmpty
    case _ => false
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
      case Some(event) => evolve(transition.state, event).map { next =>
        transition.copy(state = next, events = transition.events :+ event,
          continue = event match {
            case UsurperVictory(winner) => OathContinue.GameFinished(winner)
            case OathkeeperRecipientChoiceStarted(actor, decision, _) =>
              OathContinue.AwaitingOathkeeperRecipient(actor, decision)
            case _ => transition.continue
          })
      }
    }
}

object OathRules {
  /** How a walker command derives the action tree it walks. `starting`
    * distinguishes a fresh `startWalker` (the action's full start gates) from
    * a resume (rebuild only). `eligibilityRelaxed` (Task 5, ruling B) carries
    * `startWalker`'s relaxed-eligibility decision through to a `starting`
    * build; a resume ignores it (its rebuild never re-runs the start gates).
    */
  type WalkerTreeSource = (ExecutableCatalog, ActionRef, ReadyGame, PlayerId,
    Boolean, Boolean) => Either[OathViolation, Operation]

  /** Production tree source: every registered action declares its own tree
    * via [[oathdigital.gameplay.walker.WalkerActionRegistry]] (Task 8) --
    * this is no longer an exhaustive match on [[ActionRef]], so an
    * unregistered action rejects with a typed `Left` instead of a
    * `MatchError`.
    */
  val declaredWalkerTree: WalkerTreeSource =
    (catalog, action, ready, actor, starting, eligibilityRelaxed) =>
      if (starting) WalkerActionRegistry.build(action, catalog, ready, actor,
        eligibilityRelaxed)
      else WalkerActionRegistry.rebuild(action, catalog, ready, actor)

  /** I6: the single relaxed-eligibility rule, shared between the command
    * (`OathRules.startWalker`, via `eligibilityGathered`) and the projection
    * layer (`LegalActionProjector.recoverEligible`), so the two never drift
    * apart the way a hand-copied second implementation eventually will.
    *
    * True when SOME applicable power in `powers` declares a `Transform` at
    * ACTION'S OWN eligibility window, read from
    * `WalkerActionRegistry.eligibilityWindow(action)` (batch-1 Task 3,
    * Step 2b) rather than the `PowerWindow.RecoverActionEligibility`
    * literal this method used to name. Every `startWalker` reaches here, so
    * with a second registered action the literal would have gathered
    * Forge's start at Recover's window -- invisible only for as long as no
    * power is applicable there. An entry declaring `eligibilityWindow =
    * None` never relaxes (no gather at all); an action absent from
    * `registrations` is a `Left`, since "registers no window" and "is not
    * registered" are different facts and only the first is a rule -- the
    * same split `offerableWalkerPowers` makes one window over.
    *
    * A `Restriction` at this window never grants
    * eligibility on its own -- it can only reject the action for an
    * unrelated reason, and its mere presence must not be read as "eligible"
    * (that reading previously let a power that *forbids* Recover also
    * *enable* it).
    *
    * This method's contract is only the window and the Transform-presence
    * check; it takes no position on WHICH powers `powers` should contain --
    * that choice is deliberately the caller's, and the two production
    * callers deliberately choose different sets. `startWalker` passes the
    * command's SELECTED powers (`WalkerPowers.selected`, already narrowed to
    * the modifiers this command chose) because it is deciding whether THIS
    * command may proceed. `recoverEligible` passes the FULL catalog because
    * it answers a different question -- "could some power relax this if the
    * player chose it as a modifier" -- asked before any modifier has been
    * picked, so the Recover button can appear on a relic-less site whose
    * only eligibility-granting power has not been selected yet, even
    * though the eventual `StartWalker` command still must select the power
    * to actually use it.
    *
    * NOTE: this checks the *presence* of a Transform, not its *effect*. A
    * future power whose Transform at this window does not actually supply a
    * relic would still relax the gate. Known limitation, worth revisiting
    * once a second power hooks this window.
    */
  def eligibilityRelaxed(ready: ReadyGame, actor: PlayerId, action: ActionRef,
      powers: Vector[ContributingPower],
      registrations: Map[ActionRef, WalkerActionRegistry.Entry] =
        WalkerActionRegistry.entries): Either[OathViolation, Boolean] =
    WalkerActionRegistry.eligibilityWindow(action, registrations).map {
      case None => false
      case Some(window) => ContributionCollector.gather(window, powers,
        power => PowerCtx(ready, actor, power.source, window, Vector.empty))
        .transforms.nonEmpty
    }
}

private[gameplay] object GameStateUpdates {
  def updateCurrent(
      ready: ReadyGame
  )(f: CurrentGameState => CurrentGameState): ReadyGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))
}
