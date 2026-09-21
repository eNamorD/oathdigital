package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower

/** The phase powers of slice 1d, registered by
  * [[oathdigital.gameplay.powers.PhasePowerCatalog]] through this one object,
  * like [[DiceAndRelicDrawPowers]] and [[TargetPowers]].
  */
object MovementPowers {
  def forCatalog(catalog: ExecutableCatalog): Vector[PhasePower] =
    Vector[PhasePower](Whistle, MagicCarpet, new BrassHorse(catalog))
}
