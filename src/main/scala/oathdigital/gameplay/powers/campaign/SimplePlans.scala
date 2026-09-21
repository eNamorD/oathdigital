package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that change the dice, or pay after the Campaign, and need
  * nothing beyond the plan window: Mercenaries, Wrestlers, Fearsome Shield, the
  * two faces of the Rampart and Battle Honors, registered together. A plan whose
  * card is absent from `catalog` is omitted.
  */
object SimplePlans {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Mercenaries.forCatalog(catalog).toVector ++
      Wrestlers.forCatalog(catalog).toVector ++
      FearsomeShield.forCatalog(catalog).toVector ++
      ToweringRampart.forCatalog(catalog).toVector ++
      CrackedRampart.forCatalog(catalog).toVector ++
      BattleHonors.forCatalog(catalog).toVector
}
