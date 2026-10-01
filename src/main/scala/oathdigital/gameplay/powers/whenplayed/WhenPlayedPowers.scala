package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The When Played powers of catalog batch 3, which `WalkerPowerCatalog`
  * wires in. A power whose card is absent from `catalog` is left out. */
object WhenPlayedPowers:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector[ContributingPower]() ++
      ThreateningRoar.forCatalog(catalog).toVector ++
      Riots.forCatalog(catalog).toVector
