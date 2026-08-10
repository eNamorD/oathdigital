package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupEvent._
import oathdigital.setup.FirstGameSetupState._
import oathdigital.setup.FirstGameSetupViolation._
import oathdigital.gameplay.actions.{Search, SearchCommand, Travel, TravelCommand}
import oathdigital.gameplay.phases.{Wake, WakeCommand}

/** Deterministic aggregate boundary for setup and gameplay routing. */
final class OathRules(catalog: ExecutableCatalog)
    extends EventEvolution[FirstGameSetupState, FirstGameSetupEvent, FirstGameSetupViolation] {
  private val setup = new FirstGameSetupRules(catalog)

  override val initialState: FirstGameSetupState = setup.initialState

  def handle(state: FirstGameSetupState, command: WakeCommand): Either[FirstGameSetupViolation, FirstGameTransition] =
    Wake.handle(catalog, state, command)

  def handle(state: FirstGameSetupState, command: TravelCommand): Either[FirstGameSetupViolation, FirstGameTransition] =
    Travel.handle(catalog, state, command)

  def handle(state: FirstGameSetupState, command: SearchCommand): Either[FirstGameSetupViolation, FirstGameTransition] =
    Search.handle(catalog, state, command)

  override def evolve(state: FirstGameSetupState, event: FirstGameSetupEvent): Either[FirstGameSetupViolation, FirstGameSetupState] =
    event match {
      case event: WealthTaken => Wake.evolve(catalog, state, event)
      case event: WakeEnded => Wake.evolve(catalog, state, event)
      case event: Traveled => Travel.evolve(catalog, state, event)
      case event: SearchStarted => Search.evolve(catalog, state, event)
      case event: SearchCompleted => Search.evolve(catalog, state, event)
      case setupEvent => setup.evolve(state, setupEvent)
    }
}

private[gameplay] object OathLifecycle {
  def validateReady(
      state: FirstGameSetupState,
      playerId: PlayerId
  ): Either[FirstGameSetupViolation, ReadyFirstGame] =
    state match {
      case NoGame | _: InProgress => Left(GameNotStarted)
      case Ready(ready) =>
        val current = ready.game.current
        if (current.result.nonEmpty) Left(GameEnded)
        else if (current.turn.activePlayer != playerId)
          Left(WrongPlayer(current.turn.activePlayer, playerId))
        else if (current.turn.phase != Phase.Wake)
          Left(WrongPhase(Phase.Wake, current.turn.phase))
        else if (current.title.holder.nonEmpty)
          Left(UnsupportedWakeVictoryState("Oathkeeper or Usurper is held"))
        else if (current.players.exists(_.revealedVision.nonEmpty))
          Left(UnsupportedWakeVictoryState("a Vision is revealed"))
        else Right(ready)
    }

  def validateAct(
      state: FirstGameSetupState,
      playerId: PlayerId
  ): Either[FirstGameSetupViolation, ReadyFirstGame] = state match {
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
        case None => actions.TravelRules.validateSupportedState(ready).map(_ => ready)
      }
  }

  def validateSearchDecision(
      state: FirstGameSetupState,
      playerId: PlayerId,
      decision: DecisionId
  ): Either[FirstGameSetupViolation, ReadyFirstGame] = state match {
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
  def updateCurrent(ready: ReadyFirstGame)(f: CurrentGameState => CurrentGameState): ReadyFirstGame =
    ready.copy(game = ready.game.copy(current = f(ready.game.current)))
}
