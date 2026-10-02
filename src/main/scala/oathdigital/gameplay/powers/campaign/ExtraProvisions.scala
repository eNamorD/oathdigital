package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object ExtraProvisionsCard extends Denizen(DenizenId("48"), "Extra Provisions", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.extra-provisions"),
    persistent = false, cost = Cost(favor = 1),
    text = "+ [defense-die]")
  val powers: Vector[PrintedPower] = Vector(power)

/** Extra Provisions (card 48), a defender's battle plan: "[favor] +
  * [defense-die]".
  *
  * A favor is placed onto the card for one defense die. Bandits pay nothing,
  * so a bandit defender never applies it.
  */
case object ExtraProvisions extends BattlePlan:
  val cardId: DenizenId = ExtraProvisionsCard.id
  val id: PowerId = ExtraProvisionsCard.power.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Extra Provisions: add 1 defense die", Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(1))))
