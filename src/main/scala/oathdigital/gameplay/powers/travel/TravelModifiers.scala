package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The Travel modifiers and rules that are not terrain, registered together. A
  * relic power whose card is absent from `catalog` is omitted; a denizen
  * power names its card and is always present. Terrain is
  * [[TravelSitePowers]].
  */
object TravelModifiers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Tents.forCatalog(catalog)) ++
      Vector(RoyalStables.forCatalog(catalog)) ++
      Vector(ForestPaths.forCatalog(catalog)) ++
      DragonskinDrum.forCatalog(catalog).toVector ++
      Vector(TollRoads.forCatalog(catalog)) ++
      Vector(GraspingVines.forCatalog(catalog))
