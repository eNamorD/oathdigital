package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 4, catalog batch 3's slice 3b
  * and catalog batch 4's slices 2 and 3, actions on others, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[SelfActionPowers]]. Each names its denizen card, so all are
  * always present.
  */
object OtherActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](SpoiledSupplies, CharmingFriend, SiegeEngines,
      BookOfRecords, BarbedNet, QuickExit, DreamThief, WhisperingLeaves,
      Enchantress, AmberFlame, WitchsBargain, Inquisitor, PiedPiper) ++
      Vector(SecondChance.forCatalog(catalog)) ++
      Vector(ArmedMob.forCatalog(catalog)) ++
      Vector(HonorGuard.forCatalog(catalog)) ++
      Vector(Resettle.forCatalog(catalog)) ++
      Vector(RelicThief.forCatalog(catalog)) ++
      Vector(Assassin.forCatalog(catalog))
