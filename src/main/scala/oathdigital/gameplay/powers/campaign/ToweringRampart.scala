package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Towering Rampart (edifice E20, intact), a defender's battle plan: "+2 defense
  * dice if your pawn is at this site or this site is targeted."
  *
  * It is used by the ruler of the site the edifice stands at. A Raid targets no
  * site, so a Raid gets it only when the ruler's pawn is here. A bandit ruler
  * has no pawn, so it applies when the site is targeted, without choosing.
  */
final case class ToweringRampart private (edificeId: EdificeId)
    extends BattlePlan {
  def id: PowerId = ToweringRampart.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Edifice(edificeId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.edifice(edificeId, EdificeSide.Intact).filter(source =>
      context.pawnAt(source.siteId) || context.targets(source.siteId))
      .map(source => CampaignPlanOffer(source,
        "Towering Rampart: add 2 defense dice", Vector.empty,
        Vector(CampaignPlanEffect.AddDefenseDice(2))))
}

object ToweringRampart {
  val id: PowerId = PowerId("edifice.e20.intact")

  def forCatalog(catalog: ExecutableCatalog): Option[ToweringRampart] =
    CatalogCards.edifice(catalog, id).map(new ToweringRampart(_))
}
