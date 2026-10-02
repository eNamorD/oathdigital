package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.CatalogCards
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
