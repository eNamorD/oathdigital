package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.model._

/** How many advisers a player may hold, for a power that adds an adviser
  * outside card play (Horned Mask).
  *
  * Card play gets its limit from `PlacementTree` and Silver Tongue's
  * `SearchPlayAdviser` transform, which is the only source of a limit other
  * than the default. This repeats the same rule as a read of state, so a power
  * outside card play can ask it: 3, or 2 for the player who holds a faceup
  * Silver Tongue. Card play itself does not read this, and slice 2's
  * `PlacementRules` may fold the two together.
  */
object AdviserLimit {
  val Default: Int = 3
  /** The limit Silver Tongue sets on its holder, in both orientations. */
  val SilverTongueHolder: Int = 2

  def of(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Int =
    if (holdsSilverTongue(catalog, ready, player)) SilverTongueHolder
    else Default

  private def holdsSilverTongue(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Boolean = SilverTongue.forCatalog(catalog).exists(
    tongue => ready.game.current.players.find(_.player == player).exists(
      _.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == tongue.cardId
        case _ => false
      }))
}
