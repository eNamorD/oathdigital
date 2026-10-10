package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.model._

object WeepingBannerCard extends Relic(RelicId("R42"), "Weeping Banner",
    value = 63, defense = 2):
  val power = PrintedPower(PowerId("relic.weeping-banner"),
    persistent = false, cost = Cost(secret = 1),
    text = "The attacker **must** resolve another [skull] for each " +
      "[skull] rolled.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Weeping Banner (relic R42), a defender's battle plan: "The attacker must
  * resolve another skull for each skull rolled."
  *
  * One secret is placed onto the relic. Each skull the attacker rolls kills
  * two warbands of the force instead of one (`AttackRules.skullKills`),
  * capped by the force, and a skull face scores its swords only when both
  * of its warbands are paid, so with an odd force the last warband dies and
  * scores nothing. It adds no swords: a paid skull face is worth four with
  * Fearsome General, as without it. When the skulls are ignored (Outriders
  * and the like), nothing dies and every face scores as usual. Once the
  * attack is scored, the plan writes it again through the attack scorer
  * (`AttackScore`).
  *
  * It writes no line of its own.
  */
case object WeepingBanner extends BattlePlan:
  val relicId: RelicId = WeepingBannerCard.id
  val id: PowerId = WeepingBannerCard.power.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Weeping Banner: each skull kills two warbands",
      Vector(CampaignPlanCost.Secret(1)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignAttackResult -> (use =>
      Vector(AttackScore.rescore(use.actor))))
