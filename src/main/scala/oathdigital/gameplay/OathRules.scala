package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.actions.{Campaign, CampaignCommand,
  CampaignLosingForceRegistry, Challenge, ChallengeCommand, Economy, EconomyCommand, Forge, ForgeCommand, Recover,
  PreparedRecoverModifier, RecoverCommand, Search, SearchCommand, Travel,
  TravelCommand}
import oathdigital.gameplay.actions.{MinorActions, MinorActionCommand}
import oathdigital.gameplay.actions.{Visions, VisionCommand}
import oathdigital.gameplay.actions.{Negotiation, NegotiationCommand}
import oathdigital.gameplay.phases.{Rest, RestCommand, Wake, WakeCommand,
  WarExhaustionRandomPort}
import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.powers.recover.RecoverPowerIntegration
import oathdigital.gameplay.powers.SearchPowers
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

/** Deterministic aggregate boundary for setup and gameplay routing. */
final class OathRules(catalog: ExecutableCatalog,
    campaignLosingForceRegistry: CampaignLosingForceRegistry =
      CampaignLosingForceRegistry.default,
    warExhaustionRandomPort: WarExhaustionRandomPort =
      WarExhaustionRandomPort.random)
    extends EventEvolution[OathState, OathEvent, OathViolation] {
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
          complete.kept, complete.placement, prepend = false)
      case _ => Right(transition)
    }

  def handle(state: OathState, command: RecoverCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case start: RecoverCommand.Start => withFallback(state, start.playerId,
        MajorActionKind.Recover)(start.modifier match {
          case None => Recover.handle(catalog, state, command)
          case Some(PreparedRecoverModifier(events)) =>
            GameplayTransition(state, events,
              OathContinue.ActActionSelection(start.playerId))(evolve).flatMap {
              prepared => Recover.handle(catalog, prepared.state, RecoverCommand.Roll(
                start.playerId, start.decision, start.dice)).map(rolled =>
                rolled.copy(events = prepared.events ++ rolled.events))
            }
          case Some(_) => Left(InvalidModifierInvocation(
            "unknown Recover modifier contribution"))
        })
      case roll: RecoverCommand.Roll => withFallback(state, roll.playerId,
        MajorActionKind.Recover)(Recover.handle(catalog, state, command))
      case _ => Recover.handle(catalog, state, command)
    }).flatMap { transition =>
      command match {
        case _: RecoverCommand.Stop | _: RecoverCommand.TakeRelic =>
          completeAction(transition)
        case _ => Right(transition)
      }
    }

  def handle(state: OathState, command: ForgeCommand)
      : Either[OathViolation, OathTransition] =
    (command match {
      case begin: ForgeCommand.Begin => withFallback(state, begin.playerId,
        MajorActionKind.Forge)(Forge.handle(catalog, state, command))
      case _ => Forge.handle(catalog, state, command)
    }).flatMap { transition =>
      command match {
        case _: ForgeCommand.Complete => completeAction(transition)
        case _ => Right(transition)
      }
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
            play.adviser,
            play.placement, prepend = true)
        case _ => Right(transition)
      }
    }.flatMap(completeAction _)

  def handle(state: OathState, command: VisionCommand)
      : Either[OathViolation, OathTransition] =
    Visions.handle(catalog, state, command).flatMap { transition => command match {
      case _: VisionCommand.ChooseSecretSite | _: VisionCommand.PlayConspiracy
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
      case event: RecoverRolled => Recover.evolve(catalog, state, event)
      case event: RecoverPowerEvent =>
        RecoverPowerIntegration.evolve(catalog, state, event)
      case event: RecoverStopped => Recover.evolve(catalog, state, event)
      case event: RelicRecovered => Recover.evolve(catalog, state, event)
      case event: ForgeStarted => Forge.evolve(catalog, state, event)
      case event: ForgeCompleted => Forge.evolve(catalog, state, event)
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
      case event: ConspiracySecretSiteChosen => Visions.evolve(catalog, state, event)
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

  private def completeAction(transition: OathTransition) =
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

  private def withFallback(state: OathState, actor: PlayerId,
      action: MajorActionKind)(operation: => Either[OathViolation, OathTransition]) =
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

private[gameplay] object GameStateUpdates {
  def updateCurrent(
      ready: ReadyGame
  )(f: CurrentGameState => CurrentGameState): ReadyGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))
}
