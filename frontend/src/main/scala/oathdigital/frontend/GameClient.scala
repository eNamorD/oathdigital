package oathdigital.frontend

import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.util.control.NonFatal
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest}

final case class TransportResponse(status: Int, body: String)
final case class RawEvent(sequence: Long, discriminator: String, rawPayload: String)
trait JsonTransport {
  def request(
      method: String,
      url: String,
      body: Option[String]
  ): Future[Either[GameClientFailure, TransportResponse]]
}

final class SameOriginJsonTransport(timeoutMillis: Int = 10000)
    extends JsonTransport {
  require(timeoutMillis > 0, "timeoutMillis must be positive")

  override def request(
      method: String,
      url: String,
      body: Option[String]
  ): Future[Either[GameClientFailure, TransportResponse]] = {
    val promise = Promise[Either[GameClientFailure, TransportResponse]]()
    val xhr = new dom.XMLHttpRequest()
    xhr.open(method, url)
    xhr.timeout = timeoutMillis.toDouble
    body.foreach(_ => xhr.setRequestHeader("Content-Type", "application/json"))
    xhr.onload = _ =>
      promise.trySuccess(Right(TransportResponse(
        xhr.status.toInt,
        xhr.responseText
      )))
    xhr.onerror = _ =>
      promise.trySuccess(Left(GameClientFailure.NetworkFailure(
        s"$method $url failed"
      )))
    xhr.ontimeout = _ =>
      promise.trySuccess(Left(GameClientFailure.RequestTimedOut(
        method,
        url,
        timeoutMillis
      )))
    xhr.onabort = _ =>
      promise.trySuccess(Left(GameClientFailure.RequestAborted(
        method,
        url
      )))
    body match {
      case Some(json) => xhr.send(json)
      case None => xhr.send()
    }
    promise.future
  }
}

final case class GamePlayer(
    playerId: String,
    displayName: String,
    role: String,
    color: PlayerColorToken
)
final case class CardDetails(
    cardId: String, cardKind: String, name: String,
    suit: Option[String] = None, restrictions: Option[String] = None,
    rulesText: Option[String] = None, orientation: Option[String] = None,
    side: Option[String] = None, favor: Int = 0, secrets: Int = 0,
    relicValue: Option[Int] = None, defense: Option[Int] = None,
    hidden: Boolean = false)
final case class GameSiteCard(denizenId: String, label: String,
    details: Option[CardDetails] = None)
final case class GameSiteRelics(facedownCount: Int,
    knownRelics: Vector[CardDetails] = Vector.empty)
final case class ForgeCost(favor: Int, secrets: Int)
final case class SiteForces(
    forceKind: String,
    count: Int,
    rulerKind: String,
    rulerPlayerId: Option[String],
    label: String,
    colorToken: String
)
final case class GameSite(
    siteId: String,
    label: String,
    looseFavor: Int,
    looseSecrets: Int,
    denizenCapacity: Int,
    relicCapacity: Int,
    denizens: Vector[GameSiteCard],
    relics: GameSiteRelics,
    defense: Int = 0,
    recoverDifficulty: Option[Int] = None,
    forgeCost: Option[ForgeCost] = None,
    powers: Vector[SitePower] = Vector.empty,
    forces: Option[SiteForces] = None
)
final case class SitePower(kind: String, label: String, description: Option[String])
final case class GameRegion(regionId: String, sites: Vector[GameSite], discardCount: Int = 0,
    discardTopCardKind: Option[String] = None)
final case class GamePawn(playerId: String, siteId: String)
final case class ActivePlayerResources(
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    supply: Int
)
final case class CurrentSiteResources(
    siteId: String,
    favor: Int,
    secrets: Int
)
final case class LegalTravelDestination(siteId: String, supplyCost: Int)
final case class LegalSearchSource(kind: String, region: Option[String], supplyCost: Int)
final case class EconomyTarget(kind: String, id: String)
final case class LegalMuster(target: EconomyTarget, label: String, suit: String,
    supplyCost: Int, warbandsGained: Int)
final case class LegalTrade(target: EconomyTarget, label: String, suit: String,
    resource: String,
    supplyCost: Int, gained: Int)
sealed trait BoardTargetRef extends Product with Serializable {
  def stableKey: String
}
object BoardTargetRef {
  final case class Player(playerId: String) extends BoardTargetRef {
    def stableKey: String = s"player:$playerId"
  }
  final case class Site(siteId: String) extends BoardTargetRef {
    def stableKey: String = s"site:$siteId"
  }
  final case class SiteCard(siteId: String, cardKind: String, cardId: String)
      extends BoardTargetRef {
    def stableKey: String = s"site-card:$siteId:$cardKind:$cardId"
  }
  final case class PlayerAdviser(playerId: String, cardId: String)
      extends BoardTargetRef {
    def stableKey: String = s"player-adviser:$playerId:$cardId"
  }
  final case class PlayerRelic(playerId: String, relicId: String)
      extends BoardTargetRef {
    def stableKey: String = s"player-relic:$playerId:$relicId"
  }
  final case class PlayerPawn(playerId: String) extends BoardTargetRef {
    def stableKey: String = s"player-pawn:$playerId"
  }
  final case class PlayerBanner(playerId: String, banner: String)
      extends BoardTargetRef {
    def stableKey: String = s"player-banner:$playerId:$banner"
  }
}
final case class BoardTargetCandidate(
    target: BoardTargetRef, label: String, details: Vector[String])
final case class BoardTargetFormation(
    minimumForce: Int, maximumForce: Int, availableWarbands: Int,
    supplyCost: Int)
final case class BoardTargetAction(
    actionKind: String, prompt: String, minimum: Int, maximum: Int,
    autoActivate: Boolean, candidates: Vector[BoardTargetCandidate],
    formation: Option[BoardTargetFormation] = None,
    requiredTargets: Vector[BoardTargetRef] = Vector.empty,
    decisionId: Option[String] = None)
final case class CardResolution(kind: String, orientation: Option[String],
    replacementRequired: Boolean, replacementTargets: Vector[CardDetails])
final case class PendingCardDecision(
    decisionId: String, kind: String, actorPlayerId: String, prompt: String,
    instructions: Vector[String], cards: Vector[CardDetails], keepMinimum: Int,
    keepMaximum: Int, orderingRequired: Boolean,
    resolutionsByCard: Map[String, Vector[CardResolution]])
final case class ForgeTarget(siteId: String, denizenId: String, label: String)
final case class ForgeState(decisionId: String, actorPlayerId: String,
    favor: Int, secrets: Int, targets: Vector[ForgeTarget])
final case class BannerState(banner: String, face: String,
    holderPlayerId: Option[String], resources: Int)
final case class ChallengeState(decisionId: String, actorPlayerId: String,
    banner: String, priorHolderPlayerId: Option[String], priorResources: Int,
    legalSecretSiteIds: Vector[String],
    minimumPlacement: Int, maximumPlacement: Int)
final case class MinorAdviserPlacement(kind: String,
    replacement: Option[CardDetails] = None)
final case class MinorAdviser(card: CardDetails,
    placements: Vector[MinorAdviserPlacement])
final case class MinorActionsState(advisers: Vector[MinorAdviser],
    canPeekSiteRelics: Boolean, facedownRelics: Vector[CardDetails],
    siteId: Option[String], maxBoardToSite: Int, maxSiteToBoard: Int)
final case class NegotiationTransferInput(recipientPlayerId: String,
    favor: Int, relicIds: Vector[String])
final case class NegotiationDisclosureInput(recipientPlayerId: String,
    kind: String, ownerPlayerId: Option[String] = None,
    siteId: Option[String] = None, cardKind: Option[String] = None,
    cardId: String)
final case class NegotiationTermsInput(transfers: Vector[NegotiationTransferInput],
    disclosures: Vector[NegotiationDisclosureInput])
final case class NegotiationTransferState(authorPlayerId: String,
    recipientPlayerId: String, favor: Int, relicCount: Int,
    relics: Vector[CardDetails])
final case class NegotiationDisclosureState(authorPlayerId: String,
    recipientPlayerId: String, kind: String, card: Option[CardDetails])
final case class NegotiationState(decisionId: String, actorPlayerId: String,
    siteId: String, participantPlayerIds: Vector[String],
    acceptedPlayerIds: Vector[String], transfers: Vector[NegotiationTransferState],
    disclosures: Vector[NegotiationDisclosureState], editableFavor: Int,
    editableRelics: Vector[CardDetails], editableAdvisers: Vector[CardDetails],
    editableSiteRelics: Vector[CardDetails])
final case class PlayerBoard(
    playerId: String, warbands: Int, favor: Int, faceUpSecrets: Int,
    faceDownSecrets: Int, supply: Int, pawnSiteId: Option[String],
    advisers: Vector[CardDetails], relics: Vector[CardDetails],
    revealedVision: Option[CardDetails])
final case class GameProjection(
    gameId: String,
    nextSequence: Long,
    phase: String,
    activeParticipantId: Option[String],
    players: Vector[GamePlayer],
    world: Vector[GameRegion],
    pawnLocations: Vector[GamePawn],
    legalControls: Set[String],
    ready: Boolean,
    completed: Boolean,
    activePlayerResources: Option[ActivePlayerResources] = None,
    currentSiteResources: Option[CurrentSiteResources] = None,
    actionSelectionOpen: Boolean = false,
    actionFamilies: Vector[String] = Vector.empty,
    legalTravelDestinations: Vector[LegalTravelDestination] = Vector.empty,
    legalSearchSources: Vector[LegalSearchSource] = Vector.empty,
    legalMusters: Vector[LegalMuster] = Vector.empty,
    legalTrades: Vector[LegalTrade] = Vector.empty,
    boardTargetActions: Vector[BoardTargetAction] = Vector.empty,
    pendingCardDecision: Option[PendingCardDecision] = None,
    recover: Option[RecoverState] = None,
    campaign: Option[CampaignState] = None,
    worldDeckCount: Int = 0,
    worldDeckTopCardKind: Option[String] = None,
    playerBoards: Vector[PlayerBoard] = Vector.empty,
    oathkeeper: Option[OathkeeperStatus] = None,
    oathkeeperRecipient: Option[OathkeeperRecipientDecision] = None,
    campaignRaidRelocation: Option[CampaignRaidRelocation] = None,
    forge: Option[ForgeState] = None,
    banners: Vector[BannerState] = Vector.empty,
    challenge: Option[ChallengeState] = None,
    minorActions: Option[MinorActionsState] = None,
    negotiation: Option[NegotiationState] = None,
    negotiationWaiting: Boolean = false
)
final case class RecoverState(decisionId: String, dice: Vector[String],
    shields: Int, difficulty: Int, supplySpent: Int, supplyRemaining: Int,
    canAddDice: Boolean, canStop: Boolean)
final case class CampaignState(decisionId: String, targetSiteIds: Vector[String], force: Int,
    plansFinished: Boolean, planChoices: Vector[CampaignPlanChoice],
    selectedPlans: Vector[CampaignPlanChoice],
    attackDice: Vector[String], attack: Int, skullLosses: Int,
    maxSacrifice: Int, sacrificed: Option[Int], defenseDice: Vector[String],
    defense: Option[Int], victorious: Option[Boolean], maxPlacement: Int,
    placementTargets: Vector[CampaignPlacementTarget],
    defenderKind: String = "bandits", defenderPlayerId: Option[String] = None,
    defenderForce: Int = 0, defenseDiceCount: Int = 0,
    planSide: String = "attacker", decisionOwnerPlayerId: Option[String] = None,
    kind: String = "conquest", raidTargets: Vector[String] = Vector.empty)
final case class CampaignRaidRelocation(decisionId: String, actorPlayerId: String,
    defenderPlayerId: String, originSiteId: String, legalSiteIds: Vector[String])
final case class CampaignPlacementTarget(siteId: String, label: String)
object CampaignState {
  def apply(decisionId: String, siteId: String, force: Int,
      plansFinished: Boolean, planChoices: Vector[CampaignPlanChoice],
      selectedPlans: Vector[CampaignPlanChoice], attackDice: Vector[String],
      attack: Int, skullLosses: Int, maxSacrifice: Int, sacrificed: Option[Int],
      defenseDice: Vector[String], defense: Option[Int],
      victorious: Option[Boolean], maxPlacement: Int): CampaignState =
    new CampaignState(decisionId, Vector(siteId), force, plansFinished,
      planChoices, selectedPlans, attackDice, attack, skullLosses, maxSacrifice,
      sacrificed, defenseDice, defense, victorious, maxPlacement,
      Vector(CampaignPlacementTarget(siteId, siteId)), "bandits", None, 0, 0)
}
final case class CampaignPlanChoice(kind: String, sourceKey: Option[String],
    playerId: Option[String], siteId: Option[String], cardId: Option[String],
    label: String, handlerId: Option[String], favorCost: Int, secretCost: Int,
    mechanicalResult: String)
final case class OathkeeperStatus(goal: String, holderPlayerId: Option[String],
    side: String, usurperLimited: Boolean, winnerPlayerId: Option[String],
    winnerVictoryKind: Option[String] = None)
final case class OathkeeperRecipientDecision(decisionId: String,
    actorPlayerId: String, candidatePlayerIds: Vector[String])

sealed trait GameCommand
object GameCommand {
  final case class PlacePawn(playerId: String, siteId: String)
      extends GameCommand
  final case class ChooseAdviser(playerId: String, adviserId: String)
      extends GameCommand
  final case class TakeWealth(playerId: String, resource: String)
      extends GameCommand
  final case class EndWake(playerId: String) extends GameCommand
  final case class BeginRest(playerId: String) extends GameCommand
  final case class FinishRest(playerId: String) extends GameCommand
  final case class Travel(playerId: String, destinationSiteId: String)
      extends GameCommand
  final case class CampaignConquest(playerId: String, targetSiteIds: Vector[String],
      attackDiceCount: Int)
      extends GameCommand
  object CampaignConquest {
    def apply(playerId: String, targetSiteId: String,
        attackDiceCount: Int): CampaignConquest =
      new CampaignConquest(playerId, Vector(targetSiteId), attackDiceCount)
  }
  final case class CampaignRaid(playerId: String, targets: Vector[BoardTargetRef],
      attackDiceCount: Int) extends GameCommand
  final case class ChooseCampaignPlan(playerId: String, decisionId: String,
      choice: CampaignPlanChoice) extends GameCommand
  final case class FinishCampaignPlans(playerId: String, decisionId: String)
      extends GameCommand
  final case class ChooseCampaignSacrifice(playerId: String, decisionId: String,
      count: Int) extends GameCommand
  final case class PlaceCampaignForce(playerId: String, decisionId: String,
      allocations: Vector[CampaignPlacement]) extends GameCommand
  final case class RelocateCampaignRaidPawn(playerId: String, decisionId: String,
      destinationSiteId: String) extends GameCommand
  final case class ChooseOathkeeperRecipient(playerId: String,
      decisionId: String, recipientPlayerId: String) extends GameCommand
  final case class RevealVision(playerId: String, visionId: String) extends GameCommand
  final case class PlayConspiracy(playerId: String,
      target: Option[ConspiracyTarget]) extends GameCommand
  final case class ChooseConspiracySecretSite(playerId: String,
      decisionId: String, siteId: String) extends GameCommand
  final case class Muster(playerId: String, target: EconomyTarget) extends GameCommand
  final case class Trade(playerId: String, target: EconomyTarget, resource: String)
      extends GameCommand
  final case class BeginSearch(playerId: String, source: String, region: Option[String])
      extends GameCommand
  final case class BeginRecover(playerId: String) extends GameCommand
  final case class BeginForge(playerId: String) extends GameCommand
  final case class CompleteForge(playerId: String, decisionId: String,
      assignments: Vector[(ForgeTarget, String)]) extends GameCommand
  final case class BeginChallenge(playerId: String, banner: String) extends GameCommand
  final case class ChooseChallengeSecretSite(playerId: String, decisionId: String,
      siteId: String) extends GameCommand
  final case class CompleteChallenge(playerId: String, decisionId: String,
      amount: Int) extends GameCommand
  final case class PlaceBannerResource(playerId: String, banner: String,
      amount: Int) extends GameCommand
  final case class DiscardFacedownAdviser(playerId: String, adviser: CardDetails)
      extends GameCommand
  final case class PlayFacedownAdviser(playerId: String, adviser: CardDetails,
      placement: String, replacement: Option[CardDetails] = None) extends GameCommand
  final case class PeekSiteRelics(playerId: String) extends GameCommand
  final case class RevealOwnedRelic(playerId: String, relicId: String) extends GameCommand
  final case class MoveWarbands(playerId: String, toSite: Boolean, amount: Int)
      extends GameCommand
  final case class BeginNegotiation(playerId: String,
      participantPlayerIds: Vector[String]) extends GameCommand
  final case class ReplaceNegotiationTerms(playerId: String, decisionId: String,
      terms: NegotiationTermsInput) extends GameCommand
  final case class AcceptNegotiation(playerId: String, decisionId: String)
      extends GameCommand
  final case class DeclineNegotiation(playerId: String, decisionId: String)
      extends GameCommand
  final case class AddRecoverDice(playerId: String, decisionId: String) extends GameCommand
  final case class StopRecover(playerId: String, decisionId: String) extends GameCommand
  final case class CompleteSearch(
      playerId: String,
      decisionId: String,
      keptId: String,
      keptKind: String,
      discarded: Vector[(String, String)],
      placement: String,
      replace: Option[(String, String)] = None
  ) extends GameCommand
  final case class ResolveCardDecision(
      playerId: String, decisionId: String, resolution: DecisionResolution)
      extends GameCommand
}
sealed trait ConspiracyTarget
object ConspiracyTarget {
  final case class RelicSlot(ownerPlayerId: String, slot: Int)
      extends ConspiracyTarget
  final case class Banner(ownerPlayerId: String, banner: String)
      extends ConspiracyTarget
}
sealed trait DecisionResolution
object DecisionResolution {
  final case class StartingAdviser(adviserId: String) extends DecisionResolution
  final case class Search(kept: CardDetails, discarded: Vector[CardDetails],
      placement: String, orientation: Option[String],
      replacement: Option[CardDetails]) extends DecisionResolution
  final case class TakeFacedownRelic(relicId: String) extends DecisionResolution
}

sealed trait GameClientFailure {
  def message: String
}
object GameClientFailure {
  final case class NetworkFailure(message: String) extends GameClientFailure
  final case class RequestTimedOut(method: String, url: String, millis: Int)
      extends GameClientFailure {
    override val message: String =
      s"$method $url timed out after $millis ms"
  }
  final case class RequestAborted(method: String, url: String)
      extends GameClientFailure {
    override val message: String = s"$method $url was aborted"
  }
  final case class DecodeFailure(path: String, detail: String)
      extends GameClientFailure {
    override val message: String = s"$path: $detail"
  }
  final case class HttpFailure(status: Int, code: String, detail: String)
      extends GameClientFailure {
    override val message: String = s"HTTP $status $code: $detail"
  }
  final case class StalePosition(detail: String)
      extends GameClientFailure {
    override val message: String =
      s"Stale position; refreshed without retrying. $detail"
  }

  def isTransient(failure: GameClientFailure): Boolean = failure match {
    case _: NetworkFailure | _: RequestTimedOut | _: RequestAborted => true
    case _ => false
  }
}

final case class BootstrapPlayer(
    playerId: String,
    lineageId: String,
    color: String
)
final case class FirstGameBootstrap(
    players: Vector[BootstrapPlayer],
    firstPlayer: String
)

trait GameClient {
  def bootstrap(
      gameId: String,
      selectedPlayerId: String,
      config: FirstGameBootstrap
  ): Future[Either[GameClientFailure, GameProjection]]
  def load(
      gameId: String,
      selectedPlayerId: String
  ): Future[Either[GameClientFailure, GameProjection]]
  def submit(
      gameId: String,
      selectedPlayerId: String,
      expectedNextSequence: Long,
      command: GameCommand
  ): Future[Either[GameClientFailure, GameProjection]]
}

final class HttpGameClient(transport: JsonTransport)
    extends GameClient {
  override def bootstrap(
      gameId: String,
      selectedPlayerId: String,
      config: FirstGameBootstrap
  ) =
    send(
      "POST",
      s"/api/dev/first-games/${encode(gameId)}/bootstrap?playerId=" +
        encode(selectedPlayerId),
      Some(GameJson.encodeBootstrap(config))
    )

  override def load(gameId: String, selectedPlayerId: String) =
    send(
      "GET",
      s"/api/dev/first-games/${encode(gameId)}?playerId=" +
        encode(selectedPlayerId),
      None
    )

  override def submit(
      gameId: String,
      selectedPlayerId: String,
      expectedNextSequence: Long,
      command: GameCommand
  ) =
    send(
      "POST",
      s"/api/dev/first-games/${encode(gameId)}/commands?playerId=" +
        encode(selectedPlayerId),
      Some(GameJson.encodeCommand(expectedNextSequence, command))
    )

  def loadRawEventHistory(gameId: String, limit: Int = 25)
      : Future[Either[GameClientFailure, Vector[RawEvent]]] =
    transport.request("GET",
      s"/api/dev/first-games/${encode(gameId)}/events?limit=$limit", None).map {
      _.flatMap { response =>
        if (response.status < 200 || response.status >= 300)
          Left(GameClientFailure.HttpFailure(response.status, "event-history",
            response.body))
        else try {
          val root = js.JSON.parse(response.body)
          val events = root.selectDynamic("events").asInstanceOf[js.Array[js.Dynamic]]
          Right(events.toVector.map { event =>
            val sequence = event.selectDynamic("sequence").asInstanceOf[Double].toLong
            val discriminator = event.selectDynamic("eventType").asInstanceOf[String]
            RawEvent(sequence, discriminator, js.JSON.stringify(event))
          })
        } catch {
          case NonFatal(error) => Left(GameClientFailure.DecodeFailure("$.events",
            Option(error.getMessage).getOrElse("invalid event history")))
        }
      }
    }(scala.scalajs.concurrent.JSExecutionContext.queue)

  private def send(method: String, url: String, body: Option[String]) =
    transport.request(method, url, body).map(_.flatMap { response =>
      if (response.status >= 200 && response.status < 300)
        GameJson.decodeProjection(response.body)
      else
        GameJson.decodeError(response.body).fold(
          _ => Left(GameClientFailure.HttpFailure(
            response.status,
            "invalid-error-response",
            response.body
          )),
          error =>
            if (response.status == 409)
              Left(GameClientFailure.StalePosition(error._2))
            else
              Left(GameClientFailure.HttpFailure(
                response.status,
                error._1,
                error._2
              ))
        )
    })(scala.scalajs.concurrent.JSExecutionContext.queue)

  private def encode(value: String): String =
    js.URIUtils.encodeURIComponent(value)
}

object GameJson {
  private val MaxJsonSafeInteger = 9007199254740991d

  def encodeBootstrap(config: FirstGameBootstrap): String =
    js.JSON.stringify(js.Dynamic.literal(
      expectedNextSequence = 0,
      participants = js.Array(config.players.map(player =>
        js.Dynamic.literal(
          playerId = player.playerId,
          lineageId = player.lineageId,
          color = player.color
        )): _*),
      firstPlayer = config.firstPlayer
    ))

  def encodeCommand(sequence: Long, command: GameCommand): String = {
    val payload = command match {
      case GameCommand.PlacePawn(player, site) =>
        js.Dynamic.literal(
          `type` = "placePawn",
          playerId = player,
          siteId = site
        )
      case GameCommand.ChooseAdviser(player, adviser) =>
        js.Dynamic.literal(
          `type` = "chooseAdviser",
          playerId = player,
          adviserId = adviser
        )
      case GameCommand.TakeWealth(player, resource) =>
        js.Dynamic.literal(
          `type` = "takeWealth",
          playerId = player,
          resource = resource
        )
      case GameCommand.EndWake(player) =>
        js.Dynamic.literal(`type` = "endWake", playerId = player)
      case GameCommand.BeginRest(player) =>
        js.Dynamic.literal(`type` = "beginRest", playerId = player)
      case GameCommand.FinishRest(player) =>
        js.Dynamic.literal(`type` = "finishRest", playerId = player)
      case GameCommand.Travel(player, destination) =>
        js.Dynamic.literal(
          `type` = "travel",
          playerId = player,
          destinationSiteId = destination
        )
      case GameCommand.CampaignConquest(player, targets, count) =>
        js.Dynamic.literal(
          `type` = "beginCampaignConquest",
          playerId = player,
          targetSiteIds = js.Array(targets: _*),
          attackDiceCount = count
        )
      case GameCommand.CampaignRaid(player, targets, count) =>
        val encodedTargets = targets.map {
          case BoardTargetRef.PlayerPawn(id) =>
            js.Dynamic.literal(kind = "pawn", playerId = id)
          case BoardTargetRef.PlayerRelic(id, relic) =>
            js.Dynamic.literal(kind = "relic", playerId = id, relicId = relic)
          case BoardTargetRef.PlayerBanner(id, banner) =>
            js.Dynamic.literal(kind = banner, playerId = id)
          case target => throw new IllegalArgumentException(
            s"unsupported Campaign Raid target ${target.stableKey}")
        }
        js.Dynamic.literal(`type` = "beginCampaignRaid", playerId = player,
          targets = js.Array(encodedTargets: _*), attackDiceCount = count)
      case GameCommand.ChooseCampaignPlan(player, decision, choice) =>
        val source: js.Any = choice.kind match {
          case "adviser" => js.Dynamic.literal(kind = "adviser",
            playerId = choice.playerId.get, cardId = choice.cardId.get)
          case "site-card" => js.Dynamic.literal(kind = "site-card",
            siteId = choice.siteId.get, cardId = choice.cardId.get)
          case "relic" => js.Dynamic.literal(kind = "relic",
            playerId = choice.playerId.get, cardId = choice.cardId.get)
          case "title" => js.Dynamic.literal(kind = "title",
            playerId = choice.playerId.get)
        }
        js.Dynamic.literal(`type` = "chooseCampaignPlan", playerId = player,
          decisionId = decision, source = source)
      case GameCommand.FinishCampaignPlans(player, decision) =>
        js.Dynamic.literal(`type` = "finishCampaignPlans", playerId = player,
          decisionId = decision)
      case GameCommand.ChooseCampaignSacrifice(player, decision, count) =>
        js.Dynamic.literal(`type` = "chooseCampaignSacrifice",
          playerId = player, decisionId = decision, count = count)
      case GameCommand.PlaceCampaignForce(player, decision, allocations) =>
        js.Dynamic.literal(`type` = "placeCampaignForce",
          playerId = player, decisionId = decision,
          allocations = js.Array(allocations.map(allocation =>
            js.Dynamic.literal(siteId = allocation.siteId,
              count = allocation.count)): _*))
      case GameCommand.RelocateCampaignRaidPawn(player, decision, destination) =>
        js.Dynamic.literal(`type` = "relocateCampaignRaidPawn",
          playerId = player, decisionId = decision,
          destinationSiteId = destination)
      case GameCommand.ChooseOathkeeperRecipient(player, decision, recipient) =>
        js.Dynamic.literal(`type` = "chooseOathkeeperRecipient",
          playerId = player, decisionId = decision,
          recipientPlayerId = recipient)
      case GameCommand.RevealVision(player, vision) =>
        js.Dynamic.literal(`type` = "revealVision", playerId = player,
          visionId = vision)
      case GameCommand.PlayConspiracy(player, target) =>
        val encodedTarget: js.Any = target.fold[js.Any](null) {
          case ConspiracyTarget.RelicSlot(owner, slot) => js.Dynamic.literal(
            kind = "relic-slot", ownerPlayerId = owner, slot = slot)
          case ConspiracyTarget.Banner(owner, banner) => js.Dynamic.literal(
            kind = "banner", ownerPlayerId = owner, banner = banner)
        }
        js.Dynamic.literal(`type` = "playConspiracy", playerId = player,
          target = encodedTarget)
      case GameCommand.ChooseConspiracySecretSite(player, decision, site) =>
        js.Dynamic.literal(`type` = "chooseConspiracySecretSite",
          playerId = player, decisionId = decision, siteId = site)
      case GameCommand.Muster(player, target) =>
        js.Dynamic.literal(`type` = "muster", playerId = player,
          target = js.Dynamic.literal(kind = target.kind, id = target.id))
      case GameCommand.Trade(player, target, resource) =>
        js.Dynamic.literal(`type` = "trade", playerId = player,
          target = js.Dynamic.literal(kind = target.kind, id = target.id),
          resource = resource)
      case GameCommand.BeginSearch(player, source, region) =>
        val value = js.Dynamic.literal(
          `type` = "beginSearch", playerId = player, source = source)
        region.foreach(value.updateDynamic("region")(_))
        value
      case GameCommand.BeginRecover(player) =>
        js.Dynamic.literal(`type` = "beginRecover", playerId = player)
      case GameCommand.BeginForge(player) =>
        js.Dynamic.literal(`type` = "beginForge", playerId = player)
      case GameCommand.CompleteForge(player, decision, assignments) =>
        js.Dynamic.literal(`type` = "completeForge", playerId = player,
          decisionId = decision, assignments = js.Array(assignments.map {
            case (target, resource) => js.Dynamic.literal(
              siteId = target.siteId, denizenId = target.denizenId,
              resource = resource)
          }: _*))
      case GameCommand.BeginChallenge(player, banner) =>
        js.Dynamic.literal(`type` = "beginChallenge", playerId = player, banner = banner)
      case GameCommand.ChooseChallengeSecretSite(player, decision, site) =>
        js.Dynamic.literal(`type` = "chooseChallengeSecretSite", playerId = player,
          decisionId = decision, siteId = site)
      case GameCommand.CompleteChallenge(player, decision, amount) =>
        js.Dynamic.literal(`type` = "completeChallenge", playerId = player,
          decisionId = decision, amount = amount)
      case GameCommand.PlaceBannerResource(player, banner, amount) =>
        js.Dynamic.literal(`type` = "placeBannerResource", playerId = player,
          banner = banner, amount = amount)
      case GameCommand.DiscardFacedownAdviser(player, adviser) =>
        js.Dynamic.literal(`type` = "discardFacedownAdviser", playerId = player,
          adviser = js.Dynamic.literal(kind = adviser.cardKind, id = adviser.cardId))
      case GameCommand.PlayFacedownAdviser(player, adviser, placement, replacement) =>
        val placementValue = js.Dynamic.literal(kind = placement)
        replacement.foreach(card => placementValue.updateDynamic("replace")(
          js.Dynamic.literal(kind = card.cardKind, id = card.cardId)))
        js.Dynamic.literal(`type` = "playFacedownAdviser", playerId = player,
          adviser = js.Dynamic.literal(kind = adviser.cardKind, id = adviser.cardId),
          placement = placementValue)
      case GameCommand.PeekSiteRelics(player) =>
        js.Dynamic.literal(`type` = "peekSiteRelics", playerId = player)
      case GameCommand.RevealOwnedRelic(player, relic) =>
        js.Dynamic.literal(`type` = "revealOwnedRelic", playerId = player, relicId = relic)
      case GameCommand.MoveWarbands(player, toSite, amount) =>
        js.Dynamic.literal(`type` = "moveWarbands", playerId = player,
          toSite = toSite, amount = amount)
      case GameCommand.BeginNegotiation(player, participants) =>
        js.Dynamic.literal(`type` = "beginNegotiation", playerId = player,
          participantPlayerIds = js.Array(participants: _*))
      case GameCommand.ReplaceNegotiationTerms(player, decision, terms) =>
        js.Dynamic.literal(`type` = "replaceNegotiationTerms", playerId = player,
          decisionId = decision, terms = encodeNegotiationTerms(terms))
      case GameCommand.AcceptNegotiation(player, decision) =>
        js.Dynamic.literal(`type` = "acceptNegotiation", playerId = player,
          decisionId = decision)
      case GameCommand.DeclineNegotiation(player, decision) =>
        js.Dynamic.literal(`type` = "declineNegotiation", playerId = player,
          decisionId = decision)
      case GameCommand.AddRecoverDice(player, decision) =>
        js.Dynamic.literal(`type` = "addRecoverDice", playerId = player,
          decisionId = decision)
      case GameCommand.StopRecover(player, decision) =>
        js.Dynamic.literal(`type` = "stopRecover", playerId = player,
          decisionId = decision)
      case GameCommand.CompleteSearch(player, decision, keptId, keptKind,
          discarded, placement, replace) =>
        val placementValue = js.Dynamic.literal(`kind` = placement)
        replace.foreach { case (kind, id) => placementValue.updateDynamic("replace")(
          js.Dynamic.literal(kind = kind, id = id)) }
        js.Dynamic.literal(
          `type` = "completeSearch",
          playerId = player,
          decisionId = decision,
          kept = js.Dynamic.literal(kind = keptKind, id = keptId),
          discardedInOrder = js.Array(discarded.map { case (kind, id) =>
            js.Dynamic.literal(kind = kind, id = id) }: _*),
          placement = placementValue
        )
      case GameCommand.ResolveCardDecision(player, decision, resolution) =>
        val value = resolution match {
          case DecisionResolution.StartingAdviser(id) => js.Dynamic.literal(
            kind = "starting-adviser", adviserId = id)
          case DecisionResolution.Search(kept, discarded, placement, orientation,
              replacement) =>
            val placementValue = js.Dynamic.literal(kind = placement)
            orientation.foreach(value => placementValue.updateDynamic("orientation")(value))
            replacement.foreach(card => placementValue.updateDynamic("replace")(
              js.Dynamic.literal(kind = card.cardKind, id = card.cardId)))
            js.Dynamic.literal(kind = "search",
              kept = js.Dynamic.literal(kind = kept.cardKind, id = kept.cardId),
              discardedInOrder = js.Array(discarded.map(card =>
                js.Dynamic.literal(kind = card.cardKind, id = card.cardId)): _*),
              placement = placementValue)
          case DecisionResolution.TakeFacedownRelic(id) =>
            js.Dynamic.literal(kind = "take-facedown-relic", relicId = id)
        }
        js.Dynamic.literal(`type` = "resolveCardDecision", playerId = player,
          decisionId = decision, resolution = value)
    }
    js.Dynamic.global.Reflect.deleteProperty(payload, "playerId")
    ActorlessCommandCodec.encode(ActorlessCommandRequest(sequence,
      ujson.read(js.JSON.stringify(payload)).obj))
  }

  private def encodeNegotiationTerms(terms: NegotiationTermsInput): js.Dynamic =
    js.Dynamic.literal(
      transfers = js.Array(terms.transfers.map(t => js.Dynamic.literal(
        recipientPlayerId = t.recipientPlayerId, favor = t.favor,
        relicIds = js.Array(t.relicIds: _*))): _*),
      disclosures = js.Array(terms.disclosures.map { d =>
        val information = d.kind match {
          case "adviser" => js.Dynamic.literal(kind = d.kind,
            ownerPlayerId = d.ownerPlayerId.get,
            card = js.Dynamic.literal(kind = d.cardKind.get, id = d.cardId))
          case "held-relic" => js.Dynamic.literal(kind = d.kind,
            ownerPlayerId = d.ownerPlayerId.get, relicId = d.cardId)
          case "site-relic" => js.Dynamic.literal(kind = d.kind,
            siteId = d.siteId.get, relicId = d.cardId)
        }
        js.Dynamic.literal(recipientPlayerId = d.recipientPlayerId,
          information = information)
      }: _*))

  def decodeProjection(
      json: String
  ): Either[GameClientFailure, GameProjection] = safely {
    parseObject(json).flatMap { root =>
      for {
        game <- string(root, "gameId", "$")
        sequence <- long(root, "nextSequence", "$")
        phase <- string(root, "phase", "$")
        active <- optionalString(root, "activeParticipantId", "$")
        players <- array(root, "players", "$").flatMap(traverse(_, "players") {
          (item, path) =>
            for {
              id <- string(item, "playerId", path)
              name <- string(item, "displayName", path)
              role <- string(item, "role", path)
              color <- string(item, "colorToken", path)
            } yield GamePlayer(id, name, role, colorToken(color))
        })
        world <- array(root, "world", "$").flatMap(traverse(_, "world") {
          (item, path) =>
            for {
              id <- string(item, "regionId", path)
              discardCount <- optionalField(item, "discardCount").flatMap {
                case None => Right(0)
                case Some(_) => int(item, "discardCount", path)
              }
              discardTopValue <- optionalField(item, "discardTopCardKind")
              discardTop <- discardTopValue match {
                case None => Right(None)
                case Some(value) if value == null => Right(None)
                case Some(_) => optionalString(item, "discardTopCardKind", path)
              }
              sites <- array(item, "sites", path).flatMap(traverse(_, "sites") {
                (site, sitePath) =>
                  for {
                    siteId <- string(site, "siteId", sitePath)
                    label <- string(site, "label", sitePath)
                    looseFavor <- int(site, "looseFavor", sitePath)
                    looseSecrets <- int(site, "looseSecrets", sitePath)
                    denizenCapacity <- int(site, "denizenCapacity", sitePath)
                    relicCapacity <- int(site, "relicCapacity", sitePath)
                    denizens <- array(site, "denizens", sitePath).flatMap(
                      traverse(_, "denizens") { (denizen, denizenPath) =>
                        for {
                          id <- string(denizen, "denizenId", denizenPath)
                          name <- string(denizen, "label", denizenPath)
                          detailsValue <- optionalField(denizen, "details")
                          details <- detailsValue match {
                            case None => Right(None)
                            case Some(value) if value == null => Right(None)
                            case Some(value) => cardDetails(value,
                              s"$denizenPath.details").map(Some(_))
                          }
                        } yield GameSiteCard(id, name, details)
                      })
                    relicsValue <- field(site, "relics", sitePath)
                    relicsObject <- objectValue(
                      relicsValue,
                      s"$sitePath.relics"
                    )
                    facedownCount <- int(
                      relicsObject,
                      "facedownCount",
                      s"$sitePath.relics"
                    )
                    knownRelics <- optionalField(relicsObject, "knownRelics").flatMap {
                      case None => Right(Vector.empty)
                      case Some(_) => array(relicsObject, "knownRelics",
                        s"$sitePath.relics").flatMap(traverse(_, "knownRelics")(
                        (card, cardPath) => cardDetails(card, cardPath)))
                    }
                    defense <- optionalField(site, "defense").flatMap {
                      case None => Right(0)
                      case Some(_) => int(site, "defense", sitePath)
                    }
                    recoverValue <- optionalField(site, "recoverDifficulty")
                    recover <- recoverValue match {
                      case None => Right(None)
                      case Some(value) if value == null => Right(None)
                      case Some(value) if js.typeOf(value) == "number" =>
                        Right(Some(value.asInstanceOf[Double].toInt))
                      case _ => Left(GameClientFailure.DecodeFailure(
                        s"$sitePath.recoverDifficulty", "expected integer or null"))
                    }
                    forgeValue <- optionalField(site, "forgeCost")
                    forge <- forgeValue match {
                      case None => Right(None)
                      case Some(value) if value == null => Right(None)
                      case Some(value) => objectValue(value, s"$sitePath.forgeCost").flatMap { obj =>
                        for {
                          favor <- int(obj, "favor", s"$sitePath.forgeCost")
                          secrets <- int(obj, "secrets", s"$sitePath.forgeCost")
                        } yield Some(ForgeCost(favor, secrets))
                      }
                    }
                    powers <- optionalField(site, "powers").flatMap {
                      case None => Right(Vector.empty)
                      case Some(_) => array(site, "powers", sitePath).flatMap(
                      traverse(_, "powers") { (power, powerPath) => for {
                        kind <- string(power, "kind", powerPath)
                        label <- string(power, "label", powerPath)
                        description <- optionalString(power, "description", powerPath)
                      } yield SitePower(kind, label, description) }) }
                    forcesValue <- field(site, "forces", sitePath)
                    forces <- if (forcesValue == null) Right(None) else
                      objectValue(forcesValue, s"$sitePath.forces").flatMap { obj =>
                        for {
                          kind <- string(obj, "forceKind", s"$sitePath.forces")
                          _ <- Either.cond(Set("exile", "imperial", "bandit").contains(kind), (),
                            GameClientFailure.DecodeFailure(s"$sitePath.forces.forceKind",
                              s"unsupported force kind '$kind'"))
                          count <- int(obj, "count", s"$sitePath.forces")
                          _ <- Either.cond(count > 0, (), GameClientFailure.DecodeFailure(
                            s"$sitePath.forces.count", "expected positive integer"))
                          ruler <- string(obj, "rulerKind", s"$sitePath.forces")
                          _ <- Either.cond(Set("player", "empire", "bandit").contains(ruler), (),
                            GameClientFailure.DecodeFailure(s"$sitePath.forces.rulerKind",
                              s"unsupported ruler kind '$ruler'"))
                          rulerPlayer <- optionalString(obj, "rulerPlayerId", s"$sitePath.forces")
                          _ <- Either.cond((ruler == "player") == rulerPlayer.nonEmpty, (),
                            GameClientFailure.DecodeFailure(s"$sitePath.forces.rulerPlayerId",
                              "player ruler requires an ID and shared rulers forbid one"))
                          forceLabel <- string(obj, "label", s"$sitePath.forces")
                          color <- string(obj, "colorToken", s"$sitePath.forces")
                          _ <- Either.cond(
                            (kind, ruler, color) match {
                              case ("exile", "player",
                                  "red" | "blue" | "yellow" | "purple") => true
                              case ("imperial", "empire", "empire") => true
                              case ("bandit", "bandit", "bandit") => true
                              case _ => false
                            }, (), GameClientFailure.DecodeFailure(
                              s"$sitePath.forces",
                              "force, ruler, and color tokens do not agree"))
                        } yield Some(SiteForces(kind, count, ruler, rulerPlayer,
                          forceLabel, color))
                      }
                  } yield GameSite(
                    siteId,
                    label,
                    looseFavor,
                    looseSecrets,
                    denizenCapacity,
                    relicCapacity,
                    denizens,
                    GameSiteRelics(facedownCount, knownRelics),
                    defense,
                    recover,
                    forge,
                    powers,
                    forces
                  )
              })
            } yield GameRegion(id, sites, discardCount, discardTop)
        })
        pawns <- array(root, "pawnLocations", "$").flatMap(
          traverse(_, "pawnLocations") { (item, path) =>
            for {
              player <- string(item, "playerId", path)
              site <- string(item, "siteId", path)
            } yield GamePawn(player, site)
          })
        controls <- stringArray(root, "legalControls", "$")
        ready <- bool(root, "ready", "$")
        completed <- bool(root, "completed", "$")
        oathkeeper <- optionalField(root, "oathkeeper").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.oathkeeper").flatMap { obj => for {
            goal <- string(obj, "goal", "$.oathkeeper")
            holder <- optionalString(obj, "holderPlayerId", "$.oathkeeper")
            side <- string(obj, "side", "$.oathkeeper")
            limited <- bool(obj, "usurperLimited", "$.oathkeeper")
            winner <- optionalString(obj, "winnerPlayerId", "$.oathkeeper")
            kind <- optionalString(obj, "winnerVictoryKind", "$.oathkeeper")
          } yield Some(OathkeeperStatus(goal, holder, side, limited, winner, kind)) }
        }
        oathkeeperRecipient <- optionalField(root, "oathkeeperRecipient").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value,
            "$.oathkeeperRecipient").flatMap { obj => for {
              decision <- string(obj, "decisionId", "$.oathkeeperRecipient")
              actor <- string(obj, "actorPlayerId", "$.oathkeeperRecipient")
              candidates <- stringArray(obj, "candidatePlayerIds",
                "$.oathkeeperRecipient")
              _ <- Either.cond(candidates.nonEmpty &&
                candidates.distinct.size == candidates.size, (),
                GameClientFailure.DecodeFailure(
                  "$.oathkeeperRecipient.candidatePlayerIds",
                  "expected distinct recipient candidates"))
            } yield Some(OathkeeperRecipientDecision(
              decision, actor, candidates)) }
        }
        resources <- optionalField(root, "activePlayerResources").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => for {
            obj <- objectValue(value, "$.activePlayerResources")
            favor <- int(obj, "favor", "$.activePlayerResources")
            up <- int(obj, "faceUpSecrets", "$.activePlayerResources")
            down <- int(obj, "faceDownSecrets", "$.activePlayerResources")
            supply <- int(obj, "supply", "$.activePlayerResources")
          } yield Some(ActivePlayerResources(favor, up, down, supply))
        }
        siteResources <- optionalField(root, "currentSiteResources").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => for {
            obj <- objectValue(value, "$.currentSiteResources")
            site <- string(obj, "siteId", "$.currentSiteResources")
            favor <- int(obj, "favor", "$.currentSiteResources")
            secrets <- int(obj, "secrets", "$.currentSiteResources")
          } yield Some(CurrentSiteResources(site, favor, secrets))
        }
        actionOpen <- optionalField(root, "actionSelectionOpen").flatMap {
          case None => Right(false)
          case Some(value) if js.typeOf(value) == "boolean" =>
            Right(value.asInstanceOf[Boolean])
          case _ => Left(GameClientFailure.DecodeFailure(
            "$.actionSelectionOpen", "expected boolean"))
        }
        actions <- optionalField(root, "actionFamilies").flatMap {
          case None => Right(Vector.empty)
          case Some(_) => stringArray(root, "actionFamilies", "$")
        }
        destinations <- optionalField(root, "legalTravelDestinations").flatMap {
          case None => Right(Vector.empty)
          case Some(_) => array(root, "legalTravelDestinations", "$.").flatMap(
            traverse(_, "legalTravelDestinations") { (item, path) =>
              for {
                site <- string(item, "siteId", path)
                cost <- int(item, "supplyCost", path)
              } yield LegalTravelDestination(site, cost)
            })
        }
        searchSources <- optionalField(root, "legalSearchSources").flatMap {
          case None => Right(Vector.empty)
          case Some(_) => array(root, "legalSearchSources", "$").flatMap(
            traverse(_, "legalSearchSources") { (item, path) => for {
              kind <- string(item, "kind", path)
              region <- optionalString(item, "region", path)
              cost <- int(item, "supplyCost", path)
            } yield LegalSearchSource(kind, region, cost) })
        }
        musters <- optionalField(root, "legalMusters").flatMap {
          case None => Right(Vector.empty)
          case Some(_) => array(root, "legalMusters", "$").flatMap(
            traverse(_, "legalMusters") { (item, path) => for {
              target <- economyTarget(item, path)
              label <- string(item, "label", path)
              suit <- string(item, "suit", path)
              cost <- int(item, "supplyCost", path)
              gained <- int(item, "warbandsGained", path)
            } yield LegalMuster(target, label, suit, cost, gained) })
        }
        trades <- optionalField(root, "legalTrades").flatMap {
          case None => Right(Vector.empty)
          case Some(_) => array(root, "legalTrades", "$").flatMap(
            traverse(_, "legalTrades") { (item, path) => for {
              target <- economyTarget(item, path)
              label <- string(item, "label", path)
              suit <- string(item, "suit", path)
              resource <- string(item, "resource", path)
              cost <- int(item, "supplyCost", path)
              gained <- int(item, "gained", path)
            } yield LegalTrade(target, label, suit, resource, cost, gained) })
        }
        boardActions <- decodeBoardTargetActions(root)
        pendingDecision <- optionalField(root, "pendingCardDecision").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.pendingCardDecision").flatMap { obj => for {
            id <- string(obj, "decisionId", "$.pendingCardDecision")
            kind <- string(obj, "kind", "$.pendingCardDecision")
            actor <- string(obj, "actorPlayerId", "$.pendingCardDecision")
            prompt <- string(obj, "prompt", "$.pendingCardDecision")
            instructions <- stringArray(obj, "instructions", "$.pendingCardDecision")
            cards <- array(obj, "cards", "$.pendingCardDecision").flatMap(
              traverse(_, "cards")((card, path) => cardDetails(card, path)))
            minimum <- int(obj, "keepMinimum", "$.pendingCardDecision")
            maximum <- int(obj, "keepMaximum", "$.pendingCardDecision")
            ordering <- bool(obj, "orderingRequired", "$.pendingCardDecision")
            resolutionsValue <- field(obj, "resolutionsByCard", "$.pendingCardDecision")
            resolutionsObject <- objectValue(resolutionsValue,
              "$.pendingCardDecision.resolutionsByCard")
            resolutions <- cards.foldLeft[Either[GameClientFailure,
              Map[String, Vector[CardResolution]]]](Right(Map.empty)) {
              case (Right(acc), card) =>
                val raw = resolutionsObject.selectDynamic(card.cardId)
                if (js.isUndefined(raw)) Right(acc.updated(card.cardId, Vector.empty))
                else raw.asInstanceOf[js.Array[js.Dynamic]].toVector.zipWithIndex
                  .foldLeft[Either[GameClientFailure, Vector[CardResolution]]](Right(Vector.empty)) {
                    case (Right(values), (item, index)) =>
                      val path = s"$$.pendingCardDecision.resolutionsByCard.${card.cardId}[$index]"
                      for {
                        kind <- string(item, "kind", path)
                        orientation <- optionalString(item, "orientation", path)
                        replacementRequired <- bool(item,
                          "replacementRequired", path)
                        targets <- array(item, "replacementTargets", path).flatMap(
                          traverse(_, "replacementTargets")((target, targetPath) =>
                            cardDetails(target, targetPath)))
                      } yield values :+ CardResolution(kind, orientation,
                        replacementRequired, targets)
                    case (failure @ Left(_), _) => failure
                  }.map(value => acc.updated(card.cardId, value))
              case (failure @ Left(_), _) => failure
            }
          } yield Some(PendingCardDecision(id, kind, actor, prompt, instructions,
            cards, minimum, maximum, ordering, resolutions)) }
        }
        recover <- optionalField(root, "recover").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.recover").flatMap { obj => for {
            id <- string(obj, "decisionId", "$.recover")
            dice <- stringArray(obj, "dice", "$.recover")
            shields <- int(obj, "shields", "$.recover")
            difficulty <- int(obj, "difficulty", "$.recover")
            spent <- int(obj, "supplySpent", "$.recover")
            remaining <- int(obj, "supplyRemaining", "$.recover")
            add <- bool(obj, "canAddDice", "$.recover")
            stop <- bool(obj, "canStop", "$.recover")
          } yield Some(RecoverState(id, dice, shields, difficulty, spent,
            remaining, add, stop)) }
        }
        forge <- optionalField(root, "forge").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.forge").flatMap { obj => for {
            id <- string(obj, "decisionId", "$.forge")
            actor <- string(obj, "actorPlayerId", "$.forge")
            favor <- int(obj, "favor", "$.forge")
            secrets <- int(obj, "secrets", "$.forge")
            targets <- array(obj, "targets", "$.forge").flatMap(
              traverse(_, "forge.targets") { (target, path) => for {
                site <- string(target, "siteId", path)
                denizen <- string(target, "denizenId", path)
                label <- string(target, "label", path)
              } yield ForgeTarget(site, denizen, label) })
          } yield Some(ForgeState(id, actor, favor, secrets, targets)) }
        }
        banners <- optionalField(root, "banners").flatMap {
          case None => Right(Vector.empty)
          case Some(_) => array(root, "banners", "$").flatMap(traverse(_, "banners") {
            (value, path) => for {
              banner <- string(value, "banner", path)
              face <- string(value, "face", path)
              holder <- optionalString(value, "holderPlayerId", path)
              resources <- int(value, "resources", path)
            } yield BannerState(banner, face, holder, resources)
          })
        }
        challenge <- optionalField(root, "challenge").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.challenge").flatMap { obj => for {
            id <- string(obj, "decisionId", "$.challenge")
            actor <- string(obj, "actorPlayerId", "$.challenge")
            banner <- string(obj, "banner", "$.challenge")
            holder <- optionalString(obj, "priorHolderPlayerId", "$.challenge")
            prior <- int(obj, "priorResources", "$.challenge")
            sites <- stringArray(obj, "legalSecretSiteIds", "$.challenge")
            minimum <- int(obj, "minimumPlacement", "$.challenge")
            maximum <- int(obj, "maximumPlacement", "$.challenge")
          } yield Some(ChallengeState(id, actor, banner, holder, prior,
            sites, minimum, maximum)) }
        }
        minorActions <- optionalField(root, "minorActions").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.minorActions").flatMap { obj => for {
            advisers <- array(obj, "advisers", "$.minorActions").flatMap(
              traverse(_, "minorActions.advisers") { (entry, path) => for {
                cardValue <- field(entry, "card", path)
                card <- cardDetails(cardValue, s"$path.card")
                placements <- array(entry, "placements", path).flatMap(
                  traverse(_, "placements") { (placement, placementPath) => for {
                    kind <- string(placement, "kind", placementPath)
                    replacements <- array(placement, "replacementTargets", placementPath)
                      .flatMap(traverse(_, "replacementTargets")(
                        (target, targetPath) => cardDetails(target, targetPath)))
                    _ <- Either.cond(replacements.size <= 1, (),
                      GameClientFailure.DecodeFailure(placementPath,
                        "minor adviser placement has at most one replacement"))
                  } yield MinorAdviserPlacement(kind, replacements.headOption) })
              } yield MinorAdviser(card, placements) })
            canPeek <- field(obj, "canPeekSiteRelics", "$.minorActions").flatMap {
              case value if js.typeOf(value) == "boolean" => Right(value.asInstanceOf[Boolean])
              case _ => Left(GameClientFailure.DecodeFailure(
                "$.minorActions.canPeekSiteRelics", "expected boolean"))
            }
            relics <- array(obj, "facedownRelics", "$.minorActions").flatMap(
              traverse(_, "minorActions.facedownRelics")(
                (card, path) => cardDetails(card, path)))
            site <- optionalString(obj, "siteId", "$.minorActions")
            toSite <- int(obj, "maxBoardToSite", "$.minorActions")
            toBoard <- int(obj, "maxSiteToBoard", "$.minorActions")
          } yield Some(MinorActionsState(advisers, canPeek, relics, site,
            toSite, toBoard)) }
        }
        negotiation <- optionalField(root, "negotiation").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.negotiation").flatMap { obj => for {
            decision <- string(obj, "decisionId", "$.negotiation")
            actor <- string(obj, "actorPlayerId", "$.negotiation")
            site <- string(obj, "siteId", "$.negotiation")
            participants <- stringArray(obj, "participantPlayerIds", "$.negotiation")
            accepted <- stringArray(obj, "acceptedPlayerIds", "$.negotiation")
            transfers <- array(obj, "transfers", "$.negotiation").flatMap(
              traverse(_, "negotiation.transfers") { (row, path) => for {
                author <- string(row, "authorPlayerId", path)
                recipient <- string(row, "recipientPlayerId", path)
                favor <- int(row, "favor", path)
                count <- int(row, "relicCount", path)
                relics <- array(row, "relics", path).flatMap(
                  traverse(_, "relics")((card, cardPath) => cardDetails(card, cardPath)))
              } yield NegotiationTransferState(author, recipient, favor, count, relics) })
            disclosures <- array(obj, "disclosures", "$.negotiation").flatMap(
              traverse(_, "negotiation.disclosures") { (row, path) => for {
                author <- string(row, "authorPlayerId", path)
                recipient <- string(row, "recipientPlayerId", path)
                kind <- string(row, "kind", path)
                card <- optionalField(row, "card").flatMap {
                  case None => Right(None)
                  case Some(value) if value == null => Right(None)
                  case Some(value) => cardDetails(value, s"$path.card").map(Some(_))
                }
              } yield NegotiationDisclosureState(author, recipient, kind, card) })
            favor <- int(obj, "editableFavor", "$.negotiation")
            relics <- array(obj, "editableRelics", "$.negotiation").flatMap(
              traverse(_, "editableRelics")((card, path) => cardDetails(card, path)))
            advisers <- array(obj, "editableAdvisers", "$.negotiation").flatMap(
              traverse(_, "editableAdvisers")((card, path) => cardDetails(card, path)))
            siteRelics <- array(obj, "editableSiteRelics", "$.negotiation").flatMap(
              traverse(_, "editableSiteRelics")((card, path) => cardDetails(card, path)))
          } yield Some(NegotiationState(decision, actor, site, participants, accepted,
            transfers, disclosures, favor, relics, advisers, siteRelics)) }
        }
        negotiationWaiting <- optionalField(root, "negotiationWaiting").flatMap {
          case None => Right(false)
          case Some(value) if js.typeOf(value) == "boolean" => Right(value.asInstanceOf[Boolean])
          case _ => Left(GameClientFailure.DecodeFailure("$.negotiationWaiting",
            "expected boolean"))
        }
        campaign <- optionalField(root, "campaign").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.campaign").flatMap { obj => for {
            id <- string(obj, "decisionId", "$.campaign")
            kind <- optionalField(obj, "kind").flatMap {
              case None => Right("conquest")
              case Some(_) => string(obj, "kind", "$.campaign")
            }
            raidTargets <- optionalField(obj, "raidTargets").flatMap {
              case None => Right(Vector.empty)
              case Some(_) => stringArray(obj, "raidTargets", "$.campaign")
            }
            sites <- stringArray(obj, "targetSiteIds", "$.campaign")
            _ <- Either.cond(kind == "raid" ||
              (sites.nonEmpty && sites.distinct.size == sites.size),
              (), GameClientFailure.DecodeFailure("$.campaign.targetSiteIds",
                "Campaign targets must be non-empty and distinct"))
            _ <- Either.cond(kind == "conquest" ||
              (sites.isEmpty && raidTargets.nonEmpty &&
                raidTargets.distinct.size == raidTargets.size), (),
              GameClientFailure.DecodeFailure("$.campaign.raidTargets",
                "Raid targets must be non-empty and distinct"))
            force <- int(obj, "force", "$.campaign")
            plansFinished <- bool(obj, "plansFinished", "$.campaign")
            planChoices <- decodeCampaignPlanChoices(obj, "planChoices")
            selectedPlans <- decodeCampaignPlanChoices(obj, "selectedPlans")
            _ <- Either.cond(
              (planChoices ++ selectedPlans).flatMap(_.sourceKey).distinct.size ==
                planChoices.size + selectedPlans.size,
              (), GameClientFailure.DecodeFailure("$.campaign",
                "Campaign plan sources must not be both selected and available"))
            attackDice <- stringArray(obj, "attackDice", "$.campaign")
            attack <- int(obj, "attack", "$.campaign")
            skulls <- int(obj, "skullLosses", "$.campaign")
            maximumSacrifice <- int(obj, "maxSacrifice", "$.campaign")
            sacrificed <- optionalInt(obj, "sacrificed", "$.campaign")
            defenseDice <- stringArray(obj, "defenseDice", "$.campaign")
            defense <- optionalInt(obj, "defense", "$.campaign")
            victoriousValue <- optionalField(obj, "victorious")
            victorious <- victoriousValue match {
              case None => Right(None)
              case Some(value) if value == null => Right(None)
              case Some(_) => bool(obj, "victorious", "$.campaign").map(Some(_))
            }
            maximumPlacement <- int(obj, "maxPlacement", "$.campaign")
            placementTargets <- array(obj, "placementTargets", "$.campaign")
              .flatMap(traverse(_, "campaign.placementTargets") {
                (target, path) => for {
                  site <- string(target, "siteId", path)
                  label <- string(target, "label", path)
                } yield CampaignPlacementTarget(site, label)
              })
            _ <- Either.cond(kind == "raid" ||
              placementTargets.map(_.siteId) == sites, (),
              GameClientFailure.DecodeFailure("$.campaign.placementTargets",
                "placement targets must match Campaign targets in order"))
            defenderKind <- optionalField(obj, "defenderKind").flatMap {
              case None => Right("bandits")
              case Some(_) => string(obj, "defenderKind", "$.campaign")
            }
            defenderPlayer <- optionalField(obj, "defenderPlayerId").flatMap {
              case None => Right(None)
              case Some(value) if value == null => Right(None)
              case Some(_) => string(obj, "defenderPlayerId", "$.campaign").map(Some(_))
            }
            defenderForce <- optionalField(obj, "defenderForce").flatMap {
              case None => Right(0)
              case Some(_) => int(obj, "defenderForce", "$.campaign")
            }
            defenseDiceCount <- optionalField(obj, "defenseDiceCount").flatMap {
              case None => Right(0)
              case Some(_) => int(obj, "defenseDiceCount", "$.campaign")
            }
            planSide <- optionalField(obj, "planSide").flatMap {
              case None => Right("attacker")
              case Some(_) => string(obj, "planSide", "$.campaign")
            }
            decisionOwner <- optionalField(obj, "decisionOwnerPlayerId").flatMap {
              case None => Right(None)
              case Some(value) if value == null => Right(None)
              case Some(_) => string(obj, "decisionOwnerPlayerId", "$.campaign").map(Some(_))
            }
          } yield Some(CampaignState(id, sites, force, plansFinished, planChoices,
            selectedPlans,
            attackDice, attack,
            skulls, maximumSacrifice, sacrificed, defenseDice, defense,
            victorious, maximumPlacement, placementTargets, defenderKind,
            defenderPlayer, defenderForce, defenseDiceCount, planSide, decisionOwner,
            kind, raidTargets)) }
        }
        campaignRaidRelocation <- optionalField(root, "campaignRaidRelocation").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.campaignRaidRelocation").flatMap {
            obj => for {
              decision <- string(obj, "decisionId", "$.campaignRaidRelocation")
              actor <- string(obj, "actorPlayerId", "$.campaignRaidRelocation")
              defender <- string(obj, "defenderPlayerId", "$.campaignRaidRelocation")
              origin <- string(obj, "originSiteId", "$.campaignRaidRelocation")
              legal <- stringArray(obj, "legalSiteIds", "$.campaignRaidRelocation")
              _ <- Either.cond(legal.distinct.size == legal.size && !legal.contains(origin),
                (), GameClientFailure.DecodeFailure(
                  "$.campaignRaidRelocation.legalSiteIds",
                  "relocation sites must be distinct and exclude the origin"))
            } yield Some(CampaignRaidRelocation(decision, actor, defender,
              origin, legal))
          }
        }
        worldDeckCount <- optionalField(root, "worldDeckCount").flatMap {
          case None => Right(0)
          case Some(_) => int(root, "worldDeckCount", "$")
        }
        worldDeckTopValue <- optionalField(root, "worldDeckTopCardKind")
        worldDeckTop <- worldDeckTopValue match {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(_) => optionalString(root, "worldDeckTopCardKind", "$")
        }
        boards <- optionalField(root, "playerBoards").flatMap {
          case None => Right(Vector.empty)
          case Some(_) => array(root, "playerBoards", "$").flatMap(traverse(_, "playerBoards") {
          (item, path) => for {
            player <- string(item, "playerId", path)
            warbands <- int(item, "warbands", path)
            favor <- int(item, "favor", path)
            up <- int(item, "faceUpSecrets", path)
            down <- int(item, "faceDownSecrets", path)
            supply <- int(item, "supply", path)
            pawn <- optionalString(item, "pawnSiteId", path)
            advisers <- array(item, "advisers", path).flatMap(
              traverse(_, "advisers")((card, cardPath) => cardDetails(card, cardPath)))
            relics <- array(item, "relics", path).flatMap(
              traverse(_, "relics")((card, cardPath) => cardDetails(card, cardPath)))
            visionValue <- optionalField(item, "revealedVision")
            vision <- visionValue match {
              case None => Right(None)
              case Some(card) if card == null => Right(None)
              case Some(card) => cardDetails(card, s"$path.revealedVision").map(Some(_))
            }
          } yield PlayerBoard(player, warbands, favor, up, down, supply, pawn,
            advisers, relics, vision)
        }) }
      } yield GameProjection(
        game,
        sequence,
        phase,
        active,
        players,
        world,
        pawns,
        controls.toSet,
        ready,
        completed,
        resources,
        siteResources,
        actionOpen,
        actions,
        destinations,
        searchSources,
        musters,
        trades,
        boardActions,
        pendingDecision,
        recover,
        campaign,
        worldDeckCount,
        worldDeckTop,
        boards,
        oathkeeper,
        oathkeeperRecipient,
        campaignRaidRelocation,
        forge,
        banners,
        challenge,
        minorActions,
        negotiation,
        negotiationWaiting
      )
    }
  }

  def decodeError(
      json: String
  ): Either[GameClientFailure, (String, String)] =
    safely(parseObject(json).flatMap(root =>
      for {
        code <- string(root, "error", "$")
        message <- string(root, "message", "$")
      } yield code -> message))

  private def colorToken(value: String): PlayerColorToken =
    value match {
      case "purple" => PlayerColorToken.Purple
      case "blue" => PlayerColorToken.Blue
      case "red" => PlayerColorToken.Red
      case "yellow" => PlayerColorToken.Yellow
      case _ => PlayerColorToken.Neutral
    }

  private def cardDetails(value: js.Dynamic, path: String)
      : Either[GameClientFailure, CardDetails] = for {
    id <- string(value, "cardId", path)
    kind <- string(value, "cardKind", path)
    name <- string(value, "name", path)
    suit <- optionalString(value, "suit", path)
    restrictions <- optionalString(value, "restrictions", path)
    rulesText <- optionalString(value, "rulesText", path)
    orientation <- optionalString(value, "orientation", path)
    side <- optionalText(value, "side", path)
    favor <- optionalInt(value, "favor", path).map(_.getOrElse(0))
    secrets <- optionalInt(value, "secrets", path).map(_.getOrElse(0))
    relicValue <- optionalInt(value, "relicValue", path)
    defense <- optionalInt(value, "defense", path)
    hidden <- bool(value, "hidden", path)
  } yield CardDetails(id, kind, name, suit, restrictions, rulesText,
    orientation, side, favor, secrets, relicValue, defense, hidden)

  private def optionalText(value: js.Dynamic, name: String, path: String)
      : Either[GameClientFailure, Option[String]] =
    optionalField(value, name).flatMap {
      case None => Right(None)
      case Some(raw) if raw == null => Right(None)
      case Some(_) => optionalString(value, name, path)
    }

  private def optionalInt(value: js.Dynamic, name: String, path: String)
      : Either[GameClientFailure, Option[Int]] =
    optionalField(value, name).flatMap {
      case None => Right(None)
      case Some(raw) if raw == null => Right(None)
      case Some(_) => int(value, name, path).map(Some(_))
    }

  private def economyTarget(obj: js.Dynamic, path: String)
      : Either[GameClientFailure, EconomyTarget] = for {
    raw <- field(obj, "target", path)
    value <- objectValue(raw, s"$path.target")
    kind <- string(value, "kind", s"$path.target")
    id <- string(value, "id", s"$path.target")
    _ <- if (kind == "denizen" || kind == "edifice") Right(())
      else Left(GameClientFailure.DecodeFailure(s"$path.target.kind",
        "expected denizen or edifice"))
  } yield EconomyTarget(kind, id)

  @scala.noinline
  private def decodeBoardTargetActions(root: js.Dynamic)
      : Either[GameClientFailure, Vector[BoardTargetAction]] =
    array(root, "boardTargetActions", "$").flatMap(
      traverse(_, "boardTargetActions")(boardTargetAction))

  @scala.noinline
  private def boardTargetAction(item: js.Dynamic, path: String)
      : Either[GameClientFailure, BoardTargetAction] = for {
    kind <- string(item, "actionKind", path)
    prompt <- string(item, "prompt", path)
    minimum <- int(item, "minimum", path)
    maximum <- int(item, "maximum", path)
    auto <- bool(item, "autoActivate", path)
    decision <- optionalText(item, "decisionId", path)
    required <- array(item, "requiredTargets", path).flatMap(
      traverse(_, "requiredTargets")((value, requiredPath) =>
        boardTargetRef(value, requiredPath)))
    formation <- boardTargetFormation(item, path)
    candidates <- array(item, "candidates", path).flatMap(
      traverse(_, "candidates")(boardTargetCandidate))
    _ <- Either.cond(minimum >= 0 && maximum >= minimum &&
      maximum <= candidates.size, (), GameClientFailure.DecodeFailure(
        path, "invalid board-target cardinality"))
    keys = candidates.map(_.target.stableKey)
    _ <- Either.cond(keys.distinct.size == keys.size, (),
      GameClientFailure.DecodeFailure(s"$path.candidates",
        "duplicate target reference"))
    _ <- Either.cond(required.distinct.size == required.size &&
      required.forall(candidates.map(_.target).contains) &&
      required.size <= minimum, (), GameClientFailure.DecodeFailure(
        s"$path.requiredTargets", "invalid required target reference"))
  } yield BoardTargetAction(kind, prompt, minimum, maximum, auto,
    candidates, formation, required, decision)

  private def boardTargetFormation(item: js.Dynamic, path: String)
      : Either[GameClientFailure, Option[BoardTargetFormation]] =
    optionalField(item, "formation").flatMap {
      case None => Right(None)
      case Some(value) if value == null => Right(None)
      case Some(value) => objectValue(value, s"$path.formation").flatMap { obj =>
        for {
          min <- int(obj, "minimumForce", s"$path.formation")
          max <- int(obj, "maximumForce", s"$path.formation")
          warbands <- int(obj, "availableWarbands", s"$path.formation")
          cost <- int(obj, "supplyCost", s"$path.formation")
          _ <- Either.cond(min >= 0 && max >= min && warbands >= 0 &&
            max <= warbands && cost >= 0, (), GameClientFailure.DecodeFailure(
              s"$path.formation", "invalid board-target formation bounds"))
        } yield Some(BoardTargetFormation(min, max, warbands, cost))
      }
    }

  private def boardTargetCandidate(candidate: js.Dynamic, path: String)
      : Either[GameClientFailure, BoardTargetCandidate] = for {
    targetValue <- field(candidate, "target", path)
    target <- boardTargetRef(targetValue, s"$path.target")
    label <- string(candidate, "label", path)
    details <- stringArray(candidate, "details", path)
  } yield BoardTargetCandidate(target, label, details)

  private def boardTargetRef(value: js.Dynamic, path: String)
      : Either[GameClientFailure, BoardTargetRef] = for {
    obj <- objectValue(value, path)
    kind <- string(obj, "kind", path)
    target <- kind match {
      case "player" => string(obj, "playerId", path).map(BoardTargetRef.Player)
      case "site" => string(obj, "siteId", path).map(BoardTargetRef.Site)
      case "site-card" => for {
        site <- string(obj, "siteId", path)
        cardKind <- string(obj, "cardKind", path)
        _ <- Either.cond(Set("denizen", "edifice").contains(cardKind), (),
          GameClientFailure.DecodeFailure(s"$path.cardKind",
            "expected denizen or edifice"))
        card <- string(obj, "cardId", path)
      } yield BoardTargetRef.SiteCard(site, cardKind, card)
      case "player-adviser" => for {
        player <- string(obj, "playerId", path)
        card <- string(obj, "cardId", path)
      } yield BoardTargetRef.PlayerAdviser(player, card)
      case "player-relic" => for {
        player <- string(obj, "playerId", path)
        relic <- string(obj, "relicId", path)
      } yield BoardTargetRef.PlayerRelic(player, relic)
      case "player-pawn" => string(obj, "playerId", path)
        .map(BoardTargetRef.PlayerPawn)
      case "player-banner" => for {
        player <- string(obj, "playerId", path)
        banner <- string(obj, "banner", path)
        _ <- Either.cond(Set("peoples-favor", "darkest-secret").contains(banner),
          (), GameClientFailure.DecodeFailure(s"$path.banner",
            "expected peoples-favor or darkest-secret"))
      } yield BoardTargetRef.PlayerBanner(player, banner)
      case other => Left(GameClientFailure.DecodeFailure(s"$path.kind",
        s"unsupported board target kind '$other'"))
    }
  } yield target

  @scala.noinline
  private def safely[A](decode: => Either[GameClientFailure, A]) =
    try decode
    catch {
      case NonFatal(error) =>
        Left(GameClientFailure.DecodeFailure(
          "$",
          Option(error.getMessage).getOrElse("malformed projection")
        ))
    }

  private def parseObject(
      json: String
  ): Either[GameClientFailure, js.Dynamic] =
    try objectValue(js.JSON.parse(json), "$")
    catch {
      case NonFatal(error) =>
        Left(GameClientFailure.DecodeFailure(
          "$",
          Option(error.getMessage).getOrElse("malformed JSON")
        ))
    }

  private def objectValue(
      value: js.Dynamic,
      path: String
  ): Either[GameClientFailure, js.Dynamic] =
    if (
      value != null &&
      js.typeOf(value) == "object" &&
      !js.Array.isArray(value)
    ) Right(value)
    else Left(GameClientFailure.DecodeFailure(path, "expected object"))

  private def field(
      value: js.Dynamic,
      name: String,
      path: String
  ): Either[GameClientFailure, js.Dynamic] =
    objectValue(value, path).flatMap { objectValue =>
      val result = objectValue.selectDynamic(name)
      if (js.isUndefined(result))
        Left(GameClientFailure.DecodeFailure(
          s"$path.$name",
          "missing field"
        ))
      else Right(result)
    }

  private def string(value: js.Dynamic, name: String, path: String) =
    field(value, name, path).flatMap { result =>
      if (js.typeOf(result) == "string" && result.asInstanceOf[String].nonEmpty)
        Right(result.asInstanceOf[String])
      else Left(GameClientFailure.DecodeFailure(
        s"$path.$name",
        "expected non-empty string"
      ))
    }

  private def optionalString(value: js.Dynamic, name: String, path: String) =
    field(value, name, path).flatMap { result =>
      if (result == null) Right(None)
      else if (js.typeOf(result) == "string")
        Right(Some(result.asInstanceOf[String]))
      else Left(GameClientFailure.DecodeFailure(
        s"$path.$name",
        "expected string or null"
      ))
    }

  private def long(value: js.Dynamic, name: String, path: String) =
    field(value, name, path).flatMap { result =>
      if (
        js.typeOf(result) == "number" &&
        result.asInstanceOf[Double] >= 0 &&
        result.asInstanceOf[Double] <= MaxJsonSafeInteger &&
        result.asInstanceOf[Double].isWhole
      ) Right(result.asInstanceOf[Double].toLong)
      else Left(GameClientFailure.DecodeFailure(
        s"$path.$name",
        "expected non-negative JSON-safe integer"
      ))
    }

  private def int(value: js.Dynamic, name: String, path: String) =
    field(value, name, path).flatMap { result =>
      if (js.typeOf(result) == "number" &&
          result.asInstanceOf[Double].isWhole &&
          result.asInstanceOf[Double] >= 0 &&
          result.asInstanceOf[Double] <= Int.MaxValue)
        Right(result.asInstanceOf[Double].toInt)
      else Left(GameClientFailure.DecodeFailure(
        s"$path.$name", "expected non-negative integer"))
    }

  private def optionalField(
      value: js.Dynamic,
      name: String
  ): Either[GameClientFailure, Option[js.Dynamic]] =
    objectValue(value, "$").map { obj =>
      val result = obj.selectDynamic(name)
      if (js.isUndefined(result)) None else Some(result)
    }

  private def bool(value: js.Dynamic, name: String, path: String) =
    field(value, name, path).flatMap { result =>
      if (js.typeOf(result) == "boolean")
        Right(result.asInstanceOf[Boolean])
      else Left(GameClientFailure.DecodeFailure(
        s"$path.$name",
        "expected boolean"
      ))
    }

  private def array(value: js.Dynamic, name: String, path: String) =
    field(value, name, path).flatMap { result =>
      if (js.Array.isArray(result))
        Right(result.asInstanceOf[js.Array[js.Dynamic]].toVector)
      else Left(GameClientFailure.DecodeFailure(
        s"$path.$name",
        "expected array"
      ))
    }

  private def stringArray(value: js.Dynamic, name: String, path: String) =
    array(value, name, path).flatMap { values =>
      traverse(values, name) { (item, itemPath) =>
        if (js.typeOf(item) == "string") Right(item.asInstanceOf[String])
        else Left(GameClientFailure.DecodeFailure(
          itemPath,
          "expected string"
        ))
      }
    }

  private def validateCampaignPlanChoice(choice: CampaignPlanChoice, path: String)
      : Either[GameClientFailure, CampaignPlanChoice] = {
    val valid = choice.kind match {
      case "adviser" => choice.sourceKey.nonEmpty && choice.playerId.nonEmpty &&
        choice.siteId.isEmpty && choice.cardId.nonEmpty
      case "site-card" => choice.sourceKey.nonEmpty && choice.playerId.isEmpty &&
        choice.siteId.nonEmpty && choice.cardId.nonEmpty
      case "relic" => choice.sourceKey.nonEmpty && choice.playerId.nonEmpty &&
        choice.siteId.isEmpty && choice.cardId.nonEmpty
      case "title" => choice.sourceKey.nonEmpty && choice.playerId.nonEmpty &&
        choice.siteId.isEmpty && choice.cardId.isEmpty
      case _ => false
    }
    Either.cond(valid, choice, GameClientFailure.DecodeFailure(path,
      s"invalid Campaign plan choice shape '${choice.kind}'"))
  }

  private def decodeCampaignPlanChoices(obj: js.Dynamic, name: String) =
    array(obj, name, "$.campaign").flatMap(traverse(_, name) { (choice, choicePath) =>
      for {
        kind <- string(choice, "kind", choicePath)
        sourceKey <- optionalString(choice, "sourceKey", choicePath)
        player <- optionalString(choice, "playerId", choicePath)
        choiceSite <- optionalString(choice, "siteId", choicePath)
        card <- optionalString(choice, "cardId", choicePath)
        label <- string(choice, "label", choicePath)
        handler <- optionalString(choice, "handlerId", choicePath)
        favor <- int(choice, "favorCost", choicePath)
        secret <- int(choice, "secretCost", choicePath)
        result <- string(choice, "mechanicalResult", choicePath)
        decoded = CampaignPlanChoice(kind, sourceKey, player, choiceSite,
          card, label, handler, favor, secret, result)
        valid <- validateCampaignPlanChoice(decoded, choicePath)
      } yield valid
    }).flatMap { choices =>
      Either.cond(choices.flatMap(_.sourceKey).distinct.size == choices.size,
        choices, GameClientFailure.DecodeFailure(s"$$.campaign.$name",
          "duplicate Campaign plan source"))
    }

  @scala.noinline
  private def traverse[A](
      values: Vector[js.Dynamic],
      name: String
  )(decode: (js.Dynamic, String) => Either[GameClientFailure, A]) =
    values.zipWithIndex.foldLeft[
      Either[GameClientFailure, Vector[A]]
    ](Right(Vector.empty)) { case (result, (value, index)) =>
      result.flatMap(accumulated =>
        decode(value, s"$$.$name[$index]").map(accumulated :+ _))
    }
}
