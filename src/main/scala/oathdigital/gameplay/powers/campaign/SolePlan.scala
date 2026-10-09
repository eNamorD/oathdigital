package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds}
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx}
import oathdigital.model._

/** The restrictions of a battle plan its user may use with no other (Code
  * of Honor; Peace Envoy in slice 2), at the window of each side in
  * `sides`. The plan is withheld once its user has chosen another plan in
  * the window, with `notWithOthers`. Once it is chosen, the user's other
  * plans are withheld, with `noOthers`. Both read the side's answered plan
  * picks. A bandit defender chooses nothing; its offer's `alone` flag covers
  * it.
  */
private[campaign] object SolePlan:
  def restrictions(plan: DecisionOptionRef, sides: Set[CampaignPlanSide],
      source: PowerSourceRef, notWithOthers: NoteKey, noOthers: NoteKey)
      : Map[PowerWindow, Vector[OptionRestriction]] =
    sides.toVector.map { side =>
      def blocked(ctx: PowerCtx, ref: DecisionOptionRef): Option[NoteKey] =
        val picks = CampaignAnswers.picks(
          PendingTree(Vector.empty, ctx.answered),
          CampaignIds.planDecision(side))
        if ref == plan then Option.when(picks.exists(_ != plan))(notWithOthers)
        else Option.when(picks.contains(plan))(noOthers)
      BattlePlan.windowOf(side) -> Vector(OptionRestriction(
        (ctx, ref) => blocked(ctx, ref).map(_ =>
          OathViolation.CampaignUnavailable(
            "a battle plan used alone was combined with another")),
        (ctx, ref) => blocked(ctx, ref).map(key => key(source))))
    }.toMap
