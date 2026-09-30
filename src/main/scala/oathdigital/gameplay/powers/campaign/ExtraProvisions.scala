package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Extra Provisions (card 48), a defender's battle plan: "[favor] +
  * [defense-die]".
  *
  * A favor is placed onto the card for one defense die. Bandits pay nothing,
  * so a bandit defender never applies it.
  */
final case class ExtraProvisions private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = ExtraProvisions.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Extra Provisions: add 1 defense die", Vector(CampaignPlanCost.Favor(1)),
      Vector(CampaignPlanEffect.AddDefenseDice(1))))

object ExtraProvisions:
  val id: PowerId = PowerId("denizen.extra-provisions")

  def forCatalog(catalog: ExecutableCatalog): Option[ExtraProvisions] =
    CatalogCards.denizen(catalog, id).map(new ExtraProvisions(_))
