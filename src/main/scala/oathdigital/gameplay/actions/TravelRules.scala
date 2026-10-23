package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathViolation._
import oathdigital.model.{OathViolation, ReadyGame, Region, SiteId}

/** Travel's printed rules: the region-to-region Supply table, and nothing
  * else.
  *
  * The table returns the printed base of a route; everything that shapes it
  * lives elsewhere. Terrain is stated by the site powers themselves
  * ([[oathdigital.gameplay.powers.travel.TravelSitePowers]]) as `Transform`s
  * over the pay node, folded by the walker at `PowerWindow.TravelCost`. The
  * destinations a client is offered come from
  * [[oathdigital.gameplay.actions.travel.TravelProcedure]], which prices every
  * route through this table inside the tree the command walks, so what a
  * client is offered and what the command accepts are one expression. Role
  * and Foundation faces gate nothing here: the procedure previews every
  * destination and says why none can be reached.
  */
object TravelRules:

  /** The printed Supply cost of moving between the regions holding `source`
    * and `destination`, before any power shapes it.
    *
    * Both endpoints must be real, in-play sites and must differ: a route to
    * the site the pawn already stands on is `SameTravelSite`, not a free move.
    */
  def cost(catalog: ExecutableCatalog, ready: ReadyGame, source: SiteId,
      destination: SiteId): Either[OathViolation, Int] =
    val map = ready.game.current.map
    for
      from <- map.regionOf(source).toRight(SiteNotInPlay(source))
      to <- map.regionOf(destination).toRight(SiteNotInPlay(destination))
      _ <- Either.cond(source != destination, (), SameTravelSite(source))
      _ <- catalog.site(source).toRight(SiteNotInPlay(source))
      _ <- catalog.site(destination)
        .toRight(SiteNotInPlay(destination))
    yield (from, to) match
      case (Region.Cradle, Region.Cradle) => 1
      case (Region.Cradle, Region.Provinces) => 2
      case (Region.Cradle, Region.Hinterland) => 4
      case (Region.Provinces, _) => 2
      case (Region.Hinterland, Region.Cradle) => 4
      case (Region.Hinterland, Region.Provinces) => 2
      case (Region.Hinterland, Region.Hinterland) => 3
