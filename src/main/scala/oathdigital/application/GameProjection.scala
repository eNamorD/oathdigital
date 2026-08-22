package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.setup.OathState.{InProgress, NoGame, Ready}
import oathdigital.setup.FirstGameParticipant
import oathdigital.setup.ReadyGame
import oathdigital.setup.WakeResource
import oathdigital.gameplay.TakeWealthRules
import oathdigital.gameplay.actions.{BannerRules, CampaignPlanOption, CampaignRules, ChallengeRules, Economy, ForgeRules, MinorActions, RecoverRules, SearchRules, TravelRules, VisionRules, Visions}
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
final case class SiteRelicsProjection(facedownCount: Int,
    knownRelics: Vector[CardDetailsProjection] = Vector.empty)
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
  final case class Player(playerId: String) extends BoardTargetRefProjection
  final case class Site(siteId: String) extends BoardTargetRefProjection
  final case class SiteCard(siteId: String, cardKind: String, cardId: String)
      extends BoardTargetRefProjection
  final case class PlayerAdviser(playerId: String, cardId: String)
      extends BoardTargetRefProjection
  final case class PlayerRelic(playerId: String, relicId: String)
      extends BoardTargetRefProjection
  final case class PlayerPawn(playerId: String) extends BoardTargetRefProjection
  final case class PlayerBanner(playerId: String, banner: String)
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
    requiredTargets: Vector[BoardTargetRefProjection] = Vector.empty,
    decisionId: Option[String] = None
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
final case class ForgeAssignmentTargetProjection(
    siteId: String, denizenId: String, label: String)
final case class ForgeProjection(
    decisionId: String, actorPlayerId: String, favor: Int, secrets: Int,
    targets: Vector[ForgeAssignmentTargetProjection])
final case class BannerProjection(key: String, face: String,
    holderPlayerId: Option[String], resources: Int)
final case class ChallengeProjection(decisionId: String, actorPlayerId: String,
    banner: String, priorHolderPlayerId: Option[String], priorResources: Int,
    legalSecretSiteIds: Vector[String],
    minimumPlacement: Int, maximumPlacement: Int)
final case class CampaignProjection(
    decisionId: String, targetSiteIds: Vector[String], force: Int,
    plansFinished: Boolean, planChoices: Vector[CampaignPlanChoiceProjection],
    selectedPlans: Vector[CampaignPlanChoiceProjection],
    attackDice: Vector[String], attack: Int, skullLosses: Int,
    maxSacrifice: Int, sacrificed: Option[Int], defenseDice: Vector[String],
    defense: Option[Int], victorious: Option[Boolean], maxPlacement: Int,
    placementTargets: Vector[CampaignPlacementTargetProjection],
    defenderKind: String = "bandits", defenderPlayerId: Option[String] = None,
    defenderForce: Int = 0, defenseDiceCount: Int = 0,
    planSide: String = "attacker", decisionOwnerPlayerId: Option[String] = None,
    kind: String = "conquest", raidTargets: Vector[String] = Vector.empty)
final case class CampaignPlacementTargetProjection(siteId: String, label: String)
final case class CampaignRaidRelocationProjection(
    decisionId: String, actorPlayerId: String, defenderPlayerId: String,
    originSiteId: String, legalSiteIds: Vector[String])
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
final case class MinorAdviserProjection(card: CardDetailsProjection,
    placements: Vector[CardResolutionProjection])
final case class MinorActionsProjection(advisers: Vector[MinorAdviserProjection],
    canPeekSiteRelics: Boolean, facedownRelics: Vector[CardDetailsProjection],
    siteId: Option[String], maxBoardToSite: Int, maxSiteToBoard: Int)
final case class NegotiationTransferProjection(authorPlayerId: String,
    recipientPlayerId: String, favor: Int, relicCount: Int,
    relics: Vector[CardDetailsProjection])
final case class NegotiationDisclosureProjection(authorPlayerId: String,
    recipientPlayerId: String, kind: String,
    card: Option[CardDetailsProjection])
final case class NegotiationProjection(decisionId: String, actorPlayerId: String,
    siteId: String, participantPlayerIds: Vector[String],
    acceptedPlayerIds: Vector[String], transfers: Vector[NegotiationTransferProjection],
    disclosures: Vector[NegotiationDisclosureProjection],
    editableFavor: Int, editableRelics: Vector[CardDetailsProjection],
    editableAdvisers: Vector[CardDetailsProjection],
    editableSiteRelics: Vector[CardDetailsProjection])

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
    forge: Option[ForgeProjection] = None,
    campaign: Option[CampaignProjection] = None,
    campaignRaidRelocation: Option[CampaignRaidRelocationProjection] = None,
    worldDeckCount: Int = 0,
    worldDeckTopCardKind: Option[String] = None,
    playerBoards: Vector[PlayerBoardProjection] = Vector.empty,
    oathkeeper: Option[OathkeeperProjection] = None,
    oathkeeperRecipient: Option[OathkeeperRecipientProjection] = None
    ,banners: Vector[BannerProjection] = Vector.empty
    ,challenge: Option[ChallengeProjection] = None
    ,minorActions: Option[MinorActionsProjection] = None
    ,negotiation: Option[NegotiationProjection] = None
    ,negotiationWaiting: Boolean = false
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
            case Some(n: PendingProcedure.Negotiation)
                if requestingPlayer.exists(n.participants.contains) =>
              Vector("replaceNegotiationTerms", "declineNegotiation") ++
                requestingPlayer.filter(oathdigital.gameplay.actions.Negotiation
                  .canAccept(value, n, _)).map(_ => "acceptNegotiation")
            case Some(_: PendingProcedure.Negotiation) => Vector.empty
            case Some(p: PendingProcedure.OathkeeperRecipient)
                if requestingPlayer.contains(p.actor) =>
              Vector("chooseOathkeeperRecipient")
            case Some(_: PendingProcedure.OathkeeperRecipient) => Vector.empty
            case Some(p: PendingProcedure.Conspiracy)
                if requestingPlayer.contains(p.actor) =>
              Vector("chooseConspiracySecretSite")
            case Some(_: PendingProcedure.Conspiracy) => Vector.empty
            case Some(c: PendingProcedure.Campaign) if !c.defenderPlansFinished &&
                requestingPlayer.contains(CampaignRules.planDecisionOwner(c)) =>
              Vector("chooseCampaignPlan", "finishCampaignPlans")
            case Some(r: PendingProcedure.CampaignRaidRelocation)
                if requestingPlayer.contains(r.actor) =>
              Vector("relocateCampaignRaidPawn")
            case _ if !requestingPlayer.contains(active.player) => Vector.empty
            case Some(r: PendingProcedure.Recover) if !r.successful =>
              Vector(Option.when(active.board.supply.supply > 0)("addRecoverDice"),
                Some("stopRecover")).flatten
            case Some(_: PendingProcedure.Recover) => Vector.empty
            case Some(_: PendingProcedure.Forge) => Vector("completeForge")
            case Some(c: PendingProcedure.Challenge) if requestingPlayer.contains(c.actor) =>
              if (c.remainingRibbonResources == 0) Vector("completeChallenge")
              else Vector("chooseChallengeSecretSite")
            case Some(c: PendingProcedure.Campaign) if c.victorious.contains(true) =>
              Vector(if (c.kind == CampaignKind.Raid) "relocateCampaignRaidPawn"
                else "placeCampaignForce")
            case Some(c: PendingProcedure.Campaign) if !c.defenderPlansFinished => Vector.empty
            case Some(_: PendingProcedure.Campaign) => Vector("chooseCampaignSacrifice")
            case Some(_) => Vector.empty
            case None => current.turn.phase match {
              case Phase.Act =>
                Vector(
                  Option.when(Rest.validateBegin(Ready(value), active.player).isRight)(
                    "beginRest"),
                  Option.when(active.pawnSite.exists(siteId =>
                    RecoverRules.validate(catalog, value, active, siteId).isRight))(
                    "beginRecover"),
                  Option.when(active.pawnSite.exists(siteId =>
                    ForgeRules.validate(catalog, value, active, siteId).isRight))(
                    "beginForge"),
                  Option.when(ChallengeRules.legal(catalog, value, active.player).nonEmpty)(
                    "beginChallenge"),
                  Option.when(Banner.all.exists(b => BannerRules.holder(current, b).contains(active.player) &&
                    BannerRules.playerResources(active, b) > 0))("placeBannerResource"),
                  Option.when(active.advisers.exists(adviserOrientation(_) == Orientation.FaceDown))(
                    "facedownAdviserMinorAction"),
                  Option.when(active.advisers.exists {
                    case VisionState(id, Orientation.FaceDown) =>
                      Visions.canReveal(catalog, value, active.player, id)
                    case _ => false
                  })("revealVision"),
                  Option.when(active.advisers.exists {
                    case VisionState(id, Orientation.FaceDown) => id == VisionRules.Conspiracy
                    case _ => false
                  } && Visions.canPlayConspiracy(catalog, value, active.player))(
                    "playConspiracy"),
                  Option.when(active.pawnSite.flatMap(current.map.sites.get).exists(_.relics.nonEmpty))(
                    "peekSiteRelics"),
                  Option.when(active.relics.exists(_.orientation == Orientation.FaceDown))(
                    "revealOwnedRelic"),
                  Option.when(minorActionsProjection(value, active).maxBoardToSite > 0 ||
                    minorActionsProjection(value, active).maxSiteToBoard > 0)("moveWarbands"),
                  Option.when(oathdigital.gameplay.actions.Negotiation
                    .legalParticipants(value, active.player).nonEmpty)("beginNegotiation")
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
        val forgeProjection = current.pending.collect {
          case f: PendingProcedure.Forge if requestingPlayer.contains(f.actor) =>
            ForgeProjection(f.decision.value, f.actor.value, f.cost.favor,
              f.cost.secrets, f.eligibleTargets.map(t =>
                ForgeAssignmentTargetProjection(t.siteId.value, t.denizenId.value,
                  denizenNames.getOrElse(t.denizenId, safeLabel(t.denizenId.value)))))
        }
        val challengeProjection = current.pending.collect {
          case c: PendingProcedure.Challenge if requestingPlayer.contains(c.actor) =>
            val actor = current.players.find(_.player == c.actor).get
            val legalSites = if (c.banner == Banner.DarkestSecret && c.remainingRibbonResources > 0)
              BannerRules.leastSites(current, c.secretsPlaced).map(_.value) else Vector.empty
            ChallengeProjection(c.decision.value, c.actor.value, c.banner.key,
              c.priorHolder.map(_.value), c.priorResources, legalSites,
              c.priorResources + 1, BannerRules.playerResources(actor, c.banner))
        }
        val campaignProjection = current.pending.collect {
          case c: PendingProcedure.Campaign if requestingPlayer.exists(player =>
              player == c.actor || (!c.defenderPlansFinished &&
                player == CampaignRules.planDecisionOwner(c))) =>
            val remaining = c.force - c.skullLosses
            def sourceFields(source: PendingProcedure.CampaignPlanSource) = source match {
              case PendingProcedure.CampaignPlanSource.Adviser(player, id) =>
                ("adviser", Some(player.value), None, Some(id.value),
                  denizenNames.getOrElse(id, safeLabel(id.value)))
              case PendingProcedure.CampaignPlanSource.SiteCard(sourceSite, id) =>
                ("site-card", None, Some(sourceSite.value), Some(id.value),
                  denizenNames.getOrElse(id, safeLabel(id.value)))
              case PendingProcedure.CampaignPlanSource.Relic(player, id) =>
                ("relic", Some(player.value), None, Some(id.value),
                  catalog.relics.find(_.id.value == id.value).map(_.name).getOrElse(safeLabel(id.value)))
              case PendingProcedure.CampaignPlanSource.Title(player) =>
                ("title", Some(player.value), None, None,
                  current.title.side.toString)
            }
            def costs(costs: Vector[PendingProcedure.CampaignPlanCost]) = (
              costs.collect { case PendingProcedure.CampaignPlanCost.Favor(n) => n }.sum,
              costs.collect { case PendingProcedure.CampaignPlanCost.Secret(n) => n }.sum)
            def effects(effects: Vector[PendingProcedure.CampaignPlanEffect]) =
              effects.map {
                case PendingProcedure.CampaignPlanEffect.AddAttackDice(n) => s"Add $n attack dice"
                case PendingProcedure.CampaignPlanEffect.AddDefenseDice(n) => s"Add $n defense dice"
                case PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls => "Ignore attack-roll skull losses"
                case PendingProcedure.CampaignPlanEffect.RevealSource => "Reveal this card"
                case PendingProcedure.CampaignPlanEffect.TransformAttackResult(id) => s"Transform attack result ($id)"
                case PendingProcedure.CampaignPlanEffect.ReplaceLosingForcePolicy(id) => s"Replace losing-force policy ($id)"
                case PendingProcedure.CampaignPlanEffect.Suspend(kind) => s"Requires $kind decision"
              }.mkString("; ")
            def optionProjection(option: CampaignPlanOption) = {
              val (kind, player, site, card, _) = sourceFields(option.source)
              val (favor, secret) = costs(option.costs)
              CampaignPlanChoiceProjection(kind, Some(option.source.stableKey), player,
                site, card, option.label, Some(option.handlerId), favor, secret,
                option.description)
            }
            def resolutionProjection(plan: PendingProcedure.CampaignPlanResolution) = {
              val (kind, player, site, card, label) = sourceFields(plan.source)
              val (favor, secret) = costs(plan.costs)
              CampaignPlanChoiceProjection(kind, Some(plan.source.stableKey), player,
                site, card, label, Some(plan.handlerId), favor, secret,
                effects(plan.effects))
            }
            val isDecisionOwner = requestingPlayer.contains(CampaignRules.planDecisionOwner(c))
            val planChoices = if (isDecisionOwner)
              CampaignRules.planOptions(catalog, value, c).map(optionProjection)
            else Vector.empty
            val selectedPlans = c.plans.filter(plan => plan.side ==
              CampaignRules.currentPlanSide(c)).map(resolutionProjection)
            CampaignProjection(c.decision.value, c.targetSites.map(_.value), c.force,
              c.defenderPlansFinished, if (c.defenderPlansFinished) Vector.empty else planChoices,
              selectedPlans,
              c.attackDice.map(attackFaceName), c.attack, c.skullLosses,
              remaining, c.sacrificed, c.defenseDice.map(defenseFaceName),
              c.defense, c.victorious,
              remaining - c.sacrificed.getOrElse(0),
              c.targetSites.map(site => CampaignPlacementTargetProjection(
                site.value, siteNames.getOrElse(site, safeLabel(site.value)))),
              c.defender match {
                case CampaignDefender.Bandits => "bandits"
                case _: CampaignDefender.Player => "player"
              }, c.defender match {
                case CampaignDefender.Player(player) => Some(player.value)
                case _ => None
              }, CampaignRules.defenderForce(value, c),
              CampaignRules.defenseDiceCount(catalog, value, c),
              CampaignRules.currentPlanSide(c).toString.toLowerCase,
              Option.when(!c.defenderPlansFinished)(CampaignRules.planDecisionOwner(c).value),
              c.kind.key, c.raidTargets.map(_.stableKey))
        }
        val campaignRaidRelocation = current.pending.collect {
          case r: PendingProcedure.CampaignRaidRelocation
              if requestingPlayer.contains(r.actor) =>
            CampaignRaidRelocationProjection(r.decision.value, r.actor.value,
              r.defender.value, r.origin.value, r.legalSites.map(_.value))
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
            case Some(_: PendingProcedure.Forge) if forgeProjection.nonEmpty => "forge-assignment"
            case Some(_: PendingProcedure.Forge) => "forge-waiting"
            case Some(_: PendingProcedure.Challenge) if challengeProjection.nonEmpty => "challenge-decision"
            case Some(_: PendingProcedure.Challenge) => "challenge-waiting"
            case Some(_: PendingProcedure.Campaign) if campaignProjection.exists(_.victorious.contains(true)) => "campaign-placement"
            case Some(c: PendingProcedure.Campaign) if campaignProjection.nonEmpty && !c.defenderPlansFinished => "campaign-plan"
            case Some(_: PendingProcedure.Campaign) if campaignProjection.nonEmpty => "campaign-sacrifice"
            case Some(_: PendingProcedure.Campaign) => "campaign-waiting"
            case Some(_: PendingProcedure.CampaignRaidRelocation)
                if campaignRaidRelocation.nonEmpty => "campaign-raid-relocation"
            case Some(_: PendingProcedure.CampaignRaidRelocation) =>
              "campaign-raid-relocation-waiting"
            case Some(_: PendingProcedure.OathkeeperRecipient)
                if oathkeeperRecipient.nonEmpty => "oathkeeper-recipient"
            case Some(_: PendingProcedure.OathkeeperRecipient) =>
              "oathkeeper-recipient-waiting"
            case Some(p: PendingProcedure.Conspiracy)
                if requestingPlayer.contains(p.actor) => "conspiracy-secret-site"
            case Some(_: PendingProcedure.Conspiracy) => "conspiracy-waiting"
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
              value.game.current.commonCards.discard(Region.Cradle), Some(value), requestingPlayer),
            region("provinces", value.game.current.map.provinces,
              value.game.current.map.sites,
              value.game.current.commonCards.discard(Region.Provinces), Some(value), requestingPlayer),
            region("hinterland", value.game.current.map.hinterland,
              value.game.current.map.sites,
              value.game.current.commonCards.discard(Region.Hinterland), Some(value), requestingPlayer)
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
                "Forge", "Recover", "Challenge", "Minor Actions")
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
            current.pending match {
              case Some(p: PendingProcedure.Conspiracy)
                  if requestingPlayer.contains(p.actor) =>
                Vector(conspiracySecretSiteAction(value, p))
              case _ if requestingPlayer.contains(active.player) &&
                current.turn.phase == Phase.Act && current.pending.isEmpty =>
                boardTargetActions(value, active)
              case _ => Vector.empty
            },
          pendingCardDecision = pendingDecision,
          recover = recoverProjection,
          forge = forgeProjection,
          campaign = campaignProjection,
          worldDeckCount = current.commonCards.worldDeck.size,
          // Card backs/types are public; the World Deck top is its head.
          worldDeckTopCardKind = current.commonCards.worldDeck.headOption.map(cardKind),
          playerBoards = viewerOrderedBoards(value, requestingPlayer),
          oathkeeper = Some(OathkeeperProjection(
            value.game.campaign.oathkeeperGoal.key,
            current.title.holder.map(_.value),
            current.title.side match {
              case TitleSide.Oathkeeper => "oathkeeper"
              case TitleSide.Usurper => "usurper"
            }, current.tracks.usurperLimited, current.result.map(_.winner.value))),
          oathkeeperRecipient = oathkeeperRecipient,
          campaignRaidRelocation = campaignRaidRelocation)
          .copy(banners = Vector(
            BannerProjection("peoples-favor", current.banners.peoplesFavor.active match {
              case PeoplesFavorFace.Mob => "mob"; case PeoplesFavorFace.GrandCouncil => "grand-council"
            }, current.banners.peoplesFavor.holder.map(_.value), current.banners.peoplesFavor.favor),
            BannerProjection("darkest-secret", current.banners.darkestSecret.active match {
              case DarkestSecretFace.WanderingFlame => "wandering-flame"; case DarkestSecretFace.Festival => "festival"
            }, current.banners.darkestSecret.holder.map(_.value), current.banners.darkestSecret.secrets)),
            challenge = challengeProjection,
            minorActions = Option.when(requestingPlayer.contains(active.player) &&
              current.turn.phase == Phase.Act && current.pending.isEmpty)(
              minorActionsProjection(value, active)),
            negotiation = negotiationProjection(value, requestingPlayer),
            negotiationWaiting = current.pending.exists {
              case n: PendingProcedure.Negotiation =>
                !requestingPlayer.exists(n.participants.contains)
              case _ => false
            })
    }

  private def minorActionsProjection(ready: ReadyGame,
      active: PlayerState): MinorActionsProjection = {
    val current = ready.game.current
    val facedown = active.advisers.collect {
      case d: DenizenState if d.orientation == Orientation.FaceDown => d.id: WorldCardId
      case v: VisionState if v.orientation == Orientation.FaceDown => v.id: WorldCardId
    }
    val atSite = active.pawnSite.flatMap(current.map.sites.get)
    val ruled = atSite.exists(site => SiteRule.ruledBy(site.forces,
      current.players, active.player).getOrElse(false))
    val siteWarbands = atSite.flatMap(_.forces match {
      case SiteForces.Occupied(ForceKind.Exile(lineage), count)
          if lineage == active.lineage => Some(count)
      case _ => None
    }).getOrElse(0)
    MinorActionsProjection(facedown.map { id =>
      MinorAdviserProjection(cardDetails(id, Some(Orientation.FaceDown), hidden = false),
        MinorActions.legalAdviserPlacements(catalog, ready, active.player, id).map {
          case SearchPlacement.Adviser(_, _) => CardResolutionProjection("play-adviser")
          case SearchPlacement.Site(replace) => CardResolutionProjection("play-site",
            replacementRequired = replace.nonEmpty,
            replacementTargets = replace.toVector.map(card => cardDetails(card,
              Some(Orientation.FaceUp), hidden = false)))
          case _ => CardResolutionProjection("discard")
        } :+ CardResolutionProjection("discard"))
    }, atSite.exists(_.relics.nonEmpty), active.relics.filter(
      _.orientation == Orientation.FaceDown).map(r => cardDetails(r.id,
      Some(r.orientation), hidden = false)), active.pawnSite.map(_.value),
      if (ruled) active.board.warbands else 0, math.max(0, siteWarbands - 1))
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
    val raid = CampaignRules.legalRaidTargets(catalog, ready, player.player).map {
      case target @ CampaignRaidTarget.Pawn(defender) =>
        BoardTargetCandidateProjection(BoardTargetRefProjection.PlayerPawn(defender.value),
          s"${safeLabel(defender.value)} pawn", Vector("Required Raid target"))
      case target @ CampaignRaidTarget.Relic(defender, relic) =>
        BoardTargetCandidateProjection(
          BoardTargetRefProjection.PlayerRelic(defender.value, relic.value),
          catalog.relics.find(_.id.value == relic.value).map(_.name)
            .getOrElse(safeLabel(relic.value)))
      case CampaignRaidTarget.Banner(defender, banner) =>
        val key = banner match {
          case CampaignBanner.PeoplesFavor => "peoples-favor"
          case CampaignBanner.DarkestSecret => "darkest-secret"
        }
        BoardTargetCandidateProjection(
          BoardTargetRefProjection.PlayerBanner(defender.value, key),
          safeLabel(key))
    }
    val favor = trades.filter(_.resource == oathdigital.setup.TradeResource.Favor)
      .map(result => economyCandidate(result.target, result.source,
        Vector(s"${result.supplySpent} Supply", s"+${result.gained} favor")))
    val secret = trades.filter(_.resource == oathdigital.setup.TradeResource.Secret)
      .map(result => economyCandidate(result.target, result.source,
        Vector(s"${result.supplySpent} Supply", s"+${result.gained} secrets")))
    val challenges = ChallengeRules.legal(catalog, ready, player.player).map { banner =>
      val holder = BannerRules.holder(ready.game.current, banner)
      BoardTargetCandidateProjection(
        BoardTargetRefProjection.PlayerBanner(holder.map(_.value).getOrElse("shared-bank"), banner.key),
        safeLabel(banner.key), Vector("1 Supply",
          s"Currently ${BannerRules.resources(ready.game.current, banner)} resources"))
    }
    val negotiators = oathdigital.gameplay.actions.Negotiation
      .legalParticipants(ready, player.player).map { candidate =>
        BoardTargetCandidateProjection(BoardTargetRefProjection.Player(candidate.value),
          safeLabel(candidate.value), Vector("Co-located negotiator"))
      }
    val revealVisions = player.advisers.collect {
      case VisionState(id, Orientation.FaceDown)
          if VisionRules.trueGoal(id).nonEmpty =>
        BoardTargetCandidateProjection(
          BoardTargetRefProjection.PlayerAdviser(player.player.value, id.value),
          safeLabel(id.value))
    }
    val hasConspiracy = player.advisers.exists {
      case VisionState(id, Orientation.FaceDown) => id == VisionRules.Conspiracy
      case _ => false
    }
    val conspiracyTargets = Visions.legalTargetRefs(ready, player.player).map {
      case ConspiracyTargetRef.RelicSlot(owner, slot) =>
        BoardTargetCandidateProjection(
          BoardTargetRefProjection.PlayerRelic(owner.value, slot.toString),
          s"${safeLabel(owner.value)} facedown relic")
      case ConspiracyTargetRef.Banner(owner, banner) =>
        BoardTargetCandidateProjection(
          BoardTargetRefProjection.PlayerBanner(owner.value, banner.key),
          s"${safeLabel(owner.value)} ${safeLabel(banner.key)}")
    }
    Vector(
      selection("travel", "Choose a Travel destination", travel),
      selection("campaign-conquest", "Choose optional same-ruler Conquest sites", campaign,
        Option.when(campaign.nonEmpty)(BoardTargetFormationProjection(
          oathdigital.gameplay.actions.Campaign.MinimumForce, player.board.warbands,
          player.board.warbands, oathdigital.gameplay.actions.Campaign.SupplyCost)),
        minimum = 1, maximum = campaign.size,
        requiredTargets = campaign.headOption.map(_.target).toVector),
      selection("campaign-raid", "Choose Raid targets", raid,
        Option.when(raid.nonEmpty)(BoardTargetFormationProjection(
          oathdigital.gameplay.actions.Campaign.MinimumForce, player.board.warbands,
          player.board.warbands, oathdigital.gameplay.actions.Campaign.SupplyCost)),
        minimum = Option.when(raid.nonEmpty)(1).getOrElse(0), maximum = raid.size,
        requiredTargets = raid.headOption.map(_.target).toVector),
      selection("challenge", "Choose a banner to Challenge", challenges),
      selection("negotiation", "Choose one or more co-located negotiators", negotiators,
        minimum = 1, maximum = negotiators.size),
      selection("reveal-vision", "Choose a Vision to reveal", revealVisions),
      Option.when(hasConspiracy)(BoardTargetActionProjection(
        "play-conspiracy", if (conspiracyTargets.isEmpty) "Play Conspiracy"
          else "Choose an enemy asset for Conspiracy",
        if (conspiracyTargets.isEmpty) 0 else 1,
        if (conspiracyTargets.isEmpty) 0 else 1,
        autoActivate = false, conspiracyTargets)),
      selection("muster", "Choose a card to Muster from", musters),
      selection("trade-favor", "Choose a card to Trade for favor", favor),
      selection("trade-secret", "Choose a card to Trade for secrets", secret)
    ).flatten
  }

  private def conspiracySecretSiteAction(ready: ReadyGame,
      pending: PendingProcedure.Conspiracy): BoardTargetActionProjection = {
    val sites = BannerRules.leastSites(
      ready.game.current, pending.secretSites).map { site =>
      BoardTargetCandidateProjection(BoardTargetRefProjection.Site(site.value),
        siteNames.getOrElse(site, safeLabel(site.value)))
    }
    BoardTargetActionProjection("conspiracy-secret-site",
      "Choose a tied least-stocked site for the Darkest Secret",
      1, 1, autoActivate = true, sites, decisionId = Some(pending.decision.value))
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
      ready: Option[ReadyGame] = None,
      viewer: Option[PlayerId] = None
  ): SetupRegionProjection =
    SetupRegionProjection(
      id,
      sites.map(site =>
        siteProjection(site, states.get(site), ready, viewer)),
      discard.size,
      // Regional discards are faceup public piles; the final element is top.
      discard.lastOption.map(cardKind)
    )

  private def siteProjection(
      siteId: SiteId,
      state: Option[SiteState],
      ready: Option[ReadyGame],
      viewer: Option[PlayerId] = None
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
      SiteRelicsProjection(state.fold(0)(_.relics.size), for {
        game <- ready.toVector
        player <- viewer.toVector
        known <- game.support.relicKnowledge.getOrElse(player, Map.empty)
          .getOrElse(siteId, Vector.empty)
        relic <- state.toVector.flatMap(_.relics).filter(_.id == known)
      } yield cardDetails(relic.id, Some(Orientation.FaceDown), hidden = false)),
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
      val knownAdvisers = viewer.toVector.flatMap(id =>
        ready.support.adviserKnowledge.getOrElse(id, Vector.empty)).toSet
      val knownRelics = viewer.toVector.flatMap(id =>
        ready.support.heldRelicKnowledge.getOrElse(id, Vector.empty)).toSet
      PlayerBoardProjection(player.player.value, player.board.warbands,
        player.board.favor, player.board.faceUpSecrets, player.board.faceDownSecrets,
        player.board.supply.supply, player.pawnSite.map(_.value),
        player.advisers.map(card => if (adviserOrientation(card) == Orientation.FaceDown &&
            !owns && !knownAdvisers(card.id.asInstanceOf[WorldCardId]))
          hiddenCard(card.id, "adviser") else cardDetails(card.id,
            Some(adviserOrientation(card)), hidden = false)),
        player.relics.map(card => if (card.orientation == Orientation.FaceDown &&
            !owns && !knownRelics(card.id))
          hiddenCard(card.id, "relic") else cardDetails(card.id,
            Some(card.orientation), hidden = false)),
        player.revealedVision.map(card => cardDetails(card.id,
          Some(card.orientation), hidden = false)))
    }
  }

  private def negotiationProjection(ready: ReadyGame,
      viewer: Option[PlayerId]): Option[NegotiationProjection] =
    ready.game.current.pending.collect {
      case negotiation: PendingProcedure.Negotiation
          if viewer.exists(negotiation.participants.contains) =>
        val viewing = viewer.get
        val transfers = negotiation.participants.flatMap { author =>
          negotiation.terms(author).transfers.map { transfer =>
            val owner = ready.game.current.players.find(_.player == author).get
            NegotiationTransferProjection(author.value, transfer.recipient.value,
              transfer.favor, transfer.relics.size,
              transfer.relics.flatMap(id => owner.relics.find(_.id == id)).collect {
                case relic if viewing == author || relic.orientation == Orientation.FaceUp =>
                  cardDetails(relic.id, Some(relic.orientation), hidden = false)
              })
          }
        }
        val disclosures = negotiation.participants.flatMap { author =>
          negotiation.terms(author).disclosures.map { disclosure =>
            val visible = viewing == author
            val (kind, detail) = disclosure.information match {
              case NegotiationDisclosureRef.Adviser(_, card) => "adviser" ->
                Option.when(visible)(cardDetails(card, Some(Orientation.FaceDown), hidden = false))
              case NegotiationDisclosureRef.HeldRelic(_, relic) => "held-relic" ->
                Option.when(visible)(cardDetails(relic, Some(Orientation.FaceDown), hidden = false))
              case NegotiationDisclosureRef.SiteRelic(_, relic) => "site-relic" ->
                Option.when(visible)(cardDetails(relic, Some(Orientation.FaceDown), hidden = false))
            }
            NegotiationDisclosureProjection(author.value, disclosure.recipient.value,
              kind, detail)
          }
        }
        val player = ready.game.current.players.find(_.player == viewing).get
        val siteRelics = ready.support.relicKnowledge.getOrElse(viewing, Map.empty)
          .toVector.flatMap { case (site, known) => ready.game.current.map.sites.get(site)
            .toVector.flatMap(_.relics.filter(r => known.contains(r.id))) }
        NegotiationProjection(negotiation.decision.value, negotiation.actor.value,
          negotiation.site.value, negotiation.participants.map(_.value),
          negotiation.participants.filter(negotiation.accepted).map(_.value),
          transfers, disclosures, player.board.favor,
          player.relics.map(r => cardDetails(r.id, Some(r.orientation), hidden = false)),
          player.advisers.collect {
            case d: DenizenState if d.orientation == Orientation.FaceDown =>
              cardDetails(d.id, Some(d.orientation), hidden = false)
            case v: VisionState if v.orientation == Orientation.FaceDown =>
              cardDetails(v.id, Some(v.orientation), hidden = false)
          }, siteRelics.map(r => cardDetails(r.id, Some(r.orientation), hidden = false)))
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
