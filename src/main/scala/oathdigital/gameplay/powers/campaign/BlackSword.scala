package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model._

object BlackSwordCard extends Relic(RelicId("R35"), "Black Sword", value = 73, defense = 2):
  val power = PrintedPower(PowerId("relic.black-sword"),
    persistent = false, cost = Cost(secretBurnt = 2),
    text = "+5 [attack-die]")
  val powers: Vector[PrintedPower] = Vector(power)

/** Black Sword (relic R35), an attacker's battle plan: "[secret-burnt]
  * [secret-burnt] +5 [attack-die]".
  *
  * The relic must be faceup in the attacker's play area. Two faceup secrets
  * are burnt to the shared bank, and nothing is placed on the relic. An
  * attacker with fewer than two faceup secrets cannot pay, and the plan is
  * not offered.
  */
case object BlackSword extends BattlePlan:
  val relicId: RelicId = BlackSwordCard.id
  val id: PowerId = BlackSwordCard.power.id
  val Dice: Int = 5

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Black Sword: burn 2 secrets for 5 attack dice",
      Vector(CampaignPlanCost.SecretBurnt(2)),
      Vector(CampaignPlanEffect.AddAttackDice(BlackSword.Dice))))
