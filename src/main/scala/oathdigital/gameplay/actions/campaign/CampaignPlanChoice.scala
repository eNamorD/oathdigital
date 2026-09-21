package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.OfferHost
import oathdigital.model._

/** One side's battle-plan window: the node the powers' `Offer`s are folded into.
  * A pass of it, inside a `Repeat`, asks the user to choose one plan and then
  * pays for and applies it, so the next pass offers what is still usable after
  * the payment.
  *
  * What a pass walks:
  *
  *  - A player asks a decision listing the plans still unchosen that the user
  *    can pay for (a dry run of each plan says so, with every power's changes
  *    to its cost, and prices the option from what it recorded), then "Finish".
  *    Nothing usable means nothing is asked, the pass does nothing, and the
  *    `Repeat` ends.
  *  - The chosen plan is applied as a [[CampaignPlanApplication]].
  *  - A bandit defender applies every cost-free plan it is offered and can
  *    pay for, without asking.
  *
  * A walk resuming inside a pass keeps the two slots of the pass whatever is
  * offered, because the plan it is paying for has been chosen and is no longer
  * listed.
  */
final class CampaignPlanChoice(catalog: ExecutableCatalog, val actor: PlayerId,
    val side: CampaignPlanSide) extends OfferHost {
  override val window: Option[PowerWindow] = Some(side match {
    case CampaignPlanSide.Attacker => PowerWindow.CampaignAttackerBattlePlans
    case CampaignPlanSide.Defender => PowerWindow.CampaignDefenderBattlePlans
  })
  override val children: Vector[Operation] = Vector.empty

  def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
      : Vector[Operation] = {
    val pending = PendingTree(Vector.empty, pass.answered)
    CampaignSetup.setup(pass.state, actor, pending).fold(
      Vector.empty[Operation])(setup => CampaignPlans.userOf(setup, side) match {
      case None => banditPlans(setup, CampaignPlans.sorted(offers), pass)
      case Some(user) => userPass(setup, user, CampaignPlans.sorted(offers),
        pending, pass)
    })
  }

  private def applicationOf(setup: CampaignSetup, offered: OfferedPlan)
      : CampaignPlanApplication =
    new CampaignPlanApplication(catalog, setup, side, offered)

  /** Bandits pay nothing, so a plan that costs is never applied, and neither is
    * one a power makes unpayable for them (a surcharge in secrets, which bandits
    * do not hold).
    */
  private def banditPlans(setup: CampaignSetup, offers: Vector[OfferedPlan],
      pass: OfferHost.Pass): Vector[Operation] = offers
    .filter(_.offer.costs.isEmpty).map(applicationOf(setup, _))
    .filter(application => pass.applies(application).isRight)

  private def userPass(setup: CampaignSetup, user: PlayerId,
      offers: Vector[OfferedPlan], pending: PendingTree, pass: OfferHost.Pass)
      : Vector[Operation] = {
    val decisionId = CampaignIds.planDecision(side)
    val chosen = CampaignAnswers.picks(pending, decisionId)
    val listed = offers.filterNot(offered =>
      chosen.contains(CampaignPlans.refOf(offered.offer.source)))
      .flatMap(offered => pass.applies(applicationOf(setup, offered)).toOption
        .map(operations => PlanPrice.priced(CampaignPlans.optionOf(offered),
          offered.offer, operations)))
    if (listed.isEmpty && !pass.resuming) Vector.empty
    else Vector[Operation](
      Branch((_, _) => if (listed.isEmpty) Vector.empty
        else Vector(decision(decisionId, user, listed))),
      Branch((_, tree) => CampaignAnswers.lastPick(tree, decisionId) match {
        case None => Vector.empty
        case Some(ref) => offers.find(offered =>
          CampaignPlans.refOf(offered.offer.source) == ref) match {
          case Some(offered) => Vector(applicationOf(setup, offered))
          case None => Vector(BuildOps((_, _) => Left(
            OathViolation.InvalidEventOrder(
              "a chosen Campaign battle plan is no longer available"))))
        }
      }))
  }

  private def decision(id: String, user: PlayerId,
      listed: Vector[DecisionOption]): Decide = Decide(id, user,
    DecisionQuery.ChooseOne(listed :+
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans"),
    heading = Some(side match {
      case CampaignPlanSide.Attacker => "Choose a battle plan, or finish"
      case CampaignPlanSide.Defender =>
        "Defender: choose a battle plan, or finish"
    })))
}
