package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.powers.action.Assassin
import oathdigital.gameplay.powers.rest.{Insomnia, SilverTongue}
import oathdigital.model._

/** How many advisers a player may hold, for a power that adds an adviser
  * outside card play (Horned Mask).
  *
  * Card play gets its limit from [[oathdigital.gameplay.actions.CardPlayRules]],
  * which the [[oathdigital.gameplay.powers.rest.HolderAdviserLimit]] of Silver
  * Tongue, of Insomnia and of Assassin narrows. This reads the same facts as a read of
  * state, so the limit is defined once: the default is
  * `CardPlayRules.DefaultAdviserLimit`, and the lowest limit a held card sets
  * wins.
  */
object AdviserLimit:
  val Default: Int = CardPlayRules.DefaultAdviserLimit

  def of(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Int =
    Vector(SilverTongue.forCatalog(catalog).limitFor(ready, player),
      Insomnia.limitFor(ready, player),
      Assassin.forCatalog(catalog).limitFor(ready, player))
      .flatten.minOption.getOrElse(Default)
