package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Wrestlers (card 1), a defender's battle plan: "+1 defense die if you sacrifice
  * one warband in your force."
  *
  * The cost is a warband sacrifice: the defender's board in a Raid, or a warband
  * at a target site the defender rules in a Conquest (they choose which site when
  * several qualify). The sacrificed warband is gone before the defense is scored,
  * so it lowers the recorded force by one. A defender with no warband in their
  * force cannot pay, and the plan is not offered.
  */
final case class Wrestlers private (cardId: DenizenId) extends BattlePlan {
  def id: PowerId = Wrestlers.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Wrestlers: sacrifice a warband for 1 defense die",
      Vector(CampaignPlanCost.SacrificeWarband),
      Vector(CampaignPlanEffect.AddDefenseDice(1))))
}

object Wrestlers {
  val id: PowerId = PowerId("denizen.wrestlers")

  def forCatalog(catalog: ExecutableCatalog): Option[Wrestlers] =
    CatalogCards.denizen(catalog, id).map(new Wrestlers(_))
}
