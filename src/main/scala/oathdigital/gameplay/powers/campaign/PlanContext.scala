package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds, CampaignPlans, CampaignSetup}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** What a battle plan reads when it is asked whether it is usable now: the
  * state, the Campaign's setup and the side the window is for.
  *
  * A plan is used only by the ruler of its source, so a plan asks this for where
  * a card stands: an adviser of the plan's user, a relic held faceup by the
  * user, or a card at a site the user rules. A bandit defender is the ruler of
  * the sites Bandits rule and holds nothing else.
  */
final case class PlanContext(ready: ReadyGame, setup: CampaignSetup,
    side: CampaignPlanSide) {
  /** The player who would use the plan; `None` for a bandit defender. */
  def user: Option[PlayerId] = CampaignPlans.userOf(setup, side)

  private def ruler: CampaignDefender = user.fold[CampaignDefender](
    CampaignDefender.Bandits)(CampaignDefender.Player(_))

  /** Whether the plan's user rules `site`. */
  def rules(site: SiteId): Boolean =
    CampaignSetup.defenderAt(ready, site).contains(ruler)

  private def held: Option[PlayerState] = user.flatMap(player =>
    ready.game.current.players.find(_.player == player))

  private def sitesRuled: Vector[(SiteId, SiteState)] =
    ready.game.current.map.inPlay.filter(rules).flatMap(site =>
      ready.game.current.map.sites.get(site).map(site -> _))

  /** A denizen the user holds as an adviser, in either orientation, or one that
    * is faceup at a site the user rules.
    */
  def denizen(id: DenizenId): Option[CampaignPlanSource] =
    held.flatMap(player => player.advisers.collectFirst {
      case card: DenizenState if card.id == id =>
        CampaignPlanSource.Adviser(player.player, id): CampaignPlanSource
    }).orElse(sitesRuled.collectFirst {
      case (site, state) if state.denizens.exists {
        case card: DenizenState =>
          card.id == id && card.orientation == Orientation.FaceUp
        case _ => false
      } => CampaignPlanSource.SiteCard(site, id): CampaignPlanSource
    })

  /** A relic the user holds faceup. A relic at a site is facedown, and a plan
    * cannot use it.
    */
  def relic(id: RelicId): Option[CampaignPlanSource] = held.flatMap(player =>
    player.relics.collectFirst {
      case card if card.id == id && card.orientation == Orientation.FaceUp =>
        CampaignPlanSource.Relic(player.player, id): CampaignPlanSource
    })

  /** An edifice on the given face at a site the user rules. */
  def edifice(id: EdificeId, face: EdificeSide)
      : Option[CampaignPlanSource.SiteEdifice] = sitesRuled.collectFirst {
    case (site, state) if state.denizens.exists {
      case card: EdificeState => card.id == id && card.side == face
      case _ => false
    } => CampaignPlanSource.SiteEdifice(site, id)
  }

  /** Whether the plan's user has their pawn at `site`. */
  def pawnAt(site: SiteId): Boolean = user.exists(player =>
    ready.game.current.players.find(_.player == player)
      .exists(_.pawnSite.contains(site)))

  /** Whether `site` is a target of the Campaign. */
  def targets(site: SiteId): Boolean = setup.targetSites.contains(site)

  /** Whether any target of a Conquest is in `region`. A Raid targets no site. */
  def targetsIn(region: Region): Boolean = setup.targetSites.exists(site =>
    ready.game.current.map.regionOf(site).contains(region))
}

object PlanContext {
  /** The context of the plan window `ctx` is gathered for; `None` at any other
    * window, or before the Campaign's force is known.
    */
  def of(ctx: PowerCtx): Option[PlanContext] = (ctx.window match {
    case PowerWindow.CampaignAttackerBattlePlans =>
      Some(CampaignPlanSide.Attacker)
    case PowerWindow.CampaignDefenderBattlePlans =>
      Some(CampaignPlanSide.Defender)
    case _ => None
  }).flatMap(side => CampaignSetup.setup(ctx.state, ctx.activePlayer,
    PendingTree(ctx.nodePath, ctx.answered)).map(PlanContext(ctx.state, _, side)))
}

/** A plan that was used, as a later window sees it. `result` is the Campaign's
  * recorded result once the outcome is known (the windows from the losses on),
  * and is what a step after the losses must read, because the losses change the
  * board.
  */
final case class PlanUse(side: CampaignPlanSide, actor: PlayerId,
    result: Option[CampaignResult], ready: ReadyGame) {
  /** The player who used the plan; `None` for a bandit defender. */
  def user: Option[PlayerId] = side match {
    case CampaignPlanSide.Attacker => Some(actor)
    case CampaignPlanSide.Defender => result.map(_.defender).collect {
      case CampaignDefender.Player(player) => player
    }
  }

  /** Whether the plan's user won. `None` before the outcome is known. */
  def won: Option[Boolean] = result.map(_.attackerWins == (
    side == CampaignPlanSide.Attacker))
}

object PlanUse {
  /** The plan named by `ref`, if it was used on one of `sides` in this
    * Campaign: chosen by a player, or applied by a bandit defender, which the
    * application recorded (`CampaignPlans.appliedMarker`). `afterOutcome` says
    * whether `lastCampaignResult` is this Campaign's already.
    */
  def chosen(ready: ReadyGame, pending: PendingTree, actor: PlayerId,
      ref: DecisionOptionRef, sides: Set[CampaignPlanSide],
      afterOutcome: Boolean): Option[PlanUse] = {
    val picked = sides.toVector.find(side => CampaignAnswers.picks(pending,
      CampaignIds.planDecision(side)).contains(ref))
    val banditApplied = Option.when(sides(CampaignPlanSide.Defender) &&
      ready.game.current.rollPools.contains(CampaignPlans.appliedMarker(ref)))(
      CampaignPlanSide.Defender)
    picked.orElse(banditApplied).map(side => PlanUse(side, actor,
      Option.when(afterOutcome)(ready.game.current.lastCampaignResult)
        .flatten.filter(_.attacker == actor), ready))
  }
}
