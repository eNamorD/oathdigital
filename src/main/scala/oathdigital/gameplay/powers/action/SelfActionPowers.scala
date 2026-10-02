package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 3 and catalog batch 3's slice
  * 3a, registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]]
  * through this one object, like [[DiceAndRelicDrawPowers]]. Each names its denizen
  * card, so all are always present.
  */
object SelfActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Tutor, ShiftingMap, DemonTail, WizardSchool,
      SpiritSnare, ClayRattle, Scryer, OracularPig) ++
      Vector(Oracle.forCatalog(catalog)) ++
      Vector[PhasePower](BloodPact, ArcaneBrokers, Bog, RelicBreaker,
        TavernSongs, TinkersFair, SkeletonKey, Messenger) ++
      Vector(BedOfRoots.forCatalog(catalog))
