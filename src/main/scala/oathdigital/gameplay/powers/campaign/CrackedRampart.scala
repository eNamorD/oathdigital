package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Cracked Rampart (edifice E20, ruined), a defender's battle plan: "+1 defense
  * die if this site is targeted."
  *
  * It is used by the ruler of the site the ruined edifice stands at, and only
  * when a Conquest targets that site. A Raid never targets a site.
  */
final case class CrackedRampart private (edificeId: EdificeId)
    extends BattlePlan {
  def id: PowerId = CrackedRampart.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Edifice(edificeId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.edifice(edificeId, EdificeSide.Ruined)
      .filter(source => context.targets(source.siteId))
      .map(source => CampaignPlanOffer(source,
        "Cracked Rampart: add 1 defense die", Vector.empty,
        Vector(CampaignPlanEffect.AddDefenseDice(1))))
}

object CrackedRampart {
  val id: PowerId = PowerId("edifice.e20.ruined")

  def forCatalog(catalog: ExecutableCatalog): Option[CrackedRampart] =
    CatalogCards.edifice(catalog, id).map(new CrackedRampart(_))
}
