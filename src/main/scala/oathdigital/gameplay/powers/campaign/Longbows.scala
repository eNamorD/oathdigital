package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object LongbowsCard extends Denizen(DenizenId("4"), "Longbows", Suit.Order):
  val power = PrintedPower(PowerId("denizen.longbows"),
    persistent = false, cost = Cost.free,
    text = "± [attack-die]")
  val powers: Vector[PrintedPower] = Vector(power)

/** Longbows (card 4), a battle plan for either side: "± [attack-die]".
  *
  * It is free. An attacker adds one attack die; a defender removes one. A
  * bandit defender applies it when it stands faceup at a site the bandits
  * rule, as it applies every free plan.
  */
case object Longbows extends BattlePlan:
  val cardId: DenizenId = LongbowsCard.id
  val id: PowerId = LongbowsCard.power.id
  val Dice: Int = 1

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Longbows", context.side, Longbows.Dice), Vector.empty,
      Vector(PlanDice.effect(context.side, Longbows.Dice))))
