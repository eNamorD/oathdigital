package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of catalog batch 2's slice 3 and catalog batch 3's slice
  * 3a, registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]]
  * through this one object, like [[DiceAndRelicDrawPowers]]. A power that
  * needs the catalog is omitted when its card is absent.
  */
object SelfActionPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Tutor, ShiftingMap, DemonTail, WizardSchool,
      SpiritSnare, ClayRattle, Scryer, OracularPig) ++
      Oracle.forCatalog(catalog).toVector ++
      Vector[PhasePower](BloodPact)
