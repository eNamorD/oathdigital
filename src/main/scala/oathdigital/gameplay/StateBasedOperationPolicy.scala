package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._

private[gameplay] object StateBasedOperationPolicy extends OperationPolicy {
  override def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, Unit] = Either.cond(
    operation match {
      case Move(
          Piece.Warbands(ForceKind.Bandit, _),
          PositionedLocation(
            Location.WarbandBank(ForceKind.Bandit),
            StackPosition.Unspecified),
          PositionedLocation(Location.Site(site), StackPosition.Unspecified),
          None
      ) => ready.game.current.map.inPlay.contains(site) &&
        ready.game.current.map.sites.get(site).exists(
          _.forces == SiteForces.Empty)
      case _ => false
    },
    (),
    OperationError.RestrictedOperation(
      "state-based semantic root is not permitted")
  )
}
