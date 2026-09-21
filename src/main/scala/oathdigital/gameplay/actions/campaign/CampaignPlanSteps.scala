package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The two battle-plan windows: the attacker's, then the defender's. A player's
  * window is a `Repeat` of [[CampaignPlanChoice]] passes, each asking one plan
  * and applying it, so each plan is paid and applied the moment it is chosen and
  * the next options see the result. The `Repeat` ends on Finish, or when a pass
  * has nothing left to offer.
  */
private[campaign] object CampaignPlanSteps {
  def attacker(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    loop(catalog, actor, CampaignPlanSide.Attacker)

  /** A player defender chooses plans; a bandit defender applies its own. */
  def defender(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    Branch((ready, pending) => CampaignSetup.setup(ready, actor, pending)
      .map(_.defender) match {
        case Some(CampaignDefender.Bandits) => Vector(
          new CampaignPlanChoice(catalog, actor, CampaignPlanSide.Defender))
        case Some(_) => Vector(loop(catalog, actor, CampaignPlanSide.Defender))
        case None => Vector.empty
      })

  private def loop(catalog: ExecutableCatalog, actor: PlayerId,
      side: CampaignPlanSide): Operation = Repeat(
    (_, pending) => !CampaignAnswers.finished(pending,
      CampaignIds.planDecision(side)),
    new CampaignPlanChoice(catalog, actor, side))
}
