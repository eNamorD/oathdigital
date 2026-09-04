package oathdigital.gameplay.phases

import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.operations._
import oathdigital.model._

private[gameplay] object WakeOperationPolicy extends OperationPolicy {
  override def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, Unit] = Either.cond(
    operation match {
      case Take(
          Piece.Favor(1),
          player,
          Location.Site(site),
          Location.PlayArea(owner),
          StackPosition.Unspecified
      ) => validActor(ready, player, owner, site)
      case Take(
          Piece.Secrets(1),
          player,
          Location.Site(site),
          Location.PlayArea(owner),
          StackPosition.Unspecified
      ) => validActor(ready, player, owner, site)
      case _ => false
    },
    (),
    OperationError.RestrictedOperation(
      "Take Wealth semantic root is not permitted")
  )

  private def validActor(
      ready: ReadyGame,
      player: PlayerId,
      owner: PlayerId,
      site: SiteId
  ): Boolean = player == owner &&
    ready.game.current.turn.activePlayer == player &&
    ready.game.current.map.inPlay.contains(site) &&
    ready.game.current.players.find(_.player == player)
      .exists(_.pawnSite.contains(site))
}
