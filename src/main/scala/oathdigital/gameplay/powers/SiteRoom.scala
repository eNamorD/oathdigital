package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Room for a card at a site. Operations never check a site's capacity, so
  * a power that moves a card to a site offers only sites with room (catalog
  * batch 4 rulings, "Site capacity"). A site's card row has no gaps, so a
  * card moved there takes its first empty slot.
  */
object SiteRoom:
  /** The empty slots of `site`'s card row, in slot order: none when the site
    * is full or not in play. */
  def emptySlots(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId)
      : Vector[Int] =
    val map = ready.game.current.map
    (for
      state <- map.sites.get(site)
      if map.inPlay.contains(site)
      capacity <- catalog.site(site).map(_.capacity)
    yield (state.denizens.size until capacity).toVector)
      .getOrElse(Vector.empty)

  def hasRoom(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId)
      : Boolean = emptySlots(catalog, ready, site).nonEmpty

  /** The sites in play with room, in map order. */
  def open(catalog: ExecutableCatalog, ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(hasRoom(catalog, ready, _))
