package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 3's slice 3c, actions on sites, banks
  * and banners, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[OtherActionPowers]]. A power that needs the catalog is omitted when
  * its card is absent.
  */
object WorldActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Storyteller, Firebrand, Levelers, MemoryOfHome,
      PlagueEngines, BanditPaymaster) ++
      MemoryOfNature.forCatalog(catalog).toVector ++
      BallotBox.forCatalog(catalog).toVector
