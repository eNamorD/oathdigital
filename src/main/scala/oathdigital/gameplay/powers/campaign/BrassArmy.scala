package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model._

object BrassArmyCard extends Relic(RelicId("R25"), "Brass Army", value = 77, defense = 3):
  val campaign = PrintedPower(PowerId("relic.brass-army.campaign"),
    persistent = false, cost = Cost(secret = 1),
    text = "+4 [attack-die]")
  val restriction = PrintedPower(PowerId("relic.brass-army.restriction"),
    persistent = true, cost = Cost.free,
    text = "Whenever your pawn moves _(travel or place)_, bury this or " +
      "flip a secret you have facedown.")
  val powers: Vector[PrintedPower] = Vector(campaign, restriction)

/** Brass Army (relic R25), an attacker's battle plan: "[secret] +4 attack dice."
  *
  * The relic must be faceup in the attacker's play area. The secret is placed
  * onto the relic, which may already hold resources: a battle plan pays into an
  * occupied card. The dice join the attack pool but not the force, so no loss,
  * sacrifice or placement limit changes. The relic's other power, the pawn-move
  * restriction, is not part of this plan.
  */
case object BrassArmy extends BattlePlan:
  val relicId: RelicId = BrassArmyCard.id
  val id: PowerId = BrassArmyCard.campaign.id
  val Dice: Int = 4

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Brass Army: add 4 attack dice", Vector(CampaignPlanCost.Secret(1)),
      Vector(CampaignPlanEffect.AddAttackDice(BrassArmy.Dice))))
