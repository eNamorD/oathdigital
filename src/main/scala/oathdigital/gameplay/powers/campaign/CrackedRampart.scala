package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** Cracked Rampart (edifice E20, ruined), a defender's battle plan: "+1 defense
  * die if this site is targeted."
  *
  * It is used by the ruler of the site the ruined edifice stands at, and only
  * when a Conquest targets that site. A Raid never targets a site.
  */
case object CrackedRampart extends BattlePlan:
  val edificeId: EdificeId = ToweringRampartCard.id
  val id: PowerId = ToweringRampartCard.ruined.power.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Edifice(edificeId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.edifice(edificeId, EdificeSide.Ruined)
      .filter(source => context.targets(source.siteId))
      .map(source => CampaignPlanOffer(source,
        "Cracked Rampart: add 1 defense die", Vector.empty,
        Vector(CampaignPlanEffect.AddDefenseDice(1))))
