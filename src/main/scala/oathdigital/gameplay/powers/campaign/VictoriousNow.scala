package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** "You're victorious now" (Peace Envoy and Hearts and Minds):
  * a used plan decides the battle for its user's side. Nothing is rolled
  * and nothing is sacrificed: the attack roll, the sacrifice decision and
  * the defense roll each fold to no children. The result is recorded as
  * usual from the empty rolls, then rewritten so that the user's side
  * wins, and Peace Envoy's `line` is written after it; Hearts and Minds
  * writes none.
  *
  * Each piece is a `wrapping` of the plan, so it acts only in a Campaign
  * whose answered plan picks chose the plan, and the walk stays a pure
  * function of its answers. The plan's losses are its own.
  */
private[campaign] object VictoriousNow:
  def wrapping(line: PlanUse => Note): Map[PowerWindow, BattlePlan.Wrap] =
    windows(use => Vector(line(use)))

  /** The same, with no line of the plan's own (Hearts and Minds). */
  def silent: Map[PowerWindow, BattlePlan.Wrap] = windows(_ => Vector.empty)

  /** The four windows, writing `after` once the result is recorded. */
  private def windows(after: PlanUse => Vector[Operation])
      : Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignAttackRoll -> unrolled,
    PowerWindow.CampaignSacrificeSelection -> unrolled,
    PowerWindow.CampaignDefenseRoll -> unrolled,
    PowerWindow.CampaignAfterOutcome -> ((ctx, use, children) =>
      recorded(ctx, use.side, children) ++ after(use)))

  private val unrolled: BattlePlan.Wrap = (_, _, _) => Vector.empty

  /** `children`, with the recorded result won by `winner`'s side. */
  private def recorded(ctx: PowerCtx, winner: CampaignPlanSide,
      children: Vector[Operation]): Vector[Operation] =
    LossRewrite(ctx, children) {
      case RecordCampaignResult(result) => Vector(RecordCampaignResult(
        result.copy(attackerWins = winner == CampaignPlanSide.Attacker)))
      case other => Vector(other)
    }
