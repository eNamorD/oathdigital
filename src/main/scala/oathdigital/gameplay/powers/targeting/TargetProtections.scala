package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The persistent rules that keep a player from being targeted by a Raid, a
  * Challenge or a Conspiracy, registered together. A power whose card is absent
  * from `catalog` is omitted.
  */
object TargetProtections {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    CircletOfCommand.forCatalog(catalog).toVector ++
      OakenFortress.forCatalog(catalog).toVector ++
      RottingFortress.forCatalog(catalog).toVector
}
