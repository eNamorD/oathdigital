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
  *  - A player asks a decision listing the plans still unchosen: those the
  *    user can pay for (a dry run of each says so, with every power's changes
  *    to its cost, and prices the option from what it recorded), the rest
  *    shown unavailable with why, then "Finish", worded with how many plans
  *    are chosen (`finishWith`). A plan its user can pay for
  *    shows its offer's warning. Nothing usable means nothing
  *    is asked, the pass does nothing, and the `Repeat` ends.
  *  - The chosen plan is applied as a [[CampaignPlanApplication]].
  *  - A bandit defender applies every cost-free plan it is offered and can
  *    pay for, without asking.
  *
  * A walk resuming inside a pass keeps the two slots of the pass whatever is
  * offered, because the plan it is paying for has been chosen and is no longer
  * listed.
  */
final class CampaignPlanChoice(catalog: ExecutableCatalog, val actor: PlayerId,
    val side: CampaignPlanSide) extends OfferHost:
  override val window: Option[PowerWindow] = Some(side match {
    case CampaignPlanSide.Attacker => PowerWindow.CampaignAttackerBattlePlans
    case CampaignPlanSide.Defender => PowerWindow.CampaignDefenderBattlePlans
  })
  override val children: Vector[Operation] = Vector.empty

  def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
      : Vector[Operation] =
    val pending = PendingTree(Vector.empty, pass.answered)
    CampaignSetup.setup(pass.state, actor, pending).fold(
      Vector.empty[Operation])(setup => CampaignPlans.userOf(setup, side) match {
      case None => banditPlans(setup, CampaignPlans.sorted(offers), pass)
      case Some(user) => userPass(setup, user, CampaignPlans.sorted(offers),
        pending, pass)
    })

  private def applicationOf(setup: CampaignSetup, offered: OfferedPlan)
      : CampaignPlanApplication =
    new CampaignPlanApplication(catalog, setup, side, offered)

  /** The option, with the offer's warning when it has one. */
  private def warned(offered: OfferedPlan, option: DecisionOption)
      : DecisionOption =
    offered.offer.warning.fold(option)(DecisionOption.Warned(option, _))

  /** Bandits pay nothing, so a plan that costs is never applied, and neither is
    * one a power makes unpayable for them (a surcharge in secrets, which bandits
    * do not hold). A plan used alone (Code of Honor) is applied with no other
    * when it would be applied.
    */
  private def banditPlans(setup: CampaignSetup, offers: Vector[OfferedPlan],
      pass: OfferHost.Pass): Vector[Operation] =
    val applied = offers
      .filter(offered => offered.offer.costs.isEmpty &&
        offered.offer.withheld.isEmpty)
      .map(applicationOf(setup, _))
      .filter(application => pass.applies(application).isRight)
    val chosen = applied.find(_.offered.offer.alone).fold(applied)(Vector(_))
    chosen.flatMap(application => Vector[Operation](
      CampaignNotes.leaf(CampaignNotes.banditActivationNote(
        application.offered.offer.source)), application))

  private def userPass(setup: CampaignSetup, user: PlayerId,
      offers: Vector[OfferedPlan], pending: PendingTree, pass: OfferHost.Pass)
      : Vector[Operation] =
    val decisionId = CampaignIds.planDecision(side)
    val chosen = CampaignAnswers.picks(pending, decisionId)
    def unavailable(offered: OfferedPlan, reason: OptionReason)
        : DecisionOption =
      DecisionOption.Unavailable(CampaignPlans.optionOf(offered), reason)
    val listed = offers.filterNot(offered =>
      chosen.contains(CampaignPlans.refOf(offered.offer.source)))
      .map(offered => offered.offer.withheld match
        case Some(reason) => unavailable(offered, reason)
        case None => pass.applies(applicationOf(setup, offered)) match
          case Right(operations) => warned(offered, PlanPrice.priced(
            CampaignPlans.optionOf(offered), offered.offer, operations))
          case Left(violation) =>
            unavailable(offered, OptionReason.Written(PlanPrice.shortfall(
              pass.state, user, offered.offer, violation))))
    val playable = listed.exists(_.available)
    if !playable && !pass.resuming then Vector.empty
    else Vector[Operation](
      Branch((_, _) => if !playable then Vector.empty
        else Vector(decision(decisionId, user, listed, chosen.size))),
      Branch((_, tree) => CampaignAnswers.lastPick(tree, decisionId) match {
        case None => Vector.empty
        case Some(ref) => offers.find(offered =>
          CampaignPlans.refOf(offered.offer.source) == ref) match {
          // The application is recorded as one pick (`OpenPick`), so what
          // it pays, however a power changed the price, is what the plan
          // paid. A button plan, the title's, keeps its words.
          case Some(offered) => Vector[Operation](
            CampaignNotes.leaf(CampaignNotes.activationNote(
              offered.offer.source, user, actor)),
            OpenPick(decisionId, ref, CampaignPlans.optionOf(offered) match
              case DecisionOption.Badged(DecisionOption.Button(_, label), _) =>
                Some(label)
              case DecisionOption.Button(_, label) => Some(label)
              case _ => None),
            applicationOf(setup, offered),
            ClosePick())
          case None => Vector(BuildOps((_, _) => Left(
            OathViolation.InvalidEventOrder(
              "a chosen Campaign battle plan is no longer available"))))
        }
      }))

  private def decision(id: String, user: PlayerId,
      listed: Vector[DecisionOption], picks: Int): Decide = Decide(id, user,
    DecisionQuery.ChooseOne(listed :+
      DecisionOption.Button(CampaignIds.finish,
        CampaignPlanChoice.finishWith(picks)),
    heading = Some(side match {
      case CampaignPlanSide.Attacker => "Choose a battle plan, or finish"
      case CampaignPlanSide.Defender =>
        "Defender: choose a battle plan, or finish"
    })))

object CampaignPlanChoice:
  /** "Finish with no battle plans", or "with 2 battle plans": "Finish" says
    * how many plans the side fights with, as the modifier decision's "Start"
    * says how many modifiers the action starts with. */
  def finishWith(picks: Int): String = picks match
    case 0 => "Finish with no battle plans"
    case 1 => "Finish with 1 battle plan"
    case _ => s"Finish with $picks battle plans"
