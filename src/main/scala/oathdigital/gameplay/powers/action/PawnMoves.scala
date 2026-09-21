package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Reads and writes shared by the movement relics (Whistle, Brass Horse,
  * Magic Carpet). A pawn relocation that is not Travel is a plain `Move`, and
  * nothing here runs a Travel window.
  */
object PawnMoves {
  def pawnSite(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, SiteId] =
    PowerAccess.pawnSite(ready, player).toRight(OathViolation.InvalidEventOrder(
      s"${player.value} has no pawn on the map"))

  /** Other players whose pawn is at a site other than `player`'s. */
  def atOtherSites(ready: ReadyGame, player: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, player).toVector.flatMap(here =>
      ready.game.current.players.collect {
        case other if other.player != player &&
            other.pawnSite.exists(_ != here) => other.player
      })
}
