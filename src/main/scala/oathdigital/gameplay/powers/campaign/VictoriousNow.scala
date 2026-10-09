package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** "You're victorious now" (Peace Envoy; Hearts and Minds can reuse it):
  * a used plan decides the battle for its user's side. Nothing is rolled
  * and nothing is sacrificed: the attack roll, the sacrifice decision and
  * the defense roll each fold to no children. The result is recorded as
  * usual from the empty rolls, then rewritten so that the user's side
  * wins, and `line` is written after it.
  *
  * Each piece is a `wrapping` of the plan, so it acts only in a Campaign
  * whose answered plan picks chose the plan, and the walk stays a pure
  * function of its answers. The plan's losses are its own.
  */
private[campaign] object VictoriousNow:
  def wrapping(line: PlanUse => Note)
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] =
    Map(
      PowerWindow.CampaignAttackRoll -> unrolled,
      PowerWindow.CampaignSacrificeSelection -> unrolled,
      PowerWindow.CampaignDefenseRoll -> unrolled,
      PowerWindow.CampaignAfterOutcome -> ((use, children) =>
        recorded(use.side, children) :+ line(use)))

  private val unrolled: (PlanUse, Vector[Operation]) => Vector[Operation] =
    (_, _) => Vector.empty

  /** `children`, with the recorded result won by `winner`'s side. */
  private def recorded(winner: CampaignPlanSide, children: Vector[Operation])
      : Vector[Operation] = LossRewrite(children) {
    case RecordCampaignResult(result) => Vector(RecordCampaignResult(
      result.copy(attackerWins = winner == CampaignPlanSide.Attacker)))
    case other => Vector(other)
  }
