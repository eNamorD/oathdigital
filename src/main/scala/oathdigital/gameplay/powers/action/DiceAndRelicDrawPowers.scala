package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of slice 1b, registered by [[oathdigital.gameplay.powers.PhasePowerCatalog]]
  * through this one object so that later slices add their own group without
  * editing the same lines.
  */
object DiceAndRelicDrawPowers {
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](GamblingHall, BoneDice, MurkyFountain)
}
