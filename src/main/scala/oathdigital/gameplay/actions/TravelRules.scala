package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathViolation._
import oathdigital.model.{OathViolation, ReadyGame, Region, SiteId}

/** Travel's printed rules: the region-to-region Supply table, and nothing
  * else.
  *
  * What this used to also do, and no longer does (batch-1 Task 5):
  *
  *  - **Terrain.** `cost` ended in a window fold that added Mountain and
  *    Island and replaced the whole cost on a coast route. Terrain is now
  *    stated by the site powers themselves as `Transform`s over the pay node
  *    ([[oathdigital.gameplay.powers.travel.TravelSitePowers]]), folded by the
  *    walker at `PowerWindow.TravelCost`. This table returns the PRINTED base
  *    a transform then shapes.
  *  - **Legal destinations.** `legalDestinations` computed costs a second way
  *    and filtered them against the actor's Supply. The candidate list is now
  *    `TravelProcedure.candidates`, which dry-runs the same declared tree the
  *    command walks, so what a client is offered and what the command accepts
  *    are one expression rather than two that agree by convention.
  *  - **Role and Foundation gates.** `validateSupportedState` refused a Travel
  *    in a non-exile game or beside an altered Foundation. Neither is a fact
  *    about the printed Travel action, and `OathViolation.UnsupportedTravelState`
  *    went with them.
  */
object TravelRules {

  /** The printed Supply cost of moving between the regions holding `source`
    * and `destination`, before any power shapes it.
    *
    * Both endpoints must be real, in-play sites and must differ: a route to
    * the site the pawn already stands on is `SameTravelSite`, not a free move.
    */
  def cost(catalog: ExecutableCatalog, ready: ReadyGame, source: SiteId,
      destination: SiteId): Either[OathViolation, Int] = {
    val map = ready.game.current.map
    for {
      from <- map.regionOf(source).toRight(SiteNotInPlay(source))
      to <- map.regionOf(destination).toRight(SiteNotInPlay(destination))
      _ <- Either.cond(source != destination, (), SameTravelSite(source))
      _ <- catalog.sites.find(_.id == source).toRight(SiteNotInPlay(source))
      _ <- catalog.sites.find(_.id == destination)
        .toRight(SiteNotInPlay(destination))
    } yield (from, to) match {
      case (Region.Cradle, Region.Cradle) => 1
      case (Region.Cradle, Region.Provinces) => 2
      case (Region.Cradle, Region.Hinterland) => 4
      case (Region.Provinces, _) => 2
      case (Region.Hinterland, Region.Cradle) => 4
      case (Region.Hinterland, Region.Provinces) => 2
      case (Region.Hinterland, Region.Hinterland) => 3
    }
  }
}
