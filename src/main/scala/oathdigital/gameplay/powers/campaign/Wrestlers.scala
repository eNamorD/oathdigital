package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object WrestlersCard extends Denizen(DenizenId("1"), "Wrestlers", Suit.Order):
  val power = PrintedPower(PowerId("denizen.wrestlers"),
    persistent = false, cost = Cost.free,
    text = "+ [defense-die] if you sacrifice one warband in your force.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wrestlers (card 1), a defender's battle plan: "+1 defense die if you sacrifice
  * one warband in your force."
  *
  * The cost is a warband sacrifice: the defender's board in a Raid, or a warband
  * at a target site the defender rules in a Conquest (they choose which site when
  * several qualify). The sacrificed warband is gone before the defense is scored,
  * so it lowers the recorded force by one. A defender with no warband in their
  * force cannot pay, and the plan is not offered.
  */
case object Wrestlers extends BattlePlan:
  val cardId: DenizenId = WrestlersCard.id
  val id: PowerId = WrestlersCard.power.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Wrestlers: sacrifice a warband for 1 defense die",
      Vector(CampaignPlanCost.SacrificeWarband),
      Vector(CampaignPlanEffect.AddDefenseDice(1))))
