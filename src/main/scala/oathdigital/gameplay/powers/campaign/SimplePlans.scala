package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that change the dice, or pay after the Campaign, and need
  * nothing beyond the plan window: Mercenaries, Wrestlers, Fearsome Shield, the
  * two faces of the Rampart, Battle Honors, Longbows, Black Sword, Fire
  * Talkers, Nature Worship, Cracked Sage, Village Constable, Banner Breakers,
  * Cracking Ground, Walled Garden, Bandit Standard, Extra Provisions,
  * Encirclement, Disgraced Captain, Battle Axes, Field Promotion, Tribute
  * Spoils and Military Parade, registered together. A relic or edifice plan
  * whose card is absent from `catalog` is omitted; a denizen plan names its
  * card and is always present.
  */
object SimplePlans:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Mercenaries.forCatalog(catalog)) ++
      Vector(Wrestlers) ++
      FearsomeShield.forCatalog(catalog).toVector ++
      ToweringRampart.forCatalog(catalog).toVector ++
      CrackedRampart.forCatalog(catalog).toVector ++
      Vector(BattleHonors) ++
      Vector(Longbows) ++
      BlackSword.forCatalog(catalog).toVector ++
      Vector(FireTalkers) ++
      Vector(NatureWorship.forCatalog(catalog)) ++
      Vector(CrackedSage.forCatalog(catalog)) ++
      Vector(VillageConstable) ++
      Vector(BannerBreakers) ++
      Vector(CrackingGround) ++
      Vector(WalledGarden.forCatalog(catalog)) ++
      BanditStandard.forCatalog(catalog).toVector ++
      Vector(ExtraProvisions) ++
      Vector(Encirclement) ++
      Vector(DisgracedCaptain.forCatalog(catalog)) ++
      Vector(BattleAxes.forCatalog(catalog)) ++
      Vector(FieldPromotion) ++
      Vector(TributeSpoils.forCatalog(catalog)) ++
      Vector(MilitaryParade.forCatalog(catalog))
