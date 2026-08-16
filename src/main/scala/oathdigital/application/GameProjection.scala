package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.setup.OathState.{InProgress, NoGame, Ready}
import oathdigital.setup.FirstGameParticipant
import oathdigital.setup.ReadyGame
import oathdigital.setup.WakeResource
import oathdigital.gameplay.TakeWealthRules
import oathdigital.gameplay.actions.{CampaignRules, Economy, RecoverRules, SearchRules, TravelRules}
import oathdigital.gameplay.phases.Rest

final case class SetupPlayerProjection(
    playerId: String,
    displayName: String,
    role: String,
    colorToken: String
)
final case class CardDetailsProjection(
    cardId: String,
    cardKind: String,
    name: String,
    suit: Option[String] = None,
    restrictions: Option[String] = None,
    rulesText: Option[String] = None,
    orientation: Option[String] = None,
    side: Option[String] = None,
    favor: Int = 0,
    secrets: Int = 0,
    relicValue: Option[Int] = None,
    defense: Option[Int] = None,
    hidden: Boolean = false
)
final case class SiteCardProjection(
    cardId: String,
    label: String,
    details: Option[CardDetailsProjection] = None)
final case class SiteRelicsProjection(facedownCount: Int)
final case class ForgeCostProjection(favor: Int, secrets: Int)
final case class SiteForcesProjection(
    forceKind: String,
    count: Int,
    rulerKind: String,
    rulerPlayerId: Option[String],
    label: String,
    colorToken: String
)
final case class SetupSiteProjection(
    siteId: String,
    label: String,
    looseFavor: Int,
    looseSecrets: Int,
    denizenCapacity: Int,
    relicCapacity: Int,
    denizens: Vector[SiteCardProjection],
    relics: SiteRelicsProjection,
    defense: Int = 0,
    recoverDifficulty: Option[Int] = None,
    forgeCost: Option[ForgeCostProjection] = None,
    powers: Vector[SitePowerProjection] = Vector.empty,
    forces: Option[SiteForcesProjection] = None
)
final case class SetupRegionProjection(
    regionId: String,
    sites: Vector[SetupSiteProjection],
    discardCount: Int = 0,
    discardTopCardKind: Option[String] = None
)
final case class SitePowerProjection(kind: String, label: String, description: Option[String])
final case class PawnLocationProjection(playerId: String, siteId: String)
final case class ActivePlayerResourcesProjection(
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    supply: Int
)
final case class CurrentSiteResourcesProjection(
    siteId: String,
    favor: Int,
    secrets: Int
)
final case class LegalTravelDestinationProjection(siteId: String, supplyCost: Int)
final case class LegalSearchSourceProjection(kind: String, region: Option[String], supplyCost: Int)
final case class LegalMusterProjection(
    targetKind: String, targetId: String, label: String, suit: String,
    supplyCost: Int, warbandsGained: Int)
final case class LegalTradeProjection(
    targetKind: String, targetId: String, label: String, suit: String,
    resource: String, supplyCost: Int, gained: Int)
sealed trait BoardTargetRefProjection extends Product with Serializable
object BoardTargetRefProjection {
  final case class Site(siteId: String) extends BoardTargetRefProjection
  final case class SiteCard(siteId: String, cardKind: String, cardId: String)
      extends BoardTargetRefProjection
  final case class PlayerAdviser(playerId: String, cardId: String)
      extends BoardTargetRefProjection
  final case class PlayerRelic(playerId: String, relicId: String)
      extends BoardTargetRefProjection
}
final case class BoardTargetCandidateProjection(
    target: BoardTargetRefProjection,
    label: String,
    details: Vector[String] = Vector.empty
)
final case class BoardTargetFormationProjection(
    minimumForce: Int,
    maximumForce: Int,
    availableWarbands: Int,
    supplyCost: Int
) {
  require(minimumForce >= 0, "formation minimum must be non-negative")
  require(maximumForce >= minimumForce,
    "formation maximum must include minimum")
  require(maximumForce <= availableWarbands,
    "formation maximum cannot exceed available warbands")
  require(availableWarbands >= 0,
    "formation available warbands must be non-negative")
  require(supplyCost >= 0, "formation Supply cost must be non-negative")
}
final case class BoardTargetActionProjection(
    actionKind: String,
    prompt: String,
    minimum: Int,
    maximum: Int,
    autoActivate: Boolean,
    candidates: Vector[BoardTargetCandidateProjection],
    formation: Option[BoardTargetFormationProjection] = None,
    requiredTargets: Vector[BoardTargetRefProjection] = Vector.empty
) {
  require(minimum >= 0, "selection minimum must be non-negative")
  require(maximum >= minimum, "selection maximum must include minimum")
  require(maximum <= candidates.size,
    "selection maximum cannot exceed authorized candidates")
  require(requiredTargets.distinct.size == requiredTargets.size,
    "required selection targets must be distinct")
  require(requiredTargets.forall(required => candidates.exists(_.target == required)),
    "required selection targets must be authorized candidates")
  require(requiredTargets.size <= minimum,
    "required selection targets must fit within the minimum")
}
final case class CardResolutionProjection(
    kind: String,
    orientation: Option[String] = None,
    replacementRequired: Boolean = false,
    replacementTargets: Vector[CardDetailsProjection] = Vector.empty)
final case class PendingCardDecisionProjection(
    decisionId: String,
    kind: String,
    actorPlayerId: String,
    prompt: String,
    instructions: Vector[String],
    cards: Vector[CardDetailsProjection],
    keepMinimum: Int,
    keepMaximum: Int,
    orderingRequired: Boolean,
    resolutionsByCard: Map[String, Vector[CardResolutionProjection]]
)
final case class RecoverProjection(
    decisionId: String, dice: Vector[String], shields: Int,
    difficulty: Int, supplySpent: Int, supplyRemaining: Int,
    canAddDice: Boolean, canStop: Boolean)
final case class CampaignProjection(
    decisionId: String, targetSiteIds: Vector[String], force: Int,
    plansFinished: Boolean, planChoices: Vector[CampaignPlanChoiceProjection],
    selectedPlans: Vector[CampaignPlanChoiceProjection],
    attackDice: Vector[String], attack: Int, skullLosses: Int,
    maxSacrifice: Int, sacrificed: Option[Int], defenseDice: Vector[String],
    defense: Option[Int], victorious: Option[Boolean], maxPlacement: Int,
    placementTargets: Vector[CampaignPlacementTargetProjection])
final case class CampaignPlacementTargetProjection(siteId: String, label: String)
final case class CampaignPlanChoiceProjection(
    kind: String, sourceKey: Option[String], playerId: Option[String],
    siteId: Option[String], cardId: Option[String], label: String,
    handlerId: Option[String], favorCost: Int, secretCost: Int,
    mechanicalResult: String)
final case class OathkeeperProjection(
    goal: String, holderPlayerId: Option[String], side: String,
    usurperLimited: Boolean, winnerPlayerId: Option[String])
final case class OathkeeperRecipientProjection(
    decisionId: String, actorPlayerId: String,
    candidatePlayerIds: Vector[String])
final case class PlayerBoardProjection(
    playerId: String,
    warbands: Int,
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    supply: Int,
    pawnSiteId: Option[String],
    advisers: Vector[CardDetailsProjection],
    relics: Vector[CardDetailsProjection],
    revealedVision: Option[CardDetailsProjection]
)

final case class GameProjection(
    gameId: String,
    nextSequence: Long,
    phase: String,
    activeParticipantId: Option[String],
    players: Vector[SetupPlayerProjection],
    world: Vector[SetupRegionProjection],
    pawnLocations: Vector[PawnLocationProjection],
    legalControls: Vector[String],
    ready: Boolean,
    completed: Boolean,
    activePlayerResources: Option[ActivePlayerResourcesProjection] = None,
    currentSiteResources: Option[CurrentSiteResourcesProjection] = None,
    actionSelectionOpen: Boolean = false,
    actionFamilies: Vector[String] = Vector.empty,
    legalTravelDestinations: Vector[LegalTravelDestinationProjection] =
      Vector.empty,
    legalSearchSources: Vector[LegalSearchSourceProjection] = Vector.empty,
    legalMusters: Vector[LegalMusterProjection] = Vector.empty,
    legalTrades: Vector[LegalTradeProjection] = Vector.empty,
    boardTargetActions: Vector[BoardTargetActionProjection] = Vector.empty,
    pendingCardDecision: Option[PendingCardDecisionProjection] = None,
    recover: Option[RecoverProjection] = None,
    campaign: Option[CampaignProjection] = None,
    worldDeckCount: Int = 0,
    worldDeckTopCardKind: Option[String] = None,
    playerBoards: Vector[PlayerBoardProjection] = Vector.empty,
    oathkeeper: Option[OathkeeperProjection] = None,
    oathkeeperRecipient: Option[OathkeeperRecipientProjection] = None
)

final class GameProjector(catalog: ExecutableCatalog) {
  private val siteNames =
    catalog.sites.map(site => site.id -> site.name).toMap
  private val denizenNames =
    catalog.denizens.map(d => DenizenId(d.id.value) -> d.name).toMap
  private val edificeNames = catalog.edifices.map { edifice =>
    EdificeId(edifice.id.value) ->
      (edifice.intact.name -> edifice.ruined.name)
  }.toMap
  private val siteDefinitions = catalog.sites.map(site => site.id -> site).toMap

  def project(
      gameId: String,
      loaded: LoadedGame,
      requestingPlayer: PlayerId
  ): GameProjection = projectFor(gameId, loaded, Some(requestingPlayer))

  def projectPublic(
      gameId: String,
      loaded: LoadedGame
  ): GameProjection = projectFor(gameId, loaded, None)

  private def projectFor(
      gameId: String,
      loaded: LoadedGame,
      requestingPlayer: Option[PlayerId]
  ): GameProjection =
    loaded.state match {
      case NoGame =>
        GameProjection(
          gameId,
          loaded.nextSequence,
          "not-started",
          None,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          ready = false,
          completed = false
        )
      case progress: InProgress =>
        val order = turnOrder(progress.plan.participants,
          progress.plan.firstPlayer)
        val awaitingAdviser =
          progress.placements.size == progress.adviserChoices.size + 1
        val active =
          if (awaitingAdviser)
            order(progress.adviserChoices.size).playerId
          else order(progress.placements.size).playerId
        val phase =
          if (awaitingAdviser) "awaiting-adviser" else "awaiting-pawn"
        val controls =
          if (!requestingPlayer.contains(active)) Vector.empty
          else if (awaitingAdviser) Vector("chooseAdviser")
          else Vector("placePawn")
        val privateCards =
          if (requestingPlayer.contains(active) && awaitingAdviser) {
            val participantIndex = progress.plan.participants
              .indexWhere(participant =>
                requestingPlayer.contains(participant.playerId))
            progress.plan.denizenOrder
              .slice(6 + participantIndex * 3, 9 + participantIndex * 3)
              .map(id => cardDetails(id, Some(Orientation.FaceUp), hidden = false))
          } else Vector.empty
        val decision = Option.when(privateCards.nonEmpty)(
          PendingCardDecisionProjection(
            CardDecisionIds.startingAdviser(active,
              progress.adviserChoices.size).value,
            "starting-adviser", active.value,
            "Choose your starting adviser",
            Vector("Move exactly one adviser to Keep.",
              "The remaining candidates are discarded."),
            privateCards,
            1, 1, orderingRequired = false,
            privateCards.map(card => card.cardId ->
              Vector(CardResolutionProjection("starting-adviser"))).toMap))
        GameProjection(
          gameId,
          loaded.nextSequence,
          phase,
          Some(active.value),
          players(progress.plan.participants),
          world(progress.plan.orderedSites),
          progress.placements.map(placement =>
            PawnLocationProjection(
              placement.playerId.value,
              placement.siteId.value
            )),
          controls,
          ready = false,
          completed = false,
          pendingCardDecision = decision,
          boardTargetActions = Option.when(controls.contains("placePawn"))(
            BoardTargetActionProjection("place-pawn", "Choose a starting site",
              1, 1, autoActivate = true,
              progress.plan.orderedSites.map(site => BoardTargetCandidateProjection(
                BoardTargetRefProjection.Site(site.value),
                siteNames.getOrElse(site, safeLabel(site.value)))))).toVector
        )
      case Ready(value) =>
        val current = value.game.current
        val active = current.players.find(
          _.player == current.turn.activePlayer).get
        val site = active.pawnSite.flatMap(current.map.sites.get)
        val controls = if (current.result.nonEmpty) Vector.empty
          else current.pending match {
            case Some(p: PendingProcedure.OathkeeperRecipient)
                if requestingPlayer.contains(p.actor) =>
              Vector("chooseOathkeeperRecipient")
            case Some(_: PendingProcedure.OathkeeperRecipient) => Vector.empty
            case _ if !requestingPlayer.contains(active.player) => Vector.empty
            case Some(r: PendingProcedure.Recover) if !r.successful =>
              Vector(Option.when(active.board.supply.supply > 0)("addRecoverDice"),
                Some("stopRecover")).flatten
            case Some(_: PendingProcedure.Recover) => Vector.empty
            case Some(c: PendingProcedure.Campaign) if c.victorious.contains(true) =>
              Vector("placeCampaignForce")
            case Some(c: PendingProcedure.Campaign) if !c.plansFinished =>
              Vector("chooseCampaignPlan", "finishCampaignPlans")
            case Some(_: PendingProcedure.Campaign) => Vector("chooseCampaignSacrifice")
            case Some(_) => Vector.empty
            case None => current.turn.phase match {
              case Phase.Act =>
                Vector(
                  Option.when(Rest.validateBegin(Ready(value), active.player).isRight)(
                    "beginRest"),
                  Option.when(active.pawnSite.exists(siteId =>
                    RecoverRules.validate(catalog, value, active, siteId).isRight))(
                    "beginRecover")
                ).flatten
              case Phase.Rest => Vector("finishRest")
              case Phase.Wake =>
                val takeControls = active.pawnSite.toVector.flatMap { siteId =>
                  Vector(
                    Option.when(TakeWealthRules.validate(value, active, siteId,
                      WakeResource.Favor).isRight)("takeFavor"),
                    Option.when(TakeWealthRules.validate(value, active, siteId,
                      WakeResource.Secret).isRight)("takeSecret")
                  ).flatten
                }
                takeControls :+ "endWake"
            }
          }
        val setupPlayers = value.game.current.players.map { player =>
          SetupPlayerProjection(
            player.player.value,
            safeLabel(player.player.value),
            "exile",
            value.playerColors(player.player).value
          )
        }
        val pendingDecision = current.pending.collect {
          case search: PendingProcedure.Search
              if requestingPlayer.contains(search.actor) =>
            PendingCardDecisionProjection(
              search.decision.value, "search", search.actor.value,
              "Resolve Search",
              Vector("Move exactly one card to Keep.",
                "Remaining cards are discarded from left to right."),
              search.drawn.map(cardDetails(_, Some(Orientation.FaceUp), hidden = false)),
              1, 1, orderingRequired = true,
              search.drawn.map { card => card.value ->
                groupedResolutions(SearchRules.legalPlacements(
                  catalog, value, search, card))
              }.toMap)
          case recover: PendingProcedure.Recover
              if recover.successful && requestingPlayer.contains(recover.actor) =>
            val relics = current.map.sites(recover.site).relics
            PendingCardDecisionProjection(recover.decision.value, "recover-relic",
              recover.actor.value, "Choose a relic to recover",
              Vector("You privately peek at the site's relics.",
                "Take exactly one; it remains facedown."),
              relics.map(r => cardDetails(r.id, Some(Orientation.FaceDown), hidden = false)),
              1, 1, orderingRequired = false,
              relics.map(r => r.id.value -> Vector(
                CardResolutionProjection("take-facedown-relic"))).toMap)
        }
        val recoverProjection = current.pending.collect {
          case r: PendingProcedure.Recover if requestingPlayer.contains(r.actor) =>
            val remaining = current.players.find(_.player == r.actor).get.board.supply.supply
            RecoverProjection(r.decision.value, r.rolls.flatten.map(defenseFaceName),
              RecoverRules.score(r.rolls.flatten), r.difficulty, r.supplySpent,
              remaining, !r.successful && remaining > 0, !r.successful)
        }
        val campaignProjection = current.pending.collect {
          case c: PendingProcedure.Campaign if requestingPlayer.contains(c.actor) =>
            val remaining = c.force - c.skullLosses
            def planProjection(source: PendingProcedure.CampaignPlanSource) = source match {
              case PendingProcedure.CampaignPlanSource.Adviser(player, id) =>
                CampaignPlanChoiceProjection("adviser", Some(
                  PendingProcedure.CampaignPlanSource.Adviser(player, id).stableKey),
                  Some(player.value), None, Some(id.value),
                  denizenNames.getOrElse(id, "Outriders"), Some("denizen.outriders"),
                  0, 0, "Ignore all attack-roll skull losses")
              case PendingProcedure.CampaignPlanSource.SiteCard(site, id) =>
                CampaignPlanChoiceProjection("site-card", Some(
                  PendingProcedure.CampaignPlanSource.SiteCard(site, id).stableKey),
                  None, Some(site.value), Some(id.value),
                  denizenNames.getOrElse(id, "Outriders"), Some("denizen.outriders"),
                  0, 0, "Ignore all attack-roll skull losses")
              case PendingProcedure.CampaignPlanSource.Relic(player, id) =>
                CampaignPlanChoiceProjection("relic", Some(
                  PendingProcedure.CampaignPlanSource.Relic(player, id).stableKey),
                  Some(player.value), None, Some(id.value), "Brass Army",
                  Some("relic.brass-army"), 0, 1, "Add 4 attack dice")
            }
            val planChoices = CampaignRules.legalPlanChoices(catalog, value, c)
              .map(planProjection)
            val selectedPlans = c.plans.map(plan =>
              planProjection(plan.source).copy(favorCost = plan.favorCost,
                secretCost = plan.secretCost))
            CampaignProjection(c.decision.value, c.targetSites.map(_.value), c.force,
              c.plansFinished, if (c.plansFinished) Vector.empty else planChoices,
              selectedPlans,
              c.attackDice.map(attackFaceName), c.attack, c.skullLosses,
              remaining, c.sacrificed, c.defenseDice.map(defenseFaceName),
              c.defense, c.victorious,
              remaining - c.sacrificed.getOrElse(0),
              c.targetSites.map(site => CampaignPlacementTargetProjection(
                site.value, siteNames.getOrElse(site, safeLabel(site.value)))))
        }
        val oathkeeperRecipient = current.pending.collect {
          case p: PendingProcedure.OathkeeperRecipient
              if requestingPlayer.contains(p.actor) =>
            OathkeeperRecipientProjection(p.decision.value, p.actor.value,
              p.candidates.map(_.value))
        }
        GameProjection(
          gameId,
          loaded.nextSequence,
          if (current.result.nonEmpty) "game-over"
          else current.pending match {
            case Some(_: PendingProcedure.Search) if pendingDecision.nonEmpty =>
              "search-decision"
            case Some(_: PendingProcedure.Search) => "search-waiting"
            case Some(r: PendingProcedure.Recover) if requestingPlayer.contains(r.actor) && r.successful => "recover-relic-decision"
            case Some(_: PendingProcedure.Recover) if recoverProjection.nonEmpty => "recover-rolling"
            case Some(_: PendingProcedure.Recover) => "recover-waiting"
            case Some(_: PendingProcedure.Campaign) if campaignProjection.exists(_.victorious.contains(true)) => "campaign-placement"
            case Some(c: PendingProcedure.Campaign) if campaignProjection.nonEmpty && !c.plansFinished => "campaign-plan"
            case Some(_: PendingProcedure.Campaign) if campaignProjection.nonEmpty => "campaign-sacrifice"
            case Some(_: PendingProcedure.Campaign) => "campaign-waiting"
            case Some(_: PendingProcedure.OathkeeperRecipient)
                if oathkeeperRecipient.nonEmpty => "oathkeeper-recipient"
            case Some(_: PendingProcedure.OathkeeperRecipient) =>
              "oathkeeper-recipient-waiting"
            case _ => current.turn.phase match {
            case Phase.Wake => "wake"
            case Phase.Act => "act-action-selection"
            case Phase.Rest => "rest"
            }
          },
          Some(value.game.current.turn.activePlayer.value),
          setupPlayers,
          Vector(
            region("cradle", value.game.current.map.cradle,
              value.game.current.map.sites,
              value.game.current.commonCards.discard(Region.Cradle), Some(value)),
            region("provinces", value.game.current.map.provinces,
              value.game.current.map.sites,
              value.game.current.commonCards.discard(Region.Provinces), Some(value)),
            region("hinterland", value.game.current.map.hinterland,
              value.game.current.map.sites,
              value.game.current.commonCards.discard(Region.Hinterland), Some(value))
          ),
          value.game.current.players.flatMap(player =>
            player.pawnSite.map(site =>
              PawnLocationProjection(player.player.value, site.value))),
          controls,
          ready = true,
          completed = true,
          Some(ActivePlayerResourcesProjection(
            active.board.favor,
            active.board.faceUpSecrets,
            active.board.faceDownSecrets,
            active.board.supply.supply
          )),
          active.pawnSite.flatMap(siteId => site.map(state =>
            CurrentSiteResourcesProjection(
              siteId.value,
              state.tokens.favor,
              state.tokens.secrets
            ))),
          actionSelectionOpen = current.result.isEmpty &&
            current.turn.phase == Phase.Act && current.pending.isEmpty,
          actionFamilies =
            if (current.result.isEmpty && current.turn.phase == Phase.Act)
              Vector("Search", "Travel", "Campaign", "Muster", "Trade",
                "Forge", "Recover", "Challenge")
            else Vector.empty,
          legalTravelDestinations =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              TravelRules.legalDestinations(catalog, value, active).map {
                case (siteId, cost) =>
                  LegalTravelDestinationProjection(siteId.value, cost)
              }
            else Vector.empty,
          legalSearchSources =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              active.pawnSite.flatMap(current.map.regionOf).toVector.flatMap { origin =>
                Vector(SearchSource.WorldDeck,
                  SearchSource.RegionalDiscard(origin)).flatMap { source =>
                  SearchRules.cost(value, source, origin).toOption
                    .filter(_ <= active.board.supply.supply)
                    .filter(_ => SearchRules.draw(value, source, origin)
                      .exists(_.nonEmpty)).map { cost => source match {
                      case SearchSource.WorldDeck =>
                        LegalSearchSourceProjection("world", None, cost)
                      case SearchSource.RegionalDiscard(region) =>
                        LegalSearchSourceProjection(
                          "regional-discard", Some(region.key), cost)
                    }}
                }
              }
            else Vector.empty,
          legalMusters =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              Economy.legalMuster(catalog, value, active).map { result =>
                  LegalMusterProjection(result.target.kind, result.target.id.value,
                    economyLabel(result.target), result.suit.key, result.supplySpent,
                    result.warbandsGained)
              }
            else Vector.empty,
          legalTrades =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              Economy.legalTrades(catalog, value, active).map { result =>
                  LegalTradeProjection(result.target.kind, result.target.id.value,
                    economyLabel(result.target), result.suit.key,
                    result.resource match {
                      case oathdigital.setup.TradeResource.Favor => "favor"
                      case oathdigital.setup.TradeResource.Secret => "secret"
                    }, result.supplySpent, result.gained)
              }
            else Vector.empty,
          boardTargetActions =
            if (requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty)
              boardTargetActions(value, active)
            else Vector.empty,
          pendingCardDecision = pendingDecision,
          recover = recoverProjection,
          campaign = campaignProjection,
          worldDeckCount = current.commonCards.worldDeck.size,
          // Card backs/types are public; the World Deck top is its head.
          worldDeckTopCardKind = current.commonCards.worldDeck.headOption.map(cardKind),
          playerBoards = viewerOrderedBoards(value, requestingPlayer),
          oathkeeper = Some(OathkeeperProjection(
            "supremacy", current.title.holder.map(_.value),
            current.title.side match {
              case TitleSide.Oathkeeper => "oathkeeper"
              case TitleSide.Usurper => "usurper"
            }, current.tracks.usurperLimited, current.result.map(_.winner.value))),
          oathkeeperRecipient = oathkeeperRecipient)
    }

  private def economyLabel(target: EconomyTargetRef): String = target match {
    case EconomyTargetRef.Denizen(id) =>
      denizenNames.getOrElse(id, safeLabel(id.value))
    case EconomyTargetRef.Edifice(id) =>
      edificeNames.get(id).map(_._2).getOrElse(safeLabel(id.value))
  }

  private def boardTargetActions(ready: ReadyGame,
      player: PlayerState): Vector[BoardTargetActionProjection] = {
    val travel = TravelRules.legalDestinations(catalog, ready, player).map {
      case (siteId, cost) => BoardTargetCandidateProjection(
        BoardTargetRefProjection.Site(siteId.value),
        siteNames.getOrElse(siteId, safeLabel(siteId.value)),
        Vector(s"$cost Supply"))
    }
    val musters = Economy.legalMuster(catalog, ready, player).map { result =>
      economyCandidate(result.target, result.source,
        Vector(s"${result.supplySpent} Supply",
          s"+${result.warbandsGained} warbands"))
    }
    val trades = Economy.legalTrades(catalog, ready, player)
    val campaign = CampaignRules.legalTargets(catalog, ready, player.player).map { siteId =>
      BoardTargetCandidateProjection(BoardTargetRefProjection.Site(siteId.value),
        siteNames.getOrElse(siteId, safeLabel(siteId.value)),
        Vector(s"${oathdigital.gameplay.actions.Campaign.SupplyCost} Supply",
          s"Choose ${oathdigital.gameplay.actions.Campaign.MinimumForce} to " +
            s"${player.board.warbands} board warbands"))
    }
    val favor = trades.filter(_.resource == oathdigital.setup.TradeResource.Favor)
      .map(result => economyCandidate(result.target, result.source,
        Vector(s"${result.supplySpent} Supply", s"+${result.gained} favor")))
    val secret = trades.filter(_.resource == oathdigital.setup.TradeResource.Secret)
      .map(result => economyCandidate(result.target, result.source,
        Vector(s"${result.supplySpent} Supply", s"+${result.gained} secrets")))
    Vector(
      selection("travel", "Choose a Travel destination", travel),
      selection("campaign-conquest", "Choose optional same-ruler Conquest sites", campaign,
        Option.when(campaign.nonEmpty)(BoardTargetFormationProjection(
          oathdigital.gameplay.actions.Campaign.MinimumForce, player.board.warbands,
          player.board.warbands, oathdigital.gameplay.actions.Campaign.SupplyCost)),
        minimum = 1, maximum = campaign.size,
        requiredTargets = campaign.headOption.map(_.target).toVector),
      selection("muster", "Choose a card to Muster from", musters),
      selection("trade-favor", "Choose a card to Trade for favor", favor),
      selection("trade-secret", "Choose a card to Trade for secrets", secret)
    ).flatten
  }

  private def selection(kind: String, prompt: String,
      candidates: Vector[BoardTargetCandidateProjection],
      formation: Option[BoardTargetFormationProjection] = None,
      minimum: Int = 1, maximum: Int = 1,
      requiredTargets: Vector[BoardTargetRefProjection] = Vector.empty) =
    Option.when(candidates.nonEmpty)(BoardTargetActionProjection(
      kind, prompt, minimum, maximum, autoActivate = false, candidates,
      formation, requiredTargets))

  private def economyCandidate(target: EconomyTargetRef,
      source: oathdigital.gameplay.RuleSourceRef, details: Vector[String]) = {
    val siteId = source match {
      case oathdigital.gameplay.RuleSourceRef.SiteCard(site, _) => site
      case oathdigital.gameplay.RuleSourceRef.Edifice(site, _) => site
      case other => throw new IllegalStateException(
        s"Economy candidate has non-site source $other")
    }
    BoardTargetCandidateProjection(BoardTargetRefProjection.SiteCard(
      siteId.value, target.kind, target.id.value), economyLabel(target), details)
  }

  private def attackFaceName(value: AttackDieFace): String = value match {
    case AttackDieFace.HollowSword => "hollow-sword"
    case AttackDieFace.OneSword => "one-sword"
    case AttackDieFace.TwoSwordsSkull => "two-swords-skull"
  }

  private def players(
      participants: Vector[FirstGameParticipant]
  ): Vector[SetupPlayerProjection] =
    participants.map { participant =>
      SetupPlayerProjection(
        participant.playerId.value,
        safeLabel(participant.playerId.value),
        "exile",
        participant.color.value
      )
    }

  private def world(sites: Vector[SiteId]): Vector[SetupRegionProjection] =
    Vector(
      region("cradle", sites.take(2)),
      region("provinces", sites.slice(2, 5)),
      region("hinterland", sites.slice(5, 8))
    )

  private def region(
      id: String,
      sites: Vector[SiteId],
      states: Map[SiteId, SiteState] = Map.empty,
      discard: Vector[CardId] = Vector.empty,
      ready: Option[ReadyGame] = None
  ): SetupRegionProjection =
    SetupRegionProjection(
      id,
      sites.map(site =>
        siteProjection(site, states.get(site), ready)),
      discard.size,
      // Regional discards are faceup public piles; the final element is top.
      discard.lastOption.map(cardKind)
    )

  private def siteProjection(
      siteId: SiteId,
      state: Option[SiteState],
      ready: Option[ReadyGame]
  ): SetupSiteProjection = {
    val definition = siteDefinitions.get(siteId)
    SetupSiteProjection(
      siteId.value,
      siteNames.getOrElse(siteId, safeLabel(siteId.value)),
      state.fold(0)(_.tokens.favor),
      state.fold(0)(_.tokens.secrets),
      definition.fold(0)(_.capacity),
      definition.fold(0)(_.relicSlots),
      state.toVector.flatMap(_.denizens).map { denizen =>
        val label = denizen match {
          case value: DenizenState =>
            denizenNames.getOrElse(value.id, safeLabel(value.id.value))
          case value: EdificeState =>
            edificeNames.get(value.id).fold(safeLabel(value.id.value)) {
              case (intact, ruined) => value.side match {
                case EdificeSide.Intact => intact
                case EdificeSide.Ruined => ruined
              }
            }
        }
        val details = denizen match {
          case value: DenizenState => Some(cardDetails(value.id,
            Some(value.orientation), hidden = false).copy(
              favor = value.tokens.favor, secrets = value.tokens.secrets))
          case value: EdificeState => Some(CardDetailsProjection(
            value.id.value, "edifice", label,
            suit = catalog.edifices.find(_.id.value == value.id.value).map(_.suit.value),
            side = Some(value.side match {
              case EdificeSide.Intact => "intact"
              case EdificeSide.Ruined => "ruined"
            }), favor = value.tokens.favor, secrets = value.tokens.secrets))
        }
        SiteCardProjection(denizen.id.value, label, details)
      },
      // Site relics are facedown (CR pp. 6, 25; NF p. 14). Public and
      // player projections expose only their count; recovery's peek does not
      // yet have an authorized private projection boundary.
      SiteRelicsProjection(state.fold(0)(_.relics.size)),
      definition.fold(0)(_.defense),
      definition.flatMap(site => Option.when(site.forgeRequirements.isEmpty)(site.recoverDifficulty).flatten),
      definition.flatMap(_.forgeRequirements).map(tokens =>
        ForgeCostProjection(tokens.favor, tokens.secrets)),
      definition.toVector.flatMap(_.handlers).map(sitePower),
      for {
        site <- state
        game <- ready
        occupied <- site.forces match {
          case value: SiteForces.Occupied => Some(value)
          case SiteForces.Empty => None
        }
      } yield forceProjection(occupied, game)
    )
  }

  private def forceProjection(forces: SiteForces.Occupied,
      ready: ReadyGame): SiteForcesProjection = {
    val ruler = SiteRule.ruler(forces, ready.game.current.players).fold(
      error => throw new IllegalStateException(
        s"invalid site ruler mapping: $error"), identity)
    forces.kind match {
      case ForceKind.Exile(lineage) =>
        val SiteRuler.Player(playerId) = ruler: @unchecked
        val color = ready.playerColors.getOrElse(playerId,
          throw new IllegalStateException(
            s"missing color for site ruler ${playerId.value}"))
        val colorLabel = color.value.headOption.fold(color.value)(head =>
          s"${head.toUpper}${color.value.drop(1)}")
        SiteForcesProjection("exile", forces.count, "player",
          Some(playerId.value), s"$colorLabel Warbands", color.value)
      case ForceKind.Imperial =>
        SiteForcesProjection("imperial", forces.count, "empire", None,
          "Imperial Warbands", "empire")
      case ForceKind.Bandit =>
        SiteForcesProjection("bandit", forces.count, "bandit", None,
          "Bandit Warbands", "bandit")
    }
  }

  private def cardKind(card: CardId): String = card match {
    case _: VisionId => "vision"
    case _ => "denizen"
  }

  private def sitePower(handler: String): SitePowerProjection = {
    val kind = handler.split('.').lastOption.getOrElse(handler)
    // Display-only vocabulary: Combined Rulebook p.31 and New Foundations
    // pp.10-11. Executable behavior remains in typed rule handlers.
    val known = Map(
      "coast" -> ("Coast", "Travel along the Coast route."),
      "mountain" -> ("Mountain", "Travel here costs additional Supply."),
      "river" -> ("River", "Part of the River route."),
      "island" -> ("Island", "Travel here follows Island travel rules."),
      "pass" -> ("Pass", "Travel through the Pass is restricted."),
      "plains" -> ("Plains", "This site has the Plains site power."))
    known.get(kind).fold(SitePowerProjection(kind, safeLabel(kind), None)) {
      case (label, description) => SitePowerProjection(kind, label, Some(description))
    }
  }

  private def groupedResolutions(
      placements: Vector[SearchPlacement]
  ): Vector[CardResolutionProjection] = {
    val orderedKeys = placements.map {
      case SearchPlacement.Discard => "discard" -> None
      case SearchPlacement.Site(_) => "site" -> Some("face-up")
      case SearchPlacement.Adviser(orientation, _) =>
        "adviser" -> Some(orientationName(orientation))
    }.distinct
    orderedKeys.map { case (kind, orientation) =>
      val matching = placements.filter {
        case SearchPlacement.Discard => kind == "discard"
        case SearchPlacement.Site(_) => kind == "site"
        case SearchPlacement.Adviser(value, _) =>
          kind == "adviser" && orientation.contains(orientationName(value))
      }
      val replacements = matching.flatMap {
        case SearchPlacement.Site(replace) => replace
        case SearchPlacement.Adviser(_, replace) => replace
        case SearchPlacement.Discard => None
      }.distinct
      val required = matching.nonEmpty && matching.forall {
        case SearchPlacement.Site(replace) => replace.nonEmpty
        case SearchPlacement.Adviser(_, replace) => replace.nonEmpty
        case SearchPlacement.Discard => false
      }
      CardResolutionProjection(kind, orientation, required,
        if (required) replacements.map(cardDetails(_, None, hidden = false))
        else Vector.empty)
    }
  }

  private def viewerOrderedBoards(
      ready: ReadyGame,
      viewer: Option[PlayerId]
  ): Vector[PlayerBoardProjection] = {
    val players = ready.game.current.players
    val start = viewer.flatMap(id => Option(players.indexWhere(_.player == id))
      .filter(_ >= 0)).getOrElse(0)
    (players.drop(start) ++ players.take(start)).map { player =>
      val owns = viewer.contains(player.player)
      PlayerBoardProjection(player.player.value, player.board.warbands,
        player.board.favor, player.board.faceUpSecrets, player.board.faceDownSecrets,
        player.board.supply.supply, player.pawnSite.map(_.value),
        player.advisers.map(card => if (adviserOrientation(card) == Orientation.FaceDown && !owns)
          hiddenCard(card.id, "adviser") else cardDetails(card.id,
            Some(adviserOrientation(card)), hidden = false)),
        player.relics.map(card => if (card.orientation == Orientation.FaceDown && !owns)
          hiddenCard(card.id, "relic") else cardDetails(card.id,
            Some(card.orientation), hidden = false)),
        player.revealedVision.map(card => cardDetails(card.id,
          Some(card.orientation), hidden = false)))
    }
  }

  private def hiddenCard(id: CardId, kind: String) = CardDetailsProjection(
    "hidden", kind, s"Facedown $kind", orientation = Some("face-down"), hidden = true)

  private def adviserOrientation(card: AdviserState): Orientation = card match {
    case value: DenizenState => value.orientation
    case value: VisionState => value.orientation
  }

  private def orientationName(value: Orientation): String = value match {
    case Orientation.FaceUp => "face-up"
    case Orientation.FaceDown => "face-down"
  }

  private def defenseFaceName(value: DefenseDieFace): String = value match {
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "one-shield"
    case DefenseDieFace.TwoShields => "two-shields"
    case DefenseDieFace.Doubler => "doubler"
  }

  private def cardDetails(
      id: CardId,
      orientation: Option[Orientation],
      hidden: Boolean
  ): CardDetailsProjection = id match {
    case value: DenizenId => catalog.denizens.find(_.id.value == value.value).fold(
      CardDetailsProjection(value.value, "denizen", worldCardLabel(value),
        orientation = orientation.map(orientationName), hidden = hidden)) { d =>
      CardDetailsProjection(value.value, "denizen", d.name, Some(d.suit.value),
        Some(restrictionName(d.restrictions)), Some(d.rulesText),
        orientation.map(orientationName), hidden = hidden)
    }
    case value: VisionId => CardDetailsProjection(value.value, "vision",
      safeLabel(value.value), orientation = orientation.map(orientationName), hidden = hidden)
    case value: RelicId => catalog.relics.find(_.id.value == value.value).fold(
      CardDetailsProjection(value.value, "relic", safeLabel(value.value),
        orientation = orientation.map(orientationName), hidden = hidden)) { r =>
      CardDetailsProjection(value.value, "relic", r.name, rulesText = Some(r.rulesText),
        orientation = orientation.map(orientationName), relicValue = Some(r.value),
        defense = Some(r.defense), hidden = hidden)
    }
    case other => CardDetailsProjection(other.value, other.getClass.getSimpleName,
      safeLabel(other.value), orientation = orientation.map(orientationName), hidden = hidden)
  }

  private def restrictionName(value: oathdigital.catalog.CardRestrictions): String = value match {
    case oathdigital.catalog.CardRestrictions.Unrestricted => "unrestricted"
    case oathdigital.catalog.CardRestrictions.SiteOnly => "site-only"
    case oathdigital.catalog.CardRestrictions.AdviserOnly => "adviser-only"
    case oathdigital.catalog.CardRestrictions.LockedAdviserOnly => "locked-adviser-only"
  }

  private def turnOrder(
      participants: Vector[FirstGameParticipant],
      firstPlayer: PlayerId
  ): Vector[FirstGameParticipant] = {
    val start = participants.indexWhere(_.playerId == firstPlayer)
    participants.drop(start) ++ participants.take(start)
  }

  private def safeLabel(id: String): String =
    id.split(":").lastOption.getOrElse(id)
      .split("-").map(_.capitalize).mkString(" ")

  private def worldCardLabel(id: WorldCardId): String = id match {
    case value: DenizenId => denizenNames.getOrElse(value, safeLabel(value.value))
    case value: VisionId => safeLabel(value.value)
  }

}
