package oathdigital.model

class CampaignWindowsSuite extends munit.FunSuite {
  test("the Campaign windows have stable keys and belong to the Campaign action") {
    Vector(
      PowerWindow.CampaignCost -> "campaign.cost",
      PowerWindow.CampaignKindSelection -> "campaign.kind-selection",
      PowerWindow.CampaignDefenderSelection -> "campaign.defender-selection",
      PowerWindow.CampaignTargetSelection -> "campaign.target-selection",
      PowerWindow.CampaignForceSelection -> "campaign.force-selection",
      PowerWindow.CampaignGatherPools -> "campaign.gather-pools",
      PowerWindow.CampaignAttackRoll -> "campaign.attack-roll",
      PowerWindow.CampaignAttackResult -> "campaign.attack-result",
      PowerWindow.CampaignSacrificeSelection -> "campaign.sacrifice-selection",
      PowerWindow.CampaignDefenseRoll -> "campaign.defense-roll",
      PowerWindow.CampaignDefenseResult -> "campaign.defense-result",
      PowerWindow.CampaignLosses -> "campaign.losses",
      PowerWindow.CampaignPlacement -> "campaign.placement",
      PowerWindow.CampaignRaidTransfer -> "campaign.raid-transfer",
      PowerWindow.CampaignRaidRelocation -> "campaign.raid-relocation",
      PowerWindow.CampaignActionEligibility -> "campaign.action-eligibility",
      PowerWindow.CampaignModifierSelection -> "campaign.modifier-selection",
      PowerWindow.CampaignBeforeTargets -> "campaign.before-targets",
      PowerWindow.CampaignAttackerBattlePlans -> "campaign.attacker-battle-plans",
      PowerWindow.CampaignDefenderBattlePlans -> "campaign.defender-battle-plans",
      PowerWindow.CampaignAfterOutcome -> "campaign.after-outcome").foreach {
      case (window, key) =>
        assertEquals(window.key, key)
        assertEquals(window.associatedMajorAction, Some(MajorActionType.Campaign))
    }
  }

  test("every Campaign window key is distinct") {
    val keys = Vector(PowerWindow.CampaignCost, PowerWindow.CampaignKindSelection,
      PowerWindow.CampaignDefenderSelection, PowerWindow.CampaignTargetSelection,
      PowerWindow.CampaignForceSelection, PowerWindow.CampaignGatherPools,
      PowerWindow.CampaignAttackRoll, PowerWindow.CampaignAttackResult,
      PowerWindow.CampaignSacrificeSelection, PowerWindow.CampaignDefenseRoll,
      PowerWindow.CampaignDefenseResult, PowerWindow.CampaignLosses,
      PowerWindow.CampaignPlacement, PowerWindow.CampaignRaidTransfer,
      PowerWindow.CampaignRaidRelocation).map(_.key)
    assertEquals(keys.distinct.size, keys.size)
  }
}
