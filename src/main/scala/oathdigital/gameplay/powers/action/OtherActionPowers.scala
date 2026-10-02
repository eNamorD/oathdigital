package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 4 and catalog batch 3's slice
  * 3b, actions on others, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[SelfActionPowers]]. Each names its denizen card, so all are
  * always present.
  */
object OtherActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines,
      BookOfRecords, BarbedNet, QuickExit, DreamThief, WhisperingLeaves,
      Enchantress, AmberFlame) ++
      Vector(SecondChance.forCatalog(catalog)) ++
      Vector(ArmedMob.forCatalog(catalog)) ++
      Vector(HonorGuard.forCatalog(catalog))
