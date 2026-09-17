package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.WakeResource
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.actions.{BannerRules, CampaignRules, ChallengeRules,
  Economy, ForgeRules, VisionRules, Visions}
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
    LegalProjection(
      controls(context, minor, projectedPhasePowers),
      if (ordinaryAct) travelCandidates(context).map { case (site, cost) =>
        LegalTravelDestinationProjection(site.value, cost)
      } else Vector.empty,
      if (ordinaryAct) legalSearch(context) else Vector.empty,
      if (ordinaryAct) Economy.legalMuster(catalog, context.ready,
        context.active).map(result => LegalMusterProjection(result.target.kind,
          result.target.id.value, economyLabel(result.target), result.suit.key,
          result.supplySpent, result.warbandsGained)) else Vector.empty,
      if (ordinaryAct) Economy.legalTrades(catalog, context.ready,
        context.active).map(result => LegalTradeProjection(result.target.kind,
          result.target.id.value, economyLabel(result.target), result.suit.key,
          result.resource match {
            case oathdigital.gameplay.TradeResource.Favor => "favor"
            case oathdigital.gameplay.TradeResource.Secret => "secret"
          }, result.supplySpent, result.gained)) else Vector.empty,
      context.current.pending match {
        case Some(p: PendingProcedure.Conspiracy) if p.awaitingTarget &&
            context.viewer.contains(p.actor) =>
          Vector(conspiracyTargetAction(context.ready, p))
        case _ if ordinaryAct => boardTargetActions(context)
        case _ => Vector.empty
      },
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
      case Some(p: PendingProcedure.Conspiracy) if p.awaitingTarget &&
          context.viewer.contains(p.actor) => Vector("playConspiracy")
      case Some(_: PendingProcedure.Conspiracy) => Vector.empty
      case Some(c: PendingProcedure.Campaign) if !c.defenderPlansFinished &&
          context.viewer.contains(CampaignRules.planDecisionOwner(c)) =>
        Vector("chooseCampaignPlan", "finishCampaignPlans")
      case Some(r: PendingProcedure.CampaignRaidRelocation)
          if context.viewer.contains(r.actor) => Vector("relocateCampaignRaidPawn")
      case _ if !context.viewerIsActive => Vector.empty
      case Some(c: PendingProcedure.Challenge) if context.viewer.contains(c.actor) =>
        if (c.remainingRibbonResources == 0) Vector("completeChallenge")
        else Vector("chooseChallengeSecretSite")
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
          Option.when(ChallengeRules.legal(catalog, context.ready,
            active.player).nonEmpty)("beginChallenge"),
          Option.when(Banner.all.exists(b => BannerRules.holder(current, b)
            .contains(active.player) && BannerRules.playerResources(active, b) > 0))(
            "placeBannerResource"),
          Option.when(active.advisers.exists(presentation.adviserOrientation(_) ==
            Orientation.FaceDown))("facedownAdviserMinorAction"),
          Option.when(active.advisers.exists {
            case VisionState(id, Orientation.FaceDown) =>
              Visions.canReveal(catalog, context.ready, active.player, id)
            case _ => false
          })("revealVision"),
          Option.when(active.advisers.exists {
            case VisionState(id, Orientation.FaceDown) => id == VisionRules.Conspiracy
            case _ => false
          } && Visions.canPlayConspiracy(catalog, context.ready, active.player))(
            "playConspiracy"),
          Option.when(context.activeSite.exists(_.relics.nonEmpty))("peekSiteRelics"),
          Option.when(active.relics.exists(_.orientation == Orientation.FaceDown))(
            "revealOwnedRelic"),
          Option.when(minor.exists(value => value.maxBoardToSite > 0 ||
            value.maxSiteToBoard > 0))("moveWarbands"),
          Option.when(oathdigital.gameplay.actions.Negotiation
            .legalParticipants(context.ready, active.player).nonEmpty)("beginNegotiation")
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

  private def boardTargetActions(context: ScopedProjectionContext) = {
    val ready = context.ready; val player = context.active
    val travel = travelCandidates(context).map {
      case (site, cost) => BoardTargetCandidateProjection(
        BoardTargetRefProjection.Site(site.value), presentation.siteLabel(site),
        Vector(s"$cost Supply"))
    }
    val musters = Economy.legalMuster(catalog, ready, player).map(result =>
      economyCandidate(result.target, result.source,
        Vector(s"${result.supplySpent} Supply", s"+${result.warbandsGained} warbands")))
    val trades = Economy.legalTrades(catalog, ready, player)
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
        val key = banner match {
          case CampaignBanner.PeoplesFavor => "peoples-favor"
          case CampaignBanner.DarkestSecret => "darkest-secret"
        }
        BoardTargetCandidateProjection(BoardTargetRefProjection.PlayerBanner(
          defender.value, key), presentation.safeLabel(key))
    }
    val favor = trades.filter(_.resource == oathdigital.gameplay.TradeResource.Favor)
      .map(result => economyCandidate(result.target, result.source,
        Vector(s"${result.supplySpent} Supply", s"+${result.gained} favor")))
    val secret = trades.filter(_.resource == oathdigital.gameplay.TradeResource.Secret)
      .map(result => economyCandidate(result.target, result.source,
        Vector(s"${result.supplySpent} Supply", s"+${result.gained} secrets")))
    val challenges = ChallengeRules.legal(catalog, ready, player.player).map { banner =>
      val holder = BannerRules.holder(ready.game.current, banner)
      BoardTargetCandidateProjection(BoardTargetRefProjection.PlayerBanner(
        holder.map(_.value).getOrElse("shared-bank"), banner.key),
        presentation.safeLabel(banner.key), Vector("1 Supply",
          s"Currently ${BannerRules.resources(ready.game.current, banner)} resources"))
    }
    val negotiators = oathdigital.gameplay.actions.Negotiation
      .legalParticipants(ready, player.player).map(candidate =>
        BoardTargetCandidateProjection(BoardTargetRefProjection.Player(candidate.value),
          presentation.safeLabel(candidate.value), Vector("Co-located negotiator")))
    val visions = player.advisers.collect {
      case VisionState(id, Orientation.FaceDown) if VisionRules.trueGoal(id).nonEmpty =>
        BoardTargetCandidateProjection(BoardTargetRefProjection.PlayerAdviser(
          player.player.value, id.value), presentation.safeLabel(id.value))
    }
    val hasConspiracy = player.advisers.exists {
      case VisionState(id, Orientation.FaceDown) => id == VisionRules.Conspiracy
      case _ => false
    }
    val conspiracy = conspiracyTargetCandidates(ready, player.player)
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
      selection("challenge", "Choose a banner to Challenge", challenges),
      selection("negotiation", "Choose one or more co-located negotiators",
        negotiators, minimum = 1, maximum = negotiators.size),
      selection("reveal-vision", "Choose a Vision to reveal", visions),
      Option.when(hasConspiracy)(BoardTargetActionProjection("play-conspiracy",
        if (conspiracy.isEmpty) "Play Conspiracy" else "Choose an enemy asset for Conspiracy",
        if (conspiracy.isEmpty) 0 else 1, if (conspiracy.isEmpty) 0 else 1,
        autoActivate = false, conspiracy)),
      selection("muster", "Choose a card to Muster from", musters,
        explicitConfirm = true),
      selection("trade-favor", "Choose a card to Trade for favor", favor,
        explicitConfirm = true),
      selection("trade-secret", "Choose a card to Trade for secrets", secret,
        explicitConfirm = true)).flatten
  }

  private def conspiracyTargetAction(ready: oathdigital.gameplay.ReadyGame,
      pending: PendingProcedure.Conspiracy) = {
    val targets = conspiracyTargetCandidates(ready, pending.actor)
    BoardTargetActionProjection("play-conspiracy",
      if (targets.isEmpty) "Play Conspiracy" else "Choose an enemy asset for Conspiracy",
      if (targets.isEmpty) 0 else 1, if (targets.isEmpty) 0 else 1,
      autoActivate = true, targets, decisionId = Some(pending.decision.value))
  }
  private def conspiracyTargetCandidates(ready: oathdigital.gameplay.ReadyGame,
      actor: PlayerId) = Visions.legalTargetRefs(ready, actor).map {
    case ConspiracyTargetRef.RelicSlot(owner, slot) => BoardTargetCandidateProjection(
      BoardTargetRefProjection.PlayerRelic(owner.value, slot.toString),
      s"${presentation.safeLabel(owner.value)} facedown relic")
    case ConspiracyTargetRef.Banner(owner, banner) => BoardTargetCandidateProjection(
      BoardTargetRefProjection.PlayerBanner(owner.value, banner.key),
      s"${presentation.safeLabel(owner.value)} ${presentation.safeLabel(banner.key)}")
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
  private def economyLabel(target: EconomyTargetRef) = target match {
    case EconomyTargetRef.Denizen(id) => presentation.denizenLabel(id)
    case EconomyTargetRef.Edifice(id) => presentation.edificeLabel(id, EdificeSide.Ruined)
  }
  private def economyCandidate(target: EconomyTargetRef,
      source: oathdigital.gameplay.RuleSourceRef, details: Vector[String]) = {
    val site = source match {
      case oathdigital.gameplay.RuleSourceRef.SiteCard(id, _) => id
      case oathdigital.gameplay.RuleSourceRef.Edifice(id, _) => id
      case other => throw new IllegalStateException(
        s"Economy candidate has non-site source $other")
    }
    BoardTargetCandidateProjection(BoardTargetRefProjection.SiteCard(site.value,
      target.kind, target.id.value), economyLabel(target), details)
  }
}
