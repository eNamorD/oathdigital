package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Edifice, EdificeFace, Locked, PrintedPower}
import oathdigital.model._

object ToweringRampartCard extends Edifice(EdificeId("E20"), Suit.Order):
  object intact extends EdificeFace("Towering Rampart") with Locked:
    val power = PrintedPower(PowerId("edifice.e20.intact"),
      persistent = false, cost = Cost.free,
      text = "+2 [defense-die] if your pawn is at this site or this site " +
        "is targeted.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Cracked Rampart"):
    val power = PrintedPower(PowerId("edifice.e20.ruined"),
      persistent = false, cost = Cost.free,
      text = "+1 [defense-die] if this site is targeted.")
    val powers: Vector[PrintedPower] = Vector(power)

/** Towering Rampart (edifice E20, intact), a defender's battle plan: "+2 defense
  * dice if your pawn is at this site or this site is targeted."
  *
  * It is used by the ruler of the site the edifice stands at. A Raid targets no
  * site, so a Raid gets it only when the ruler's pawn is here. A bandit ruler
  * has no pawn, so it applies when the site is targeted, without choosing.
  */
case object ToweringRampart extends BattlePlan:
  val edificeId: EdificeId = ToweringRampartCard.id
  val id: PowerId = ToweringRampartCard.intact.power.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Edifice(edificeId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.edifice(edificeId, EdificeSide.Intact).filter(source =>
      context.pawnAt(source.siteId) || context.targets(source.siteId))
      .map(source => CampaignPlanOffer(source,
        "Towering Rampart: add 2 defense dice", Vector.empty,
        Vector(CampaignPlanEffect.AddDefenseDice(2))))
