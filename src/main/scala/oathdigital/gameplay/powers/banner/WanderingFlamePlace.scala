package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.action.{PaidAction, PawnMoves}
import oathdigital.model._

/** Wandering Flame, place a secret (the Banner of the Darkest Secret,
  * Wandering Flame face), ACTION: move one secret from your board onto the
  * site your pawn is at. It costs nothing and has no once-per-turn limit.
  *
  * A secret placed on a site rests faceup, so only a faceup secret can go. The
  * power is usable only while the holder has one (a facedown secret is never
  * flipped or moved). The engine finds the banner as the source, for its holder
  * and on the Wandering Flame face only.
  */
case object WanderingFlamePlace extends PaidAction(
    "banner.darkest-secret.wandering-flame.place", Cost.free) {
  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = faceUpSecrets(ready, player) > 0

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(BuildOps((state, _) => place(state, player)))

  private def faceUpSecrets(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.faceUpSecrets)

  private def place(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    PawnMoves.pawnSite(ready, player).map { here =>
      if (faceUpSecrets(ready, player) == 0) Vector.empty
      else Vector(Move(Piece.Secrets(1),
        PositionedLocation(Location.PlayArea(player)),
        PositionedLocation(Location.Site(here))))
    }
}
