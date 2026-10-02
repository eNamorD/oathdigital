package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.gameplay.powers.action.{DiceAndRelicDrawPowers, Elders, MagicWaterskin, MovementPowers, OtherActionPowers, SelfActionPowers, TargetPowers, WaysideInn,
  WorldActionPowers}
import oathdigital.gameplay.powers.banner.BannerFacePowers
import oathdigital.gameplay.powers.cardplay.VowOfObedience
import oathdigital.gameplay.powers.rest.{Insomnia, SilverTongue}
import oathdigital.gameplay.powers.wake.{Hunger, MarbleFountains,
  Quartermaster, RiverSitePower}

/** The production phase powers, beside [[WalkerPowerCatalog]]. A relic, edifice
  * or site power whose card is absent from `catalog` is omitted; a denizen
  * power names its card and is always present.
  */
object PhasePowerCatalog:
  def default(catalog: ExecutableCatalog): PhasePowers =
    PhasePowers(Vector(SilverTongue.forCatalog(catalog)) ++
      Vector(Insomnia) ++
      Vector(VowOfObedience) ++
      Vector[PhasePower](WaysideInn, Elders, MagicWaterskin, MarbleFountains,
        Quartermaster) ++
      DiceAndRelicDrawPowers.powers ++
      TargetPowers.forCatalog(catalog) ++
      MovementPowers.forCatalog(catalog) ++
      SelfActionPowers.forCatalog(catalog) ++
      OtherActionPowers.forCatalog(catalog) ++
      WorldActionPowers.forCatalog(catalog) ++
      BannerFacePowers.phasePowers ++
      Vector(Hunger.forCatalog(catalog)) ++
      RiverSitePower.forCatalog(catalog))
