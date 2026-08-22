package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.actions.{Campaign, CampaignCommand,
  CampaignLosingForceRegistry, Challenge, ChallengeCommand, Economy, EconomyCommand, Forge, ForgeCommand, Recover,
  RecoverCommand, Search, SearchCommand, Travel, TravelCommand}
import oathdigital.gameplay.actions.{MinorActions, MinorActionCommand}
import oathdigital.gameplay.actions.{Negotiation, NegotiationCommand}
import oathdigital.gameplay.phases.{Rest, RestCommand, Wake, WakeCommand}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState._
import oathdigital.setup.OathViolation._

/** Deterministic aggregate boundary for setup and gameplay routing. */
final class OathRules(catalog: ExecutableCatalog,
    campaignLosingForceRegistry: CampaignLosingForceRegistry =
      CampaignLosingForceRegistry.default)
    extends EventEvolution[OathState, OathEvent, OathViolation] {
  private val setup = new FirstGameSetupRules(catalog)

  override val initialState: OathState = setup.initialState

  def handle(
      state: OathState,
      command: WakeCommand
  ): Either[OathViolation, OathTransition] =
    Wake.handle(state, command)

  def handle(
      state: OathState,
      command: TravelCommand
  ): Either[OathViolation, OathTransition] =
    Travel.handle(catalog, state, command).flatMap(completeAction)

  def handle(state: OathState, command: EconomyCommand)
      : Either[OathViolation, OathTransition] =
    Economy.handle(catalog, state, command).flatMap(completeAction)

  def handle(
      state: OathState,
      command: SearchCommand
  ): Either[OathViolation, OathTransition] =
    Search.handle(catalog, state, command).flatMap { transition =>
      command match {
        case _: SearchCommand.Complete => completeAction(transition)
        case _ => Right(transition)
      }
    }

  def handle(state: OathState, command: RecoverCommand)
      : Either[OathViolation, OathTransition] =
    Recover.handle(catalog, state, command).flatMap { transition =>
      command match {
        case _: RecoverCommand.Stop | _: RecoverCommand.TakeRelic =>
          completeAction(transition)
        case _ => Right(transition)
      }
    }

  def handle(state: OathState, command: ForgeCommand)
      : Either[OathViolation, OathTransition] =
    Forge.handle(catalog, state, command).flatMap { transition =>
      command match {
        case _: ForgeCommand.Complete => completeAction(transition)
        case _ => Right(transition)
      }
    }

  def handle(state: OathState, command: ChallengeCommand)
      : Either[OathViolation, OathTransition] =
    Challenge.handle(catalog, state, command).flatMap { transition => command match {
      case _: ChallengeCommand.Complete | _: ChallengeCommand.PlaceResource =>
        completeAction(transition)
      case _ => Right(transition)
    }}

  def handle(state: OathState, command: MinorActionCommand)
      : Either[OathViolation, OathTransition] =
    MinorActions.handle(catalog, state, command).flatMap(completeAction)

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
    Campaign.handle(catalog, state, command, campaignLosingForceRegistry)
      .flatMap { transition =>
      command match {
        case _: CampaignCommand.Place => completeAction(transition)
        case _: CampaignCommand.Sacrifice if (transition.state match {
          case Ready(ready) => ready.game.current.pending.isEmpty
          case _ => false
        }) => completeAction(transition)
        case _ => Right(transition)
      }
    }

  def handle(state: OathState, command: RestCommand)
      : Either[OathViolation, OathTransition] =
    Rest.handle(catalog, state, command).flatMap { transition =>
      command match {
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
      case event: WealthTaken => Wake.evolve(state, event)
      case event: WakeEnded => Wake.evolve(state, event)
      case event: Traveled => Travel.evolve(catalog, state, event)
      case event: Mustered => Economy.evolve(catalog, state, event)
      case event: Traded => Economy.evolve(catalog, state, event)
      case event: SearchStarted => Search.evolve(catalog, state, event)
      case event: SearchCompleted => Search.evolve(catalog, state, event)
      case event: RecoverRolled => Recover.evolve(catalog, state, event)
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
      case setupEvent => setup.evolve(state, setupEvent)
    }

  private def completeAction(transition: OathTransition) =
    appendEvaluation(transition, StateBasedEvaluation.banditRefill(catalog, _))
      .flatMap(appendEvaluation(_, StateBasedEvaluation.afterAction))

  private def enterWake(transition: OathTransition) =
    appendEvaluation(transition, StateBasedEvaluation.atWake)

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

private[gameplay] object OathLifecycle {
  def validateReady(
      state: OathState,
      playerId: PlayerId
  ): Either[OathViolation, ReadyGame] =
    state match {
      case NoGame | _: InProgress => Left(GameNotStarted)
      case Ready(ready) =>
        val current = ready.game.current
        if (current.result.nonEmpty) Left(GameEnded)
        else if (current.turn.activePlayer != playerId)
          Left(WrongPlayer(current.turn.activePlayer, playerId))
        else if (current.turn.phase != Phase.Wake)
          Left(WrongPhase(Phase.Wake, current.turn.phase))
        else Right(ready)
    }

  def validateAct(
      state: OathState,
      playerId: PlayerId
  ): Either[OathViolation, ReadyGame] = state match {
    case NoGame | _: InProgress => Left(GameNotStarted)
    case Ready(ready) =>
      val current = ready.game.current
      if (current.result.nonEmpty) Left(GameEnded)
      else if (current.turn.activePlayer != playerId)
        Left(WrongPlayer(current.turn.activePlayer, playerId))
      else if (current.turn.phase != Phase.Act)
        Left(WrongPhase(Phase.Act, current.turn.phase))
      else current.pending match {
        case Some(value) => Left(PendingProcedureBlocksAction(value.decision))
        case None => Right(ready)
      }
  }

  def validateSearchDecision(
      state: OathState,
      playerId: PlayerId,
      decision: DecisionId
  ): Either[OathViolation, ReadyGame] = state match {
    case Ready(ready) if ready.game.current.turn.activePlayer != playerId =>
      Left(WrongPlayer(ready.game.current.turn.activePlayer, playerId))
    case Ready(ready) if ready.game.current.turn.phase != Phase.Act =>
      Left(WrongPhase(Phase.Act, ready.game.current.turn.phase))
    case Ready(ready) => ready.game.current.pending match {
      case Some(value: PendingProcedure.Search) if value.actor != playerId =>
        Left(WrongPlayer(value.actor, playerId))
      case Some(value: PendingProcedure.Search) if value.decision != decision =>
        Left(SearchDecisionMismatch(value.decision, decision))
      case Some(_: PendingProcedure.Search) => Right(ready)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Search decision is pending"))
    }
    case _ => Left(GameNotStarted)
  }
}

private[gameplay] object GameStateUpdates {
  def updateCurrent(
      ready: ReadyGame
  )(f: CurrentGameState => CurrentGameState): ReadyGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))
}
