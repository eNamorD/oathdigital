package oathdigital.gameplay.actions

import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.operations._

private[gameplay] object TravelOperationPolicy extends OperationPolicy {
  override def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, Unit] = Either.cond(
    operation match {
      case Move(
          Piece.Pawn(player),
          PositionedLocation(Location.Site(source), StackPosition.Unspecified),
          PositionedLocation(Location.Site(destination), StackPosition.Unspecified),
          None
      ) => ready.game.current.players.find(_.player == player).exists(actor =>
        ready.game.current.turn.activePlayer == player &&
          actor.pawnSite.contains(source) && source != destination &&
          ready.game.current.map.inPlay.contains(source) &&
          ready.game.current.map.inPlay.contains(destination))
      case AdjustSupply(player, amount) if amount < 0 =>
        ready.game.current.turn.activePlayer == player
      case _ => false
    },
    (),
    OperationError.RestrictedOperation(
      "Travel semantic root is not permitted")
  )
}
