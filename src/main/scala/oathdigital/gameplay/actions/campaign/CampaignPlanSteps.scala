package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The two battle-plan windows: the attacker's, then the defender's. Each is a
  * `Repeat` of a decision and its application, so each plan is paid and applied
  * the moment it is chosen and the next options see the result.
  */
private[campaign] object CampaignPlanSteps {
  private def decisionId(side: CampaignPlanSide): String = side match {
    case CampaignPlanSide.Attacker => CampaignIds.attackerPlan
    case CampaignPlanSide.Defender => CampaignIds.defenderPlan
  }

  private def ownerOf(setup: CampaignSetup, side: CampaignPlanSide): Option[PlayerId] =
    side match {
      case CampaignPlanSide.Attacker => Some(setup.actor)
      case CampaignPlanSide.Defender => setup.defender match {
        case CampaignDefender.Player(player) => Some(player)
        case CampaignDefender.Bandits => None
      }
    }

  def attacker(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    Sequence(Vector[Operation](loop(catalog, actor, CampaignPlanSide.Attacker)),
      Some(PowerWindow.CampaignAttackerBattlePlans))

  /** A player defender chooses plans; a bandit defender applies its own. */
  def defender(catalog: ExecutableCatalog, actor: PlayerId): Operation =
    Sequence(Vector[Operation](Branch((ready, pending) =>
      CampaignSetup.setup(ready, actor, pending) match {
        case Some(setup) if setup.defender == CampaignDefender.Bandits =>
          Vector(BuildOps((state, tree) => CampaignSetup.setup(state, actor, tree)
            .toRight(OathViolation.InvalidEventOrder(
              "Campaign reached the defender's plans without a setup"))
            .map(found => CampaignPlans.banditPlans(catalog, state, found)
              .flatMap(CampaignPlans.apply(_, actor)))))
        case Some(_) => Vector(loop(catalog, actor, CampaignPlanSide.Defender))
        case None => Vector.empty
      })), Some(PowerWindow.CampaignDefenderBattlePlans))

  private def loop(catalog: ExecutableCatalog, actor: PlayerId,
      side: CampaignPlanSide): Operation = {
    val id = decisionId(side)
    Repeat((ready, pending) => !CampaignAnswers.finished(pending, id) &&
        remaining(catalog, ready, actor, pending, side).nonEmpty,
      Sequence(Vector[Operation](
        Branch((ready, pending) => decision(catalog, ready, actor, pending, side)),
        Branch((ready, pending) => application(catalog, actor, pending, side)))))
  }

  private def remaining(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, pending: PendingTree, side: CampaignPlanSide)
      : Vector[CampaignPlanOption] = (for {
    setup <- CampaignSetup.setup(ready, actor, pending)
    owner <- ownerOf(setup, side)
  } yield CampaignPlans.available(catalog, ready, setup, side, owner,
    CampaignAnswers.picks(pending, decisionId(side)))).getOrElse(Vector.empty)

  private def decision(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, pending: PendingTree, side: CampaignPlanSide)
      : Vector[Operation] = (for {
    setup <- CampaignSetup.setup(ready, actor, pending)
    owner <- ownerOf(setup, side)
    options = remaining(catalog, ready, actor, pending, side)
    if options.nonEmpty
  } yield Vector[Operation](Decide(decisionId(side), owner,
    DecisionQuery.ChooseOne(options.map(_.queryOption) :+
      DecisionOption.Button(CampaignIds.finish, "Finish battle plans"),
      heading = Some(side match {
        case CampaignPlanSide.Attacker => "Choose a battle plan, or finish"
        case CampaignPlanSide.Defender =>
          "Defender: choose a battle plan, or finish"
      }))))).getOrElse(Vector.empty)

  /** Applies the latest pick. The chosen plan is found in the unfiltered
    * options, which still offer it: nothing changed since it was chosen.
    */
  private def application(catalog: ExecutableCatalog, actor: PlayerId,
      pending: PendingTree, side: CampaignPlanSide): Vector[Operation] =
    CampaignAnswers.lastPick(pending, decisionId(side)).toVector.map(pick =>
      BuildOps((ready, tree) => (for {
        setup <- CampaignSetup.setup(ready, actor, tree)
        owner <- ownerOf(setup, side)
        option <- CampaignPlans.options(catalog, ready, setup, side, owner)
          .find(_.ref == pick)
      } yield CampaignPlans.apply(option, owner)).toRight(
        OathViolation.InvalidEventOrder(
          "a chosen Campaign battle plan is no longer available"))))
}
