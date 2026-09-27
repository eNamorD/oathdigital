package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Staging for the River. The first game has one River in play, Ancient
  * City, where the actor's pawn stands. `withRivers` puts more in play.
  */
object RiverFixture:
  import PowerFixture._

  val ancientCity: SiteId = SiteId("site:ancient-city")
  val riverbank: SiteId = SiteId("site:riverbank")
  val tidalMarshes: SiteId = SiteId("site:tidal-marshes")

  /** Ancient City's River. */
  lazy val river: RiverSitePower = RiverSitePower.forCatalog(catalog)
    .find(_.site == ancientCity).get

  /** `rivers` take the places of the first in-play sites, in map order, that
    * are not a River and hold no pawn. Each keeps the replaced site's state,
    * so every card and piece stays in the game.
    */
  def withRivers(ready: ReadyGame, rivers: Vector[SiteId]): ReadyGame =
    val current = ready.game.current
    require(rivers.forall(!current.map.inPlay.contains(_)),
      s"already in play: $rivers")
    val pawns = current.players.flatMap(_.pawnSite).toSet
    val replaced = current.map.inPlay.filter(site =>
      !RiverSitePower.isRiver(catalog, site) && !pawns.contains(site))
      .take(rivers.size)
    val swaps = replaced.zip(rivers).toMap
    def swap(ids: Vector[SiteId]) = ids.map(id => swaps.getOrElse(id, id))
    ready.updateCurrent(c => c.copy(map = c.map.copy(
      cradle = swap(c.map.cradle), provinces = swap(c.map.provinces),
      hinterland = swap(c.map.hinterland),
      sites = swaps.foldLeft(c.map.sites) { case (sites, (from, to)) =>
        sites.removed(from).updated(to, sites(from)) })))

  def staged(rivers: Vector[SiteId] = Vector(riverbank, tidalMarshes),
      phase: Phase = Phase.Wake): ReadyGame =
    inPhase(withRivers(base, rivers), phase)
