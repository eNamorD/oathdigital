package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Watchdog (card 234), a defender's battle plan: "+1 defense die if any target
  * is in the Cradle."
  *
  * It is used by the ruler of its card, from an adviser or a ruled site. A
  * bandit defender uses it from a site Bandits rule, without choosing. A Raid
  * targets no site, so it never applies to one.
  */
final case class Watchdog private (cardId: DenizenId) extends BattlePlan {
  def id: PowerId = Watchdog.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    if (!context.targetsIn(Region.Cradle)) None
    else context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Watchdog: add 1 defense die", Vector.empty,
      Vector(CampaignPlanEffect.AddDefenseDice(1))))
}

object Watchdog {
  val id: PowerId = PowerId("denizen.watchdog")

  def forCatalog(catalog: ExecutableCatalog): Option[Watchdog] =
    CatalogCards.denizen(catalog, id).map(new Watchdog(_))
}
