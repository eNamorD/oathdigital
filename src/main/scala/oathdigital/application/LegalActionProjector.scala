package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.actions.{CampaignRules, ForgeRules}
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.negotiation.NegotiationProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.gameplay.phases.rest.BeginRestProcedure
import oathdigital.model._
import oathdigital.protocol.projection._

private[application] final class LegalActionProjector(
    catalog: ExecutableCatalog,
    presentation: GamePresentationProjector,
    walkerDecisions: WalkerDecisionProjector,
    phasePowers: PhasePowerProjector
) {
  def this(catalog: ExecutableCatalog,
      presentation: GamePresentationProjector,
      walkerDecisions: WalkerDecisionProjector) =
    this(catalog, presentation, walkerDecisions,
      new PhasePowerProjector(catalog, walkerDecisions))

  /** The automatic walker powers a Travel candidate is costed against
    * (batch-1 Task 5) -- the same full catalog `OathRules` is constructed
    * with, selected down to the automatic set because a projection is built
    * before the viewer has chosen any modifier. The major-action preview
    * re-costs the same candidates with what they then selected.
    */
  private val walkerPowerCatalog = WalkerPowerCatalog.default(catalog)

  /** Travel destinations and what each would actually cost, obtained by
    * dry-running the declared Travel tree per destination rather than by a
    * second cost calculation beside it -- see `TravelProcedure.candidates`.
    */
  private def travelCandidates(context: ScopedProjectionContext) =
    TravelProcedure.candidates(catalog, context.ready, context.active.player,
      WalkerPowers.selected(walkerPowerCatalog, Vector.empty))

  /** The resources the viewer could take right now -- asked of the procedure
    * that owns Take Wealth, exactly as Travel's destinations are (batch-1
    * Task 7). This projector assembles no gameplay procedure of its own: it
    * neither builds a tree, nor spells a start selection, nor runs the
    * simulation, because each of those would be a second copy of something
    * the procedure already states.
    */
  private def takeableResources(context: ScopedProjectionContext)
      : Vector[WakeResource] =
    TakeWealthProcedure.candidates(catalog, context.ready,
      context.active.player,
      WalkerPowers.selected(walkerPowerCatalog, Vector.empty))

  /** Whether a Muster or Trade could start now: at least one source survives
    * the same preview a start runs. Asked of the procedures that own them.
    */
  private def musterStartable(context: ScopedProjectionContext): Boolean =
    MusterProcedure.startOptions(catalog, context.ready, context.active.player,
      WalkerPowers.selected(walkerPowerCatalog, Vector.empty))
      .exists(_.outcome.isRight)

  private def tradeStartable(context: ScopedProjectionContext,
      resource: TradeResource): Boolean =
    TradeProcedure.startOptions(catalog, context.ready, context.active.player,
      resource, WalkerPowers.selected(walkerPowerCatalog, Vector.empty))
      .exists(_.outcome.isRight)

  /** Whether Challenge or Place Banner Resource could start now: the same
    * dry run a start performs, asked of the procedures that own them.
    */
  private def challengeStartable(context: ScopedProjectionContext): Boolean =
    ChallengeProcedure.startable(catalog, context.ready, context.active.player,
      WalkerPowers.selected(walkerPowerCatalog, Vector.empty))

  private def negotiationStartable(context: ScopedProjectionContext): Boolean =
    NegotiationProcedure.startable(catalog, context.ready,
      context.active.player, WalkerPowers.selected(walkerPowerCatalog, Vector.empty))

  private def placeBannerResourceStartable(
      context: ScopedProjectionContext): Boolean =
    PlaceBannerResourceProcedure.startable(catalog, context.ready,
      context.active.player, WalkerPowers.selected(walkerPowerCatalog, Vector.empty))

  /** The control a resource is offered as. A name the client binds a button
    * to is presentation, which is why this mapping is here and the question
    * of whether the resource is takeable at all is not.
    */
  private def takeControl(resource: WakeResource): String = resource match {
    case WakeResource.Favor => "takeFavor"
    case WakeResource.Secret => "takeSecret"
  }

  def project(context: ScopedProjectionContext): LegalProjection =
    project(context, phasePowers.project(context))

  def project(context: ScopedProjectionContext,
      projectedPhasePowers: Vector[PhasePowerProjection]): LegalProjection = {
    val minor = Option.when(context.viewerIsActive &&
      context.current.turn.phase == Phase.Act && context.current.pending.isEmpty &&
      context.current.walkerPending.isEmpty)(minorActionsProjection(context))
    val ordinaryAct = context.viewerIsActive &&
      context.current.turn.phase == Phase.Act && context.current.pending.isEmpty &&
      context.current.walkerPending.isEmpty
    val travelFacts = if (ordinaryAct) travelCandidates(context)
      else Vector.empty[(SiteId, Int)]
    LegalProjection(
      controls(context, minor, projectedPhasePowers),
      travelFacts.map { case (site, cost) =>
        LegalTravelDestinationProjection(site.value, cost)
      },
      if (ordinaryAct) legalSearch(context) else Vector.empty,
      if (ordinaryAct) boardTargetActions(context, travelFacts)
      else Vector.empty,
      minor)
  }

  private def controls(context: ScopedProjectionContext,
      minor: Option[MinorActionsProjection],
      projectedPhasePowers: Vector[PhasePowerProjection]): Vector[String] = {
    val current = context.current
    val active = context.active
    if (current.result.nonEmpty) Vector.empty
    else if (current.walkerPending.nonEmpty) walkerControls(context)
    else current.pending match {
      case Some(n: PendingProcedure.Negotiation)
          if context.viewer.exists(n.participants.contains) =>
        Vector("replaceNegotiationTerms", "declineNegotiation") ++
          context.viewer.filter(oathdigital.gameplay.actions.Negotiation
            .canAccept(context.ready, n, _)).map(_ => "acceptNegotiation")
      case Some(_: PendingProcedure.Negotiation) => Vector.empty
      case Some(c: PendingProcedure.Campaign) if !c.defenderPlansFinished &&
          context.viewer.contains(CampaignRules.planDecisionOwner(c)) =>
        Vector("chooseCampaignPlan", "finishCampaignPlans")
      case Some(r: PendingProcedure.CampaignRaidRelocation)
          if context.viewer.contains(r.actor) => Vector("relocateCampaignRaidPawn")
      case _ if !context.viewerIsActive => Vector.empty
      case Some(c: PendingProcedure.Campaign) if c.victorious.contains(true) =>
        Vector(if (c.kind == CampaignKind.Raid) "relocateCampaignRaidPawn"
          else "placeCampaignForce")
      case Some(c: PendingProcedure.Campaign) if !c.defenderPlansFinished => Vector.empty
      case Some(_: PendingProcedure.Campaign) => Vector("chooseCampaignSacrifice")
      case Some(_) => Vector.empty
      case None => current.turn.phase match {
        case Phase.Act => Vector(
          Option.when(BeginRestProcedure.validateBegin(catalog, Ready(context.ready), active.player).isRight)(
            "beginRest"),
          Option.when(active.pawnSite.exists(_ =>
            recoverEligible(context, active)))("beginRecover"),
          Option.when(active.pawnSite.exists(site => ForgeRules.validate(
            catalog, context.ready, active, site).isRight))("beginForge"),
          Option.when(musterStartable(context))("beginMuster"),
          Option.when(tradeStartable(context, TradeResource.Favor))(
            "beginTradeFavor"),
          Option.when(tradeStartable(context, TradeResource.Secret))(
            "beginTradeSecret"),
          Option.when(challengeStartable(context))("beginChallenge"),
          Option.when(placeBannerResourceStartable(context))("placeBannerResource"),
          Option.when(active.advisers.exists(presentation.adviserOrientation(_) ==
            Orientation.FaceDown))("facedownAdviserMinorAction"),
          Option.when(context.activeSite.exists(_.relics.nonEmpty))("peekSiteRelics"),
          Option.when(active.relics.exists(_.orientation == Orientation.FaceDown))(
            "revealOwnedRelic"),
          Option.when(minor.exists(value => value.maxBoardToSite > 0 ||
            value.maxSiteToBoard > 0))("moveWarbands"),
          Option.when(negotiationStartable(context))("beginNegotiation")
        ).flatten ++ phasePowers.controls(projectedPhasePowers)
        case Phase.Rest => phasePowers.controls(projectedPhasePowers) :+ "finishRest"
        case Phase.RoundEnd | Phase.WarExhaustion => Vector.empty
        case Phase.Wake =>
          takeableResources(context).map(takeControl) ++
            phasePowers.controls(projectedPhasePowers) :+ "endWake"
      }
    }
  }

  /** Whether `active` can start Recover at their current site. Relic
    * availability is deliberately irrelevant: a successful empty-site
    * Recover is a legal wasted action.
    */
  private def recoverEligible(context: ScopedProjectionContext,
      active: PlayerState): Boolean =
    RecoverProcedure.build(catalog, context.ready, active.player).isRight

  /** While a generic-walker procedure is parked, no other Act control is legal
    * (`GameApplicationService`/`OathLifecycle` reject every legacy command
    * for exactly this reason — see Task 6 command-exclusivity ruling); the
    * only legal controls are answering the parked position itself, visible
    * only to the player the parked position awaits.
    */
  private def walkerControls(context: ScopedProjectionContext): Vector[String] =
    walkerDecisions.project(context).toVector.map {
      case decision if decision.kind == "roll" => "rollWalker"
      case _ => "resolveWalkerDecision"
    }

  private def legalSearch(context: ScopedProjectionContext) =
    SearchProcedure.legalSources(catalog, context.ready,
      context.active.player, WalkerPowers.selected(walkerPowerCatalog,
        Vector.empty)).map {
      case (SearchSource.WorldDeck, cost) =>
        LegalSearchSourceProjection("world", None, cost)
      case (SearchSource.RegionalDiscard(region), cost) =>
        LegalSearchSourceProjection("regional-discard", Some(region.key), cost)
    }

  private def minorActionsProjection(context: ScopedProjectionContext) = {
    val active = context.active
    val facedown = active.advisers.collect {
      case d: DenizenState if d.orientation == Orientation.FaceDown => d.id: WorldCardId
      case v: VisionState if v.orientation == Orientation.FaceDown => v.id: WorldCardId
    }
    val ruled = context.activeSite.exists(site => SiteRule.ruledBy(site.forces,
      context.current.players, active.player).getOrElse(false))
    val siteWarbands = context.activeSite.flatMap(_.forces match {
      case SiteForces.Occupied(ForceKind.Exile(lineage), count)
          if lineage == active.lineage => Some(count)
      case _ => None
    }).getOrElse(0)
    MinorActionsProjection(facedown.map { id => MinorAdviserProjection(
      presentation.cardDetails(id, Some(Orientation.FaceDown), hidden = false),
      Vector.empty)
    }, context.activeSite.exists(_.relics.nonEmpty), active.relics.filter(
      _.orientation == Orientation.FaceDown).map(r => presentation.cardDetails(r.id,
      Some(r.orientation), hidden = false)), active.pawnSite.map(_.value),
      if (ruled) active.board.warbands else 0, math.max(0, siteWarbands - 1))
  }

  private def boardTargetActions(context: ScopedProjectionContext,
      travelFacts: Vector[(SiteId, Int)]) = {
    val ready = context.ready; val player = context.active
    val travel = travelFacts.map {
      case (site, cost) => BoardTargetCandidateProjection(
        BoardTargetRefProjection.Site(site.value), presentation.siteLabel(site),
        Vector(s"$cost Supply"))
    }
    val campaign = CampaignRules.legalTargets(catalog, ready, player.player).map(site =>
      BoardTargetCandidateProjection(BoardTargetRefProjection.Site(site.value),
        presentation.siteLabel(site), Vector(
          s"${oathdigital.gameplay.actions.Campaign.SupplyCost} Supply",
          s"Choose ${oathdigital.gameplay.actions.Campaign.MinimumForce} to " +
            s"${player.board.warbands} board warbands")))
    val raid = CampaignRules.legalRaidTargets(catalog, ready, player.player).map {
      case CampaignRaidTarget.Pawn(defender) => BoardTargetCandidateProjection(
        BoardTargetRefProjection.PlayerPawn(defender.value),
        s"${presentation.safeLabel(defender.value)} pawn", Vector("Required Raid target"))
      case CampaignRaidTarget.Relic(defender, relic) => BoardTargetCandidateProjection(
        BoardTargetRefProjection.PlayerRelic(defender.value, relic.value),
        presentation.relicLabel(relic))
      case CampaignRaidTarget.Banner(defender, banner) =>
        val key = banner.key
        BoardTargetCandidateProjection(BoardTargetRefProjection.PlayerBanner(
          defender.value, key), presentation.safeLabel(key))
    }
    val negotiators = oathdigital.gameplay.actions.Negotiation
      .legalParticipants(ready, player.player).map(candidate =>
        BoardTargetCandidateProjection(BoardTargetRefProjection.Player(candidate.value),
          presentation.safeLabel(candidate.value), Vector("Co-located negotiator")))
    Vector(
      selection("travel", "Choose a Travel destination", travel),
      selection("campaign-conquest", "Choose optional same-ruler Conquest sites",
        campaign, Option.when(campaign.nonEmpty)(BoardTargetFormationProjection(
          oathdigital.gameplay.actions.Campaign.MinimumForce, player.board.warbands,
          player.board.warbands, oathdigital.gameplay.actions.Campaign.SupplyCost)),
        1, campaign.size, campaign.headOption.map(_.target).toVector),
      selection("campaign-raid", "Choose Raid targets", raid,
        Option.when(raid.nonEmpty)(BoardTargetFormationProjection(
          oathdigital.gameplay.actions.Campaign.MinimumForce, player.board.warbands,
          player.board.warbands, oathdigital.gameplay.actions.Campaign.SupplyCost)),
        Option.when(raid.nonEmpty)(1).getOrElse(0), raid.size,
        raid.headOption.map(_.target).toVector),
      selection("negotiation", "Choose one or more co-located negotiators",
        negotiators, minimum = 1, maximum = negotiators.size)).flatten
  }

  private def selection(kind: String, prompt: String,
      candidates: Vector[BoardTargetCandidateProjection],
      formation: Option[BoardTargetFormationProjection] = None,
      minimum: Int = 1, maximum: Int = 1,
      requiredTargets: Vector[BoardTargetRefProjection] = Vector.empty,
      explicitConfirm: Boolean = false) =
    Option.when(candidates.nonEmpty)(BoardTargetActionProjection(kind, prompt,
      minimum, maximum, autoActivate = false, candidates, formation, requiredTargets,
      explicitConfirm = explicitConfirm))
}
