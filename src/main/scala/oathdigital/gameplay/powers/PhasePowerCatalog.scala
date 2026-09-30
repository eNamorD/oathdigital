package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{PhasePower, PhasePowers}
import oathdigital.gameplay.powers.action.{DiceAndRelicDrawPowers, Elders, MagicWaterskin, MovementPowers, OtherActionPowers, SelfActionPowers, TargetPowers, WaysideInn}
import oathdigital.gameplay.powers.banner.BannerFacePowers
import oathdigital.gameplay.powers.cardplay.VowOfObedience
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.powers.wake.{Hunger, MarbleFountains,
  RiverSitePower}

/** The production phase powers, beside [[WalkerPowerCatalog]]. A power whose
  * card is absent from `catalog` is omitted.
  */
object PhasePowerCatalog:
  def default(catalog: ExecutableCatalog): PhasePowers =
    PhasePowers(SilverTongue.forCatalog(catalog).toVector ++
      VowOfObedience.forCatalog(catalog).toVector ++
      Vector[PhasePower](WaysideInn, Elders, MagicWaterskin, MarbleFountains) ++
      DiceAndRelicDrawPowers.powers ++
      TargetPowers.forCatalog(catalog) ++
      MovementPowers.forCatalog(catalog) ++
      SelfActionPowers.forCatalog(catalog) ++
      OtherActionPowers.powers ++
      BannerFacePowers.phasePowers ++
      Hunger.forCatalog(catalog).toVector ++
      RiverSitePower.forCatalog(catalog))
