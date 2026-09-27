package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** The "±" of a battle plan (catalog batch 2 rulings, "Sign"): the side fixes
  * the sign. An attacker adds attack dice; a defender removes them from the
  * attack pool, which never goes below zero. */
private[campaign] object PlanDice:
  def effect(side: CampaignPlanSide, dice: Int): CampaignPlanEffect = side match
    case CampaignPlanSide.Attacker => CampaignPlanEffect.AddAttackDice(dice)
    case CampaignPlanSide.Defender => CampaignPlanEffect.RemoveAttackDice(dice)

  /** The offer's label, "Longbows: add 1 attack die". */
  def label(card: String, side: CampaignPlanSide, dice: Int): String =
    val noun = if dice == 1 then "attack die" else "attack dice"
    side match
      case CampaignPlanSide.Attacker => s"$card: add $dice $noun"
      case CampaignPlanSide.Defender => s"$card: remove $dice $noun"
