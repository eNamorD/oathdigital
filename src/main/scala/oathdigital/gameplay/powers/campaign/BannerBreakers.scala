package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object BannerBreakersCard extends Denizen(DenizenId("222"), "Banner Breakers", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.banner-breakers"),
    persistent = false, cost = Cost(favor = 1, favorBurnt = 1),
    text = "+3 [attack-die] if the defender has the Darkest Secret or " +
      "People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Banner Breakers (card 222), an attacker's battle plan: "[favor]
  * [favor-burnt] +3 [attack-die] if the defender has the Darkest Secret or
  * People's Favor."
  *
  * A favor is placed onto the card and another is burnt. It adds three attack
  * dice, and is offered only while the defender holds either banner, so never
  * against bandits.
  */
case object BannerBreakers extends BattlePlan:
  val cardId: DenizenId = BannerBreakersCard.id
  val id: PowerId = BannerBreakersCard.power.id
  val Dice: Int = 3

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).filter(_ => Banner.all.exists(context.enemyHolds))
      .map(source => CampaignPlanOffer(source,
        PlanDice.label("Banner Breakers", context.side, BannerBreakers.Dice),
        Vector(CampaignPlanCost.Favor(1), CampaignPlanCost.FavorBurnt(1)),
        Vector(CampaignPlanEffect.AddAttackDice(BannerBreakers.Dice))))
