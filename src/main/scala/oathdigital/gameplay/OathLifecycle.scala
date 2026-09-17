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
