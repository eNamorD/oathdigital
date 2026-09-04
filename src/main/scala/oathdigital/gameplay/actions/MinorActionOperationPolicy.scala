package oathdigital.gameplay.actions

import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.operations._
import oathdigital.model._

private[gameplay] object MinorActionOperationPolicy extends OperationPolicy {
  override def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Either[OperationError, Unit] = Either.cond(
    permits(ready, operation),
    (),
    OperationError.RestrictedOperation(
      "minor-action semantic root is not permitted")
  )

  private def permits(ready: ReadyGame, operation: CoreOperation): Boolean =
    operation match {
      case Reveal(relic: RelicId, Location.PlayArea(player)) =>
        activePlayerAt(ready, player, None).exists(_.relics.exists(value =>
          value.id == relic && value.orientation == Orientation.FaceDown))
      case Peek(viewer, relic: RelicId, Location.Site(site)) =>
        activePlayerAt(ready, viewer, Some(site)).isDefined &&
          ready.game.current.map.sites.get(site).exists(
            _.relics.exists(_.id == relic))
      case Move(
          Piece.Warbands(kind, _),
          PositionedLocation(Location.PlayArea(player), StackPosition.Unspecified),
          PositionedLocation(Location.Site(site), StackPosition.Unspecified),
          None
      ) => validWarbandActor(ready, player, site, kind)
      case Move(
          Piece.Warbands(kind, _),
          PositionedLocation(Location.Site(site), StackPosition.Unspecified),
          PositionedLocation(Location.PlayArea(player), StackPosition.Unspecified),
          None
      ) => validWarbandActor(ready, player, site, kind)
      case _ => false
    }

  private def validWarbandActor(
      ready: ReadyGame,
      player: PlayerId,
      site: SiteId,
      kind: ForceKind
  ): Boolean = activePlayerAt(ready, player, Some(site)).exists(actor =>
    kind == ForceKind.Exile(actor.lineage))

  private def activePlayerAt(
      ready: ReadyGame,
      player: PlayerId,
      site: Option[SiteId]
  ): Option[PlayerState] = ready.game.current.players.find(actor =>
    actor.player == player &&
      ready.game.current.turn.activePlayer == player &&
      site.forall(actor.pawnSite.contains))
}
