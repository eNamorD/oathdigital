package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._
import oathdigital.model.{MajorActionType, PowerWindow}

object CampaignPowers {
  private val modifier = Some(MajorActionType.Campaign)
  object VowOfPeace extends ReviewedPower("denizen.vow-of-peace", modifier,
    Vector(ReviewedHandler.automatic(PowerWindow.CampaignBeforeTargets,
      implemented = true)))
  object Outriders extends ReviewedPower("denizen.outriders", modifier,
    Vector(ReviewedHandler.selected(PowerWindow.CampaignAttackerBattlePlans,
      implemented = true)))
  object BrassArmyCampaign extends ReviewedPower("relic.brass-army.campaign", modifier,
    Vector(ReviewedHandler.selected(PowerWindow.CampaignAttackerBattlePlans,
      implemented = true)))
  object Watchdog extends ReviewedPower("denizen.watchdog", modifier,
    Vector(ReviewedHandler.selected(PowerWindow.CampaignDefenderBattlePlans,
      implemented = true)))
  object BagOfSiegeworks extends ReviewedPower("relic.bag-of-siegeworks", modifier,
    Vector(ReviewedHandler.selected(PowerWindow.CampaignAttackerBattlePlans)))
  val powers: Vector[Power] = Vector(VowOfPeace, Outriders, BrassArmyCampaign,
    Watchdog, BagOfSiegeworks)
}
