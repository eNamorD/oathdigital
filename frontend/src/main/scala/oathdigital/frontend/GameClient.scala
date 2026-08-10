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
    hidden: Boolean = false)
final case class GameSiteCard(denizenId: String, label: String,
    details: Option[CardDetails] = None)
final case class GameSiteRelics(facedownCount: Int)
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
    powers: Vector[SitePower] = Vector.empty
)
final case class SitePower(kind: String, label: String, description: Option[String])
final case class GameRegion(regionId: String, sites: Vector[GameSite], discardCount: Int = 0)
final case class GamePawn(playerId: String, siteId: String)
final case class AdviserChoice(adviserId: String, label: String)
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
final case class SearchCard(
    cardId: String,
    cardKind: String,
    label: String,
    legalPlacements: Vector[String]
)
final case class PendingSearch(
    decisionId: String,
    drawnCards: Vector[SearchCard],
    replaceableAdvisers: Vector[String],
    replaceableSiteCards: Vector[String]
)
final case class CardResolution(kind: String, orientation: Option[String],
    replacementTargets: Vector[CardDetails])
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
    privateAdviserChoices: Vector[AdviserChoice],
    activePlayerResources: Option[ActivePlayerResources] = None,
    currentSiteResources: Option[CurrentSiteResources] = None,
    actionSelectionOpen: Boolean = false,
    actionFamilies: Vector[String] = Vector.empty,
    legalTravelDestinations: Vector[LegalTravelDestination] = Vector.empty,
    legalSearchSources: Vector[LegalSearchSource] = Vector.empty,
    legalMusters: Vector[LegalMuster] = Vector.empty,
    legalTrades: Vector[LegalTrade] = Vector.empty,
    pendingSearch: Option[PendingSearch] = None,
    pendingCardDecision: Option[PendingCardDecision] = None,
    worldDeckCount: Int = 0,
    playerBoards: Vector[PlayerBoard] = Vector.empty
)

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
  final case class Muster(playerId: String, target: EconomyTarget) extends GameCommand
  final case class Trade(playerId: String, target: EconomyTarget, resource: String)
      extends GameCommand
  final case class BeginSearch(playerId: String, source: String, region: Option[String])
      extends GameCommand
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
                    powers <- optionalField(site, "powers").flatMap {
                      case None => Right(Vector.empty)
                      case Some(_) => array(site, "powers", sitePath).flatMap(
                      traverse(_, "powers") { (power, powerPath) => for {
                        kind <- string(power, "kind", powerPath)
                        label <- string(power, "label", powerPath)
                        description <- optionalString(power, "description", powerPath)
                      } yield SitePower(kind, label, description) }) }
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
                    powers
                  )
              })
            } yield GameRegion(id, sites, discardCount)
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
        choices <- optionalField(root, "privateAdviserChoices").flatMap {
          case None => Right(Vector.empty)
          case Some(_) => array(root, "privateAdviserChoices", "$").flatMap(
          traverse(_, "privateAdviserChoices") { (item, path) =>
            for {
              id <- string(item, "adviserId", path)
              label <- string(item, "label", path)
            } yield AdviserChoice(id, label)
          }) }
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
        pendingSearch <- optionalField(root, "pendingSearch").flatMap {
          case None => Right(None)
          case Some(value) if value == null => Right(None)
          case Some(value) => objectValue(value, "$.pendingSearch").flatMap { obj => for {
            decision <- string(obj, "decisionId", "$.pendingSearch")
            cards <- array(obj, "drawnCards", "$.pendingSearch").flatMap(
              traverse(_, "drawnCards") { (card, path) => for {
                id <- string(card, "cardId", path)
                kind <- string(card, "cardKind", path)
                label <- string(card, "label", path)
                placements <- stringArray(card, "legalPlacements", path)
              } yield SearchCard(id, kind, label, placements) })
            advisers <- stringArray(obj, "replaceableAdvisers", "$.pendingSearch")
            siteCards <- stringArray(obj, "replaceableSiteCards", "$.pendingSearch")
          } yield Some(PendingSearch(decision, cards, advisers, siteCards)) }
        }
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
                        targets <- array(item, "replacementTargets", path).flatMap(
                          traverse(_, "replacementTargets")((target, targetPath) =>
                            cardDetails(target, targetPath)))
                      } yield values :+ CardResolution(kind, orientation, targets)
                    case (failure @ Left(_), _) => failure
                  }.map(value => acc.updated(card.cardId, value))
              case (failure @ Left(_), _) => failure
            }
          } yield Some(PendingCardDecision(id, kind, actor, prompt, instructions,
            cards, minimum, maximum, ordering, resolutions)) }
        }
        worldDeckCount <- optionalField(root, "worldDeckCount").flatMap {
          case None => Right(0)
          case Some(_) => int(root, "worldDeckCount", "$")
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
        choices,
        resources,
        siteResources,
        actionOpen,
        actions,
        destinations,
        searchSources,
        musters,
        trades,
        pendingSearch,
        pendingDecision,
        worldDeckCount,
        boards
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
    hidden <- bool(value, "hidden", path)
  } yield CardDetails(id, kind, name, suit, restrictions, rulesText,
    orientation, hidden)

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
