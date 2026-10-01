package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 4 and catalog batch 3's slice
  * 3b, actions on others, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[SelfActionPowers]]. A power that needs the catalog is omitted when
  * its card is absent.
  */
object OtherActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines,
      BookOfRecords, BarbedNet, QuickExit, DreamThief, WhisperingLeaves,
      Enchantress) ++
      SecondChance.forCatalog(catalog).toVector ++
      ArmedMob.forCatalog(catalog).toVector ++
      HonorGuard.forCatalog(catalog).toVector
