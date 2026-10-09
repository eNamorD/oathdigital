package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

/** The Narrow Pass's map. Cradle: Ancient City, Broken Peaks. Provinces:
  * the Pass, Buried Giant, Deep Woods. Hinterland: Desolate Shore, Dunes,
  * Fair Isle. Each site holds its printed bandits. No Table step lays out
  * regions, so the map is built here.
  */
object PassMap:
  val pass: SiteId = CatalogNames.site("Narrow Pass")
  val ancientCity: SiteId = CatalogNames.site("Ancient City")
  val brokenPeaks: SiteId = CatalogNames.site("Broken Peaks")
  val buriedGiant: SiteId = CatalogNames.site("Buried Giant")
  val deepWoods: SiteId = CatalogNames.site("Deep Woods")
  val desolateShore: SiteId = CatalogNames.site("Desolate Shore")
  val dunes: SiteId = CatalogNames.site("Dunes")
  val fairIsle: SiteId = CatalogNames.site("Fair Isle")

  val map: MapState =
    val cradle = Vector(ancientCity, brokenPeaks)
    val provinces = Vector(pass, buriedGiant, deepWoods)
    val hinterland = Vector(desolateShore, dunes, fairIsle)
    val states = (cradle ++ provinces ++ hinterland).map { id =>
      val definition = catalog.sites.find(_.id == id).get
      id -> SiteState(
        if definition.capacity == 0 then SiteForces.Empty
        else SiteForces.Occupied(ForceKind.Bandit, definition.capacity),
        Vector.empty, Vector.empty, definition.startingResources)
    }.toMap
    MapState(cradle, provinces, hinterland, states)

  /** `ready` on `map`, with `player`'s pawn at `site`. */
  def withPawnAt(ready: ReadyGame, player: PlayerId, site: SiteId,
      on: MapState = map): ReadyGame =
    Table.from(ready.updateCurrent(_.copy(map = on))).pawn(player, at = site)
      .ready
