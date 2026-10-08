package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.actions.ForgeRules
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.actions.negotiation.NegotiationProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.phases.wake.TakeWealthProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.walker.{FollowUpStarted, ModifierOffers,
  ProcedureWalker, WalkerPowers}
import oathdigital.gameplay.phases.rest.BeginRestProcedure
import oathdigital.model._
import oathdigital.protocol.projection._

private[application] final class LegalActionProjector(
    catalog: ExecutableCatalog,
    presentation: GamePresentationProjector,
    walkerDecisions: WalkerDecisionProjector,
    phasePowers: PhasePowerProjector
):
  def this(catalog: ExecutableCatalog,
      presentation: GamePresentationProjector,
      walkerDecisions: WalkerDecisionProjector) =
    this(catalog, presentation, walkerDecisions,
      new PhasePowerProjector(catalog, walkerDecisions))

  /** The walker power catalog a start control is costed against -- the same
    * full catalog `OathRules` is constructed with. Most controls select it
    * down to the automatic set, since a projection is built before the viewer
    * has chosen any modifier.
    */
  private val walkerPowerCatalog = WalkerPowerCatalog.default(catalog)

  /** Whether `procedure` could start under some modifier selection the
    * viewer could make, no selection included: a start only a modifier makes
    * affordable is still offered. `starts` is the procedure's own dry run. */
  private def startableWithModifiers(context: ScopedProjectionContext,
      procedure: ProcedureRef)(starts: WalkerPowers => Boolean): Boolean =
    ModifierOffers.selections(walkerPowerCatalog, context.ready,
      context.active.player, procedure).exists(starts)

  /** Whether a Travel could start now, asked of the procedure that owns it. */
  private def travelStartable(context: ScopedProjectionContext): Boolean =
    startableWithModifiers(context, ActionRef.Travel)(powers =>
      TravelProcedure.startable(catalog, context.ready, context.active.player,
        powers))

  /** Whether a Search could start now, asked of the procedure that owns it. */
  private def searchStartable(context: ScopedProjectionContext): Boolean =
    startableWithModifiers(context, ActionRef.Search)(powers =>
      SearchProcedure.startable(catalog, context.ready, context.active.player,
        powers))

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
    startableWithModifiers(context, ActionRef.Muster)(powers =>
      MusterProcedure.startOptions(catalog, context.ready,
        context.active.player, powers).exists(_.outcome.isRight))

  private def tradeStartable(context: ScopedProjectionContext,
      resource: TradeResource): Boolean =
    startableWithModifiers(context, ActionRef.Trade)(powers =>
      TradeProcedure.startOptions(catalog, context.ready,
        context.active.player, resource, powers).exists(_.outcome.isRight))

  /** Whether Challenge or Place Banner Resource could start now: the same
    * dry run a start performs, asked of the procedures that own them.
    */
  private def challengeStartable(context: ScopedProjectionContext): Boolean =
    startableWithModifiers(context, ActionRef.Challenge)(powers =>
      ChallengeProcedure.startable(catalog, context.ready,
        context.active.player, powers))

  private def campaignStartable(context: ScopedProjectionContext): Boolean =
    startableWithModifiers(context, ActionRef.Campaign)(powers =>
      CampaignProcedure.startable(catalog, context.ready,
        context.active.player, powers))

  private def negotiationStartable(context: ScopedProjectionContext): Boolean =
    startableWithModifiers(context, ActionRef.Negotiation)(powers =>
      NegotiationProcedure.startable(catalog, context.ready,
        context.active.player, powers))

  private def placeBannerResourceStartable(
      context: ScopedProjectionContext): Boolean =
    startableWithModifiers(context, ActionRef.PlaceBannerResource)(powers =>
      PlaceBannerResourceProcedure.startable(catalog, context.ready,
        context.active.player, powers))

  /** The control a resource is offered as. A name the client binds a button
    * to is presentation, which is why this mapping is here and the question
    * of whether the resource is takeable at all is not.
    */
  private def takeControl(resource: WakeResource): String = resource match
    case WakeResource.Favor => "takeFavor"
    case WakeResource.Secret => "takeSecret"

  def project(context: ScopedProjectionContext): LegalProjection =
    project(context, phasePowers.project(context))

  def project(context: ScopedProjectionContext,
      projectedPhasePowers: Vector[PhasePowerProjection]): LegalProjection =
    val minor = Option.when(context.viewerIsActive &&
      context.current.turn.phase == Phase.Act &&
      context.current.walkerPending.isEmpty &&
      context.current.followUps.isEmpty)(minorActionsProjection(context))
    val followUp = followUpProjection(context)
    LegalProjection(controls(context, minor, projectedPhasePowers, followUp),
      minor, followUp)

  private def controls(context: ScopedProjectionContext,
      minor: Option[MinorActionsProjection],
      projectedPhasePowers: Vector[PhasePowerProjection],
      followUp: Option[FollowUpProjection]): Vector[String] =
    val current = context.current
    val active = context.active
    if current.result.nonEmpty then Vector.empty
    else if current.walkerPending.nonEmpty then walkerControls(context)
    // Branches on the queue itself: should the head's projection fail, its
    // player can still skip it, and no control the service refuses is shown.
    else if current.followUps.nonEmpty then
      if context.viewer.contains(current.followUps.head.player) then
        followUp.fold(Vector("skipFollowUp"))(_ =>
          Vector("beginFollowUp", "skipFollowUp"))
      else Vector.empty
    else if !context.viewerIsActive then Vector.empty
    else current.turn.phase match
        case Phase.Act => Vector(
          Option.when(searchStartable(context))("beginSearch"),
          Option.when(BeginRestProcedure.validateBegin(catalog, Ready(context.ready), active.player).isRight)(
            "beginRest"),
          Option.when(active.pawnSite.exists(_ =>
            recoverEligible(context, active)))("beginRecover"),
          Option.when(active.pawnSite.exists(site => ForgeRules.validate(
            catalog, context.ready, active, site).isRight))("beginForge"),
          Option.when(travelStartable(context))("beginTravel"),
          Option.when(musterStartable(context))("beginMuster"),
          Option.when(tradeStartable(context, TradeResource.Favor))(
            "beginTradeFavor"),
          Option.when(tradeStartable(context, TradeResource.Secret))(
            "beginTradeSecret"),
          Option.when(challengeStartable(context))("beginChallenge"),
          Option.when(campaignStartable(context))("beginCampaign"),
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
        case Phase.Setup | Phase.RoundEnd | Phase.WarExhaustion => Vector.empty
        case Phase.Wake =>
          takeableResources(context).map(takeControl) ++
            phasePowers.controls(projectedPhasePowers) :+ "endWake"

  /** The queue's head as every viewer sees it (follow-up actions design,
    * "Projection and UI"), while no walker is pending and the game is not
    * over. Whether it can start is asked of the state its start would
    * leave, so the waiver and the Act-phase exemption apply as they would.
    */
  private def followUpProjection(context: ScopedProjectionContext)
      : Option[FollowUpProjection] =
    val current = context.current
    for
      head <- current.followUps.headOption
      if current.walkerPending.isEmpty && current.result.isEmpty
      started <- ProcedureWalker.applyRecorded(Ready(context.ready),
        FollowUpStarted(head)).toOption.collect { case Ready(ready) => ready }
      startable = followUpStartable(context.copy(ready = started), head.action)
    yield FollowUpProjection(head.player.value, head.action.key,
      actionLabel(head.action), sourceLabel(context.ready, head.source),
      startable,
      Option.when(!startable)(LegalActionProjector.followUpReason(
        head.action, Option.when(head.action == ActionRef.Campaign)(
          CampaignProcedure.refusal(catalog, started, head.player,
            WalkerPowers.selected(walkerPowerCatalog, Vector.empty))).flatten)),
      current.followUps.tail.map(entry => actionLabel(entry.action)))

  /** Whether the queued `action` could start, by the same dry run as its
    * own start control. */
  private def followUpStartable(context: ScopedProjectionContext,
      action: ActionRef): Boolean = action match
    case ActionRef.Campaign => campaignStartable(context)
    case ActionRef.Travel => travelStartable(context)
    case ActionRef.Search => searchStartable(context)
    case ActionRef.Muster => musterStartable(context)
    case ActionRef.Challenge => challengeStartable(context)
    case ActionRef.Recover => recoverEligible(context, context.active)
    case _ => false

  /** "Campaign", "Travel": an action as a button and the Game Log name it. */
  private def actionLabel(action: ActionRef): String = action.key.capitalize

  private def sourceLabel(ready: ReadyGame, source: PowerSourceRef): String =
    source match
      case PowerSourceRef.Card(card) => presentation.cardLabel(ready, card)
      case PowerSourceRef.Site(site) => presentation.siteLabel(site)
      case PowerSourceRef.Banner(banner) => BannerRules.displayName(banner)

  /** Whether `active` can start Recover at their current site, Supply cost
    * included. Relic availability is deliberately irrelevant: a successful
    * empty-site Recover is a legal wasted action.
    */
  private def recoverEligible(context: ScopedProjectionContext,
      active: PlayerState): Boolean =
    startableWithModifiers(context, ActionRef.Recover)(powers =>
      RecoverProcedure.startable(catalog, context.ready, active.player, powers))

  /** While a generic-walker procedure is parked, no other Act control is legal
    * (`GameApplicationService`/`OathLifecycle` reject every legacy command
    * for exactly this reason — see Task 6 command-exclusivity ruling); the
    * only legal controls are answering the parked position itself, visible
    * only to the player the parked position awaits.
    */
  private def walkerControls(context: ScopedProjectionContext): Vector[String] =
    walkerDecisions.project(context).toVector.map:
      case decision if decision.kind == "roll" => "rollWalker"
      case _ => "resolveWalkerDecision"

  private def minorActionsProjection(context: ScopedProjectionContext) =
    val active = context.active
    val facedown = active.advisers.collect:
      case d: DenizenState if d.orientation == Orientation.FaceDown => d.id: WorldCardId
      case v: VisionState if v.orientation == Orientation.FaceDown => v.id: WorldCardId
    val ruled = context.activeSite.exists(site => SiteRule.ruledBy(site.forces,
      context.current.players, active.player).getOrElse(false))
    val siteWarbands = context.activeSite.flatMap(_.forces match {
      case SiteForces.Occupied(ForceKind.Exile(lineage), count)
          if lineage == active.lineage => Some(count)
      case _ => None
    }).getOrElse(0)
    MinorActionsProjection(facedown.map { id => MinorAdviserProjection(
      presentation.cardDetails(id, Some(Orientation.FaceDown), hidden = false))
    }, context.activeSite.exists(_.relics.nonEmpty), active.relics.filter(
      _.orientation == Orientation.FaceDown).map(r => presentation.cardDetails(r.id,
      Some(r.orientation), hidden = false)), active.pawnSite.map(_.value),
      if ruled then active.board.warbands else 0, math.max(0, siteWarbands - 1))

object LegalActionProjector:
  /** Why a queued action cannot start (follow-up actions design): each
    * action declares its own. A Campaign shows its refusal's text, or names
    * the missing target. */
  private[application] def followUpReason(action: ActionRef,
      campaignRefusal: Option[OathViolation]): String = action match
    case ActionRef.Campaign => campaignRefusal match
      case Some(OathViolation.CampaignUnavailable(detail))
          if detail == CampaignProcedure.NothingToAttack =>
        "No site to conquer and no enemy pawn to raid"
      case Some(OathViolation.CampaignUnavailable(detail)) => detail
      case _ => "Cannot start now"
    case ActionRef.Travel => "No site to travel to"
    case _ => "Cannot start now"
