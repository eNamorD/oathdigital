package oathdigital.frontend

import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.util.control.NonFatal

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
final case class GameSiteRelics(facedownCount: Int)
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
    requiredTargets: Vector[BoardTargetRef] = Vector.empty)
final case class CardResolution(kind: String, orientation: Option[String],
    replacementRequired: Boolean, replacementTargets: Vector[CardDetails])
final case class PendingCardDecision(
    decisionId: String, kind: String, actorPlayerId: String, prompt: String,
    instructions: Vector[String], cards: Vector[CardDetails], keepMinimum: Int,
    keepMaximum: Int, orderingRequired: Boolean,
    resolutionsByCard: Map[String, Vector[CardResolution]])
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
    oathkeeper: Option[OathkeeperStatus] = None
)
final case class RecoverState(decisionId: String, dice: Vector[String],
    shields: Int, difficulty: Int, supplySpent: Int, supplyRemaining: Int,
    canAddDice: Boolean, canStop: Boolean)
final case class CampaignState(decisionId: String, targetSiteIds: Vector[String], force: Int,
    plansFinished: Boolean, planChoices: Vector[CampaignPlanChoice],
    selectedPlans: Vector[CampaignPlanChoice],
    attackDice: Vector[String], attack: Int, skullLosses: Int,
    maxSacrifice: Int, sacrificed: Option[Int], defenseDice: Vector[String],
    defense: Option[Int], victorious: Option[Boolean], maxPlacement: Int)
object CampaignState {
  def apply(decisionId: String, siteId: String, force: Int,
      plansFinished: Boolean, planChoices: Vector[CampaignPlanChoice],
      selectedPlans: Vector[CampaignPlanChoice], attackDice: Vector[String],
      attack: Int, skullLosses: Int, maxSacrifice: Int, sacrificed: Option[Int],
      defenseDice: Vector[String], defense: Option[Int],
      victorious: Option[Boolean], maxPlacement: Int): CampaignState =
    new CampaignState(decisionId, Vector(siteId), force, plansFinished,
      planChoices, selectedPlans, attackDice, attack, skullLosses, maxSacrifice,
      sacrificed, defenseDice, defense, victorious, maxPlacement)
}
final case class CampaignPlanChoice(kind: String, sourceKey: Option[String],
    playerId: Option[String], siteId: Option[String], cardId: Option[String],
    label: String, handlerId: Option[String], favorCost: Int, secretCost: Int,
    mechanicalResult: String)
final case class OathkeeperStatus(goal: String, holderPlayerId: Option[String],
    side: String, usurperLimited: Boolean, winnerPlayerId: Option[String])

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
  final case class ChooseCampaignPlan(playerId: String, decisionId: String,
      choice: CampaignPlanChoice) extends GameCommand
  final case class FinishCampaignPlans(playerId: String, decisionId: String)
      extends GameCommand
  final case class ChooseCampaignSacrifice(playerId: String, decisionId: String,
      count: Int) extends GameCommand
  final case class PlaceCampaignForce(playerId: String, decisionId: String,
      count: Int) extends GameCommand
  final case class Muster(playerId: String, target: EconomyTarget) extends GameCommand
  final case class Trade(playerId: String, target: EconomyTarget, resource: String)
      extends GameCommand
  final case class BeginSearch(playerId: String, source: String, region: Option[String])
      extends GameCommand
  final case class BeginRecover(playerId: String) extends GameCommand
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
      case GameCommand.ChooseCampaignPlan(player, decision, choice) =>
        val source: js.Any = choice.kind match {
          case "adviser" => js.Dynamic.literal(kind = "adviser",
            playerId = choice.playerId.get, cardId = choice.cardId.get)
          case "site-card" => js.Dynamic.literal(kind = "site-card",
            siteId = choice.siteId.get, cardId = choice.cardId.get)
          case "relic" => js.Dynamic.literal(kind = "relic",
            playerId = choice.playerId.get, cardId = choice.cardId.get)
        }
        js.Dynamic.literal(`type` = "chooseCampaignPlan", playerId = player,
          decisionId = decision, source = source)
      case GameCommand.FinishCampaignPlans(player, decision) =>
        js.Dynamic.literal(`type` = "finishCampaignPlans", playerId = player,
          decisionId = decision)
      case GameCommand.ChooseCampaignSacrifice(player, decision, count) =>
        js.Dynamic.literal(`type` = "chooseCampaignSacrifice",
          playerId = player, decisionId = decision, count = count)
      case GameCommand.PlaceCampaignForce(player, decision, count) =>
        js.Dynamic.literal(`type` = "placeCampaignForce",
          playerId = player, decisionId = decision, count = count)
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
    js.JSON.stringify(js.Dynamic.literal(
      expectedNextSequence = sequence.toDouble,
      command = payload
    ))
  }

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
                    GameSiteRelics(facedownCount),
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
          } yield Some(OathkeeperStatus(goal, holder, side, limited, winner)) }
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
        boardActions <- array(root, "boardTargetActions", "$").flatMap(
          traverse(_, "boardTargetActions") { (item, path) => for {
            kind <- string(item, "actionKind", path)
            prompt <- string(item, "prompt", path)
            minimum <- int(item, "minimum", path)
            maximum <- int(item, "maximum", path)
            auto <- bool(item, "autoActivate", path)
            required <- array(item, "requiredTargets", path).flatMap(
              traverse(_, "requiredTargets")((value, requiredPath) =>
                boardTargetRef(value, requiredPath)))
            formation <- optionalField(item, "formation").flatMap {
              case None => Right(None)
              case Some(value) if value == null => Right(None)
              case Some(value) => objectValue(value, s"$path.formation").flatMap { obj =>
                for {
                  min <- int(obj, "minimumForce", s"$path.formation")
                  max <- int(obj, "maximumForce", s"$path.formation")
                  warbands <- int(obj, "availableWarbands", s"$path.formation")
                  cost <- int(obj, "supplyCost", s"$path.formation")
                  _ <- Either.cond(min >= 0 && max >= min && warbands >= 0 &&
                    max <= warbands && cost >= 0,
                    (), GameClientFailure.DecodeFailure(s"$path.formation",
                      "invalid board-target formation bounds"))
                } yield Some(BoardTargetFormation(min, max, warbands, cost))
              }
            }
            candidates <- array(item, "candidates", path).flatMap(
              traverse(_, "candidates") { (candidate, candidatePath) => for {
                targetValue <- field(candidate, "target", candidatePath)
                target <- boardTargetRef(targetValue, s"$candidatePath.target")
                label <- string(candidate, "label", candidatePath)
                details <- stringArray(candidate, "details", candidatePath)
              } yield BoardTargetCandidate(target, label, details) })
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
            candidates, formation, required) })
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
        campaign <- optionalField(root, "campaign").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.campaign").flatMap { obj => for {
            id <- string(obj, "decisionId", "$.campaign")
            sites <- stringArray(obj, "targetSiteIds", "$.campaign")
            _ <- Either.cond(sites.nonEmpty && sites.distinct.size == sites.size,
              (), GameClientFailure.DecodeFailure("$.campaign.targetSiteIds",
                "Campaign targets must be non-empty and distinct"))
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
          } yield Some(CampaignState(id, sites, force, plansFinished, planChoices,
            selectedPlans,
            attackDice, attack,
            skulls, maximumSacrifice, sacrificed, defenseDice, defense,
            victorious, maximumPlacement)) }
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
        oathkeeper
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

  private def boardTargetRef(value: js.Dynamic, path: String)
      : Either[GameClientFailure, BoardTargetRef] = for {
    obj <- objectValue(value, path)
    kind <- string(obj, "kind", path)
    target <- kind match {
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
      case other => Left(GameClientFailure.DecodeFailure(s"$path.kind",
        s"unsupported board target kind '$other'"))
    }
  } yield target

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
