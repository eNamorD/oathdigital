package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The Travel modifiers and rules that are not terrain, registered together.
  * Each names its card, so all are always present. Terrain is
  * [[TravelSitePowers]].
  */
object TravelModifiers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Tents.forCatalog(catalog)) ++
      Vector(RoyalStables.forCatalog(catalog)) ++
      Vector(ForestPaths.forCatalog(catalog)) ++
      Vector(DragonskinDrum.forCatalog(catalog)) ++
      Vector(TollRoads.forCatalog(catalog)) ++
      Vector(GraspingVines.forCatalog(catalog))
