package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 3's slice 3c, actions on sites, banks
  * and banners, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[OtherActionPowers]]. Each names its denizen card, so all are
  * always present.
  */
object WorldActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Storyteller, Firebrand, Levelers, MemoryOfHome,
      PlagueEngines, BanditPaymaster) ++
      Vector(MemoryOfNature.forCatalog(catalog)) ++
      Vector(BallotBox.forCatalog(catalog)) ++
      Vector(DarkEnforcer.forCatalog(catalog)) ++
      Vector(TamingCharm.forCatalog(catalog)) ++
      Vector(GreatFeast.forCatalog(catalog))
