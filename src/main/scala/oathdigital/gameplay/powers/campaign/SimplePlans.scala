package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that change the dice, or pay after the Campaign, and need
  * nothing beyond the plan window: Mercenaries, Wrestlers, Fearsome Shield, the
  * two faces of the Rampart, Battle Honors, Longbows, Black Sword, Fire
  * Talkers, Nature Worship, Cracked Sage, Village Constable, Banner Breakers,
  * Cracking Ground, Walled Garden, Bandit Standard, Extra Provisions,
  * Encirclement, Disgraced Captain, Battle Axes, Field Promotion, Tribute
  * Spoils and Military Parade, registered together. Each names its card, so
  * all are always present.
  */
object SimplePlans:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Mercenaries.forCatalog(catalog)) ++
      Vector(Wrestlers) ++
      Vector(FearsomeShield) ++
      Vector(ToweringRampart) ++
      Vector(CrackedRampart) ++
      Vector(BattleHonors) ++
      Vector(Longbows) ++
      Vector(BlackSword) ++
      Vector(FireTalkers) ++
      Vector(NatureWorship.forCatalog(catalog)) ++
      Vector(CrackedSage.forCatalog(catalog)) ++
      Vector(VillageConstable) ++
      Vector(BannerBreakers) ++
      Vector(CrackingGround) ++
      Vector(WalledGarden.forCatalog(catalog)) ++
      Vector(BanditStandard) ++
      Vector(ExtraProvisions) ++
      Vector(Encirclement) ++
      Vector(DisgracedCaptain.forCatalog(catalog)) ++
      Vector(BattleAxes.forCatalog(catalog)) ++
      Vector(FieldPromotion) ++
      Vector(TributeSpoils.forCatalog(catalog)) ++
      Vector(MilitaryParade.forCatalog(catalog))
