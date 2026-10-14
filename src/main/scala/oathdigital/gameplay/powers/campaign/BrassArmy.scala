package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model._

object BrassArmyCard extends Relic(RelicId("R25"), "Brass Army", value = 77, defense = 3):
  val campaign = PrintedPower(PowerId("relic.brass-army.campaign"),
    persistent = false, cost = Cost(secret = 1),
    text = "±4 [attack-die]")
  val restriction = PrintedPower(PowerId("relic.brass-army.restriction"),
    persistent = true, cost = Cost.free,
    text = "Whenever your pawn moves _(travel or place)_, bury this or " +
      "flip a secret you have facedown.")
  val powers: Vector[PrintedPower] = Vector(campaign, restriction)

/** Brass Army (relic R25), a battle plan for either side: "[secret] ±4
  * attack dice." The component catalog prints "+4", a typo (catalog batch 8
  * rulings, slice 6).
  *
  * The relic must be faceup in its user's play area. The secret is placed
  * onto the relic, which may already hold resources: a battle plan pays into
  * an occupied card. An attacker adds four dice to the attack pool but not
  * to the force, so no loss, sacrifice or placement limit changes. A
  * defender removes four from it, and the pool never goes below zero. The
  * relic's other power, the pawn-move restriction, is `BrassArmyMoves`.
  */
case object BrassArmy extends BattlePlan:
  val relicId: RelicId = BrassArmyCard.id
  val id: PowerId = BrassArmyCard.campaign.id
  val Dice: Int = 4

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Brass Army", context.side, BrassArmy.Dice),
      Vector(CampaignPlanCost.Secret(1)),
      Vector(PlanDice.effect(context.side, BrassArmy.Dice))))
