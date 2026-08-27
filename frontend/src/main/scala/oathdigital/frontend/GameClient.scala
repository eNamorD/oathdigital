package oathdigital.frontend

import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.util.control.NonFatal
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  FirstGameBootstrapCodec, FirstGameBootstrapRequest,
  MajorActionPreviewCodec, MajorActionPreviewRequest, MajorActionPreviewResponse,
  ModifierInvocation, GameIntent => GameCommand}
import oathdigital.protocol.projection.{GameProjection, GameProjectionCodec}

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

final case class EconomyTarget(kind: String, id: String)
final case class NegotiationTransferInput(recipientPlayerId: String,
    favor: Int, relicIds: Vector[String])
final case class NegotiationDisclosureInput(recipientPlayerId: String,
    kind: String, ownerPlayerId: Option[String] = None,
    siteId: Option[String] = None, cardKind: Option[String] = None,
    cardId: String)
final case class NegotiationTermsInput(transfers: Vector[NegotiationTransferInput],
    disclosures: Vector[NegotiationDisclosureInput])
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

trait GameClient {
  def bootstrap(
      gameId: String,
      selectedPlayerId: String,
      config: FirstGameBootstrapRequest
  ): Future[Either[GameClientFailure, GameProjection]]
  def load(
      gameId: String,
      selectedPlayerId: String
  ): Future[Either[GameClientFailure, GameProjection]]
  def submit(
      gameId: String,
      selectedPlayerId: String,
      expectedNextSequence: Long,
      command: GameCommand,
      orderedModifiers: Vector[ModifierInvocation] = Vector.empty
  ): Future[Either[GameClientFailure, GameProjection]]
  def preview(gameId: String, selectedPlayerId: String,
      request: MajorActionPreviewRequest)
      : Future[Either[GameClientFailure, MajorActionPreviewResponse]]
}

final class HttpGameClient(transport: JsonTransport)
    extends GameClient {
  override def bootstrap(
      gameId: String,
      selectedPlayerId: String,
      config: FirstGameBootstrapRequest
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
      command: GameCommand,
      orderedModifiers: Vector[ModifierInvocation]
  ) =
    send(
      "POST",
      s"/api/dev/first-games/${encode(gameId)}/commands?playerId=" +
        encode(selectedPlayerId),
      Some(GameJson.encodeCommand(expectedNextSequence, command, orderedModifiers))
    )

  override def preview(gameId: String, selectedPlayerId: String,
      request: MajorActionPreviewRequest) =
    transport.request("POST", s"/api/dev/first-games/${encode(gameId)}/preview?playerId=" +
      encode(selectedPlayerId), Some(MajorActionPreviewCodec.encodeRequest(request))).map({ result =>
      result.flatMap(response => if (response.status >= 200 && response.status < 300)
        MajorActionPreviewCodec.decodeResponse(response.body).left.map(error =>
          GameClientFailure.DecodeFailure(error.path, error.message))
      else Left(GameClientFailure.HttpFailure(response.status, "preview", response.body)))
    })(scala.scalajs.concurrent.JSExecutionContext.queue)

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
  def encodeBootstrap(request: FirstGameBootstrapRequest): String =
    FirstGameBootstrapCodec.encode(request)

  def encodeCommand(sequence: Long, command: GameCommand,
      modifiers: Vector[ModifierInvocation] = Vector.empty): String =
    ActorlessCommandCodec.encode(ActorlessCommandRequest(sequence, command, modifiers))

  def decodeProjection(json: String): Either[GameClientFailure, GameProjection] =
    GameProjectionCodec.decode(json).left.map(error =>
      GameClientFailure.DecodeFailure(error.path, error.message))

  def decodeError(json: String): Either[GameClientFailure, (String, String)] =
    try {
      ujson.read(json) match {
        case value: ujson.Obj =>
          (value.value.get("error"), value.value.get("message")) match {
            case (Some(ujson.Str(code)), Some(ujson.Str(message))) =>
              Right(code -> message)
            case _ => Left(GameClientFailure.DecodeFailure("$",
              "expected error and message strings"))
          }
        case _ => Left(GameClientFailure.DecodeFailure("$", "expected object"))
      }
    } catch {
      case NonFatal(error) => Left(GameClientFailure.DecodeFailure("$",
        Option(error.getMessage).getOrElse("malformed JSON")))
    }
}
