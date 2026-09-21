package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.gameplay.powers.action.{DiceAndRelicDrawPowers, Elders, MagicWaterskin, WaysideInn}
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.powers.wake.MarbleFountains

/** The production phase powers, beside [[WalkerPowerCatalog]]. A power whose
  * card is absent from `catalog` is omitted.
  */
object PhasePowerCatalog {
  def default(catalog: ExecutableCatalog): PhasePowers =
    PhasePowers(SilverTongue.forCatalog(catalog).toVector ++
      Vector[PhasePower](WaysideInn, Elders, MagicWaterskin, MarbleFountains) ++
      DiceAndRelicDrawPowers.forCatalog(catalog))
}
