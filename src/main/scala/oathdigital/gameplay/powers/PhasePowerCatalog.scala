package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers

/** The production phase powers, beside [[WalkerPowerCatalog]]. A power whose
  * card is absent from `catalog` is omitted.
  */
object PhasePowerCatalog {
  def default(catalog: ExecutableCatalog): PhasePowers = PhasePowers(Vector.empty)
}
