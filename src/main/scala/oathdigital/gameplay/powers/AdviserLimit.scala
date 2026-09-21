package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.model._

/** How many advisers a player may hold, for a power that adds an adviser
  * outside card play (Horned Mask).
  *
  * Card play gets its limit from [[PlacementRules]], which Silver Tongue's
  * `SearchPlayAdviser` transform narrows. This reads the same two facts as a
  * read of state, so the limit is defined once: the default is
  * `PlacementRules.DefaultAdviserLimit` and the only power that lowers it is
  * Silver Tongue, through `SilverTongue.limitFor`.
  */
object AdviserLimit {
  val Default: Int = PlacementRules.DefaultAdviserLimit

  def of(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Int =
    SilverTongue.forCatalog(catalog).flatMap(_.limitFor(ready, player))
      .getOrElse(Default)
}
