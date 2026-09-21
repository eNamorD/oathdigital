package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Offer, Transform}
import oathdigital.model._

/** A power that offers a Campaign battle plan. It is chosen at the plan step,
  * by the ruler of its source, so it is automatic: it needs no selection at the
  * start of the action, whatever the catalog's `persistent` flag says.
  *
  * A plan states three things:
  *
  *  - `plan` says whether the plan is usable now and what it costs and does. It
  *    reads only where its card stands and the Campaign's setup; whether the
  *    user can pay is left to the plan window, which dry-runs the plan. The
  *    offer is hooked at the window of every side in `sides`, and `plan` reads
  *    the side from its context.
  *  - `later` is what a used plan does when the Campaign reaches a later window
  *    (Outriders ignores the skulls when the attack is scored). It runs only when
  *    the plan was chosen, and its operations are appended to the window's
  *    children, so a plan that must run last is registered at the end of the
  *    Campaign, which is `CampaignActionEligibility`, the root.
  *  - `cardRef` is how the plan's source is named in a decision, which is how a
  *    later window learns the plan was chosen.
  */
trait BattlePlan extends ContributingPower {
  /** How the plan's source is named in the plan decision. */
  def cardRef: DecisionOptionRef
  /** The sides of the Campaign that may use the plan. */
  def sides: Set[CampaignPlanSide]
  /** The plan, when it is usable now for `context`'s side. */
  def plan(context: PlanContext): Option[CampaignPlanOffer]
  /** What a used plan adds at a later window. */
  def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map.empty

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override def resolution: PowerResolution = PowerResolution.Automatic

  final override lazy val contributions: Map[PowerWindow, Vector[Contribution]] = {
    val offers: Map[PowerWindow, Vector[Contribution]] = sides.toVector.map {
      side => BattlePlan.windowOf(side) -> Vector[Contribution](Offer(ctx =>
        PlanContext.of(ctx).filter(_.side == side).flatMap(plan)))
    }.toMap
    val afterwards: Map[PowerWindow, Vector[Contribution]] = later.map {
      case (window, build) => window -> Vector[Contribution](Transform(
        (ctx, children) => children :+ Branch((ready, pending) =>
          PlanUse.chosen(ready, pending, ctx.activePlayer, cardRef, sides,
            BattlePlan.outcomeKnown(window)).fold(Vector.empty[Operation])(build))))
    }
    offers ++ afterwards
  }
}

object BattlePlan {
  def windowOf(side: CampaignPlanSide): PowerWindow = side match {
    case CampaignPlanSide.Attacker => PowerWindow.CampaignAttackerBattlePlans
    case CampaignPlanSide.Defender => PowerWindow.CampaignDefenderBattlePlans
  }

  /** The windows walked after `RecordCampaignResult`, where the recorded result
    * is this Campaign's. The root is the last of them.
    */
  private val afterOutcome: Set[PowerWindow] = Set(PowerWindow.CampaignLosses,
    PowerWindow.CampaignPlacement, PowerWindow.CampaignRaidTransfer,
    PowerWindow.CampaignRaidRelocation, PowerWindow.CampaignActionEligibility)

  def outcomeKnown(window: PowerWindow): Boolean = afterOutcome(window)
}
