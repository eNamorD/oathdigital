package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that change the dice, or pay after the Campaign, and need
  * nothing beyond the plan window: Mercenaries, Wrestlers, Fearsome Shield, the
  * two faces of the Rampart, Battle Honors, Longbows, Black Sword, Fire
  * Talkers, Nature Worship, Cracked Sage, Village Constable and Banner Breakers,
  * registered together. A plan whose card is absent from `catalog` is omitted.
  */
object SimplePlans:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Mercenaries.forCatalog(catalog).toVector ++
      Wrestlers.forCatalog(catalog).toVector ++
      FearsomeShield.forCatalog(catalog).toVector ++
      ToweringRampart.forCatalog(catalog).toVector ++
      CrackedRampart.forCatalog(catalog).toVector ++
      BattleHonors.forCatalog(catalog).toVector ++
      Longbows.forCatalog(catalog).toVector ++
      BlackSword.forCatalog(catalog).toVector ++
      FireTalkers.forCatalog(catalog).toVector ++
      NatureWorship.forCatalog(catalog).toVector ++
      CrackedSage.forCatalog(catalog).toVector ++
      VillageConstable.forCatalog(catalog).toVector ++
      BannerBreakers.forCatalog(catalog).toVector
