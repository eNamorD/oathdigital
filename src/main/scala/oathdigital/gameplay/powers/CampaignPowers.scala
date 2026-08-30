package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver._

object CampaignPowers {
  val registrations: Vector[RegisteredPower] = Vector(
    PowerRegistration.automatic("denizen.vow-of-peace",
      Some(MajorActionType.Campaign), Vector(PowerWindow.CampaignBeforeTargets),
      implemented = true),
    PowerRegistration.selected("denizen.outriders", MajorActionType.Campaign,
      PowerWindow.CampaignAttackerBattlePlans, implemented = true),
    PowerRegistration.selected("relic.brass-army.campaign", MajorActionType.Campaign,
      PowerWindow.CampaignAttackerBattlePlans, implemented = true),
    PowerRegistration.selected("denizen.watchdog", MajorActionType.Campaign,
      PowerWindow.CampaignDefenderBattlePlans, implemented = true),
    PowerRegistration.selected("relic.bag-of-siegeworks", MajorActionType.Campaign,
      PowerWindow.CampaignAttackerBattlePlans))
}
