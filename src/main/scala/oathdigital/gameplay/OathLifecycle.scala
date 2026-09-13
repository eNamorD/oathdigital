package oathdigital.gameplay

import oathdigital.gameplay._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.model._

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
      else if (current.walkerPending.nonEmpty)
        Left(InvalidEventOrder(
          "a walker procedure is pending; legacy actions are blocked"))
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

private[gameplay] object GameplayTransition {
  def apply(
      state: OathState,
      events: Vector[OathEvent],
      continue: OathContinue
  )(
      evolve: (OathState, OathEvent) => Either[OathViolation, OathState]
  ): Either[OathViolation, OathTransition] =
    events.foldLeft[Either[OathViolation, OathState]](Right(state)) {
      case (Right(current), event) => evolve(current, event)
      case (failure @ Left(_), _) => failure
    }.map(OathTransition(_, events, continue))
}
