package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The Travel modifiers and rules that are not terrain, registered together. A
  * power whose card is absent from `catalog` is omitted. Terrain is
  * [[TravelSitePowers]].
  */
object TravelModifiers {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Tents.forCatalog(catalog).toVector ++
      ForestPaths.forCatalog(catalog).toVector ++
      DragonskinDrum.forCatalog(catalog).toVector ++
      TollRoads.forCatalog(catalog).toVector ++
      GraspingVines.forCatalog(catalog).toVector
}
