package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{AdviserFace, CardPlayRules}
import oathdigital.gameplay.powers.action.{Assassin, PiedPiper}
import oathdigital.gameplay.powers.cardplay.{FamilyWagon, LostTapestry}
import oathdigital.gameplay.powers.rest.{Insomnia, SilverTongue}
import oathdigital.model._

/** How many advisers a player may hold, for a power that adds an adviser
  * outside card play (Horned Mask).
  *
  * Card play gets its limit from [[oathdigital.gameplay.actions.CardPlayRules]],
  * which the [[oathdigital.gameplay.powers.rest.HolderAdviserLimit]] of Silver
  * Tongue, of Insomnia and of Assassin narrows. This reads the same facts as
  * a read of state, so the limit is defined once: the default is
  * `CardPlayRules.DefaultAdviserLimit`, and the lowest limit a held card sets
  * wins. A faceup Family Wagon gives its holder no limit
  * (`CardPlayRules.Unbounded`) before those apply. `ignored` and
  * `towardLimit` read the facts by which card play's rules leave an adviser
  * out of the count (Lost Tapestry, Pied Piper).
  */
object AdviserLimit:
  val Default: Int = CardPlayRules.DefaultAdviserLimit

  def of(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Int =
    Vector(SilverTongue.forCatalog(catalog).limitFor(ready, player),
      Insomnia.limitFor(ready, player),
      Assassin.forCatalog(catalog).limitFor(ready, player))
      .flatten.minOption.getOrElse(
        if FamilyWagon.lifts(ready, player) then CardPlayRules.Unbounded
        else Default)

  /** The advisers `player` holds that do not count toward the limit, as card
    * play's rules mark them: a faceup Pied Piper, and a Lost Tapestry
    * holder's facedown Visions. */
  def ignored(ready: ReadyGame, player: PlayerId): Vector[AdviserFace] =
    PiedPiper.ignored(ready, player) ++ LostTapestry.ignored(ready, player)

  /** How many of `player`'s advisers count toward the limit. Marriage counts
    * once, as any adviser does. */
  def towardLimit(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.advisers.size) - ignored(ready, player).size

  /** How many adviser slots `player`'s board draws: the limit and one more
    * per adviser that does not count, or, with no limit, the advisers held
    * and one empty slot. */
  def drawn(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId): Int =
    val limit = of(catalog, ready, player)
    if limit == CardPlayRules.Unbounded then
      ready.game.current.players.find(_.player == player)
        .fold(0)(_.advisers.size) + 1
    else limit + ignored(ready, player).size
