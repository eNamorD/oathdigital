package oathdigital.server

import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCookie, RawHeader, SameSite, `Set-Cookie`}
import akka.http.scaladsl.server.{Directives, Route}
import org.slf4j.LoggerFactory
import oathdigital.application._
import oathdigital.protocol._

final class TrustedSeatRoutes(
    identities: IdentityRepository,
    provisioning: TrustedGameProvisioning,
    gateway: TrustedGameGateway,
    publicBaseUrl: URI,
    blockingExecutionContext: ExecutionContext
) extends Directives {
  private val logger = LoggerFactory.getLogger(classOf[TrustedSeatRoutes])
  private val cookieName = "oath_seat"
  private val privateHeaders = List(RawHeader("Cache-Control", "no-store"),
    RawHeader("Referrer-Policy", "no-referrer"))

  val route: Route = respondWithHeaders(privateHeaders) {
    path("games") {
      post { noQuery { sameOrigin {
        entity(as[String]) { body =>
          TrustedGameCreateRequestCodec.decode(body) match {
            case Left(_) => complete(malformed)
            case Right(request) => async {
              provisioning.create(request, publicBaseUrl.toString) match {
                case Right(created) => json(StatusCodes.Created, TrustedGameCreateResponseCodec.encode(created))
                case Left(TrustedGameFailure.InvalidRequest) => malformed
                case Left(TrustedGameFailure.DuplicateGame) => error(StatusCodes.Conflict,
                  "duplicate-game", "the game already exists")
                case Left(_) => internalError()
              }
            }
          }
        }
      } } }
    } ~ path("s" / Segment) { raw =>
      get { noQuery { async {
        SeatCode.parse(raw) match {
          case Left(_) => invalidLink
          case Right(code) => resolve(code) match {
            case Right(seat) if DevelopmentTrustBoundary.validateIdentifier(seat.gameId, "$.gameId").isRight =>
              val path = canonicalPath(seat.gameId)
              val cookie = HttpCookie(cookieName, code.raw, maxAge = Some(31536000L),
                path = Some(path), secure = publicBaseUrl.getScheme.equalsIgnoreCase("https"),
                httpOnly = true).withSameSite(SameSite.Lax)
              HttpResponse(StatusCodes.SeeOther, headers = List(canonicalLocation(seat.gameId), `Set-Cookie`(cookie)))
            case Left(TrustedSeatFailure.StorageFailure) => internalError()
            case _ => invalidLink
          }
        }
      } } }
    } ~ pathPrefix("games" / Segment) { gameId =>
      noQuery {
        if (DevelopmentTrustBoundary.validateIdentifier(gameId, "$.gameId").isLeft) complete(malformed)
        else extractRequest { request =>
          pathEnd {
            get {
              onComplete(Future {
                authenticate(request, gameId).flatMap(gateway.load(gameId, _))
              }(blockingExecutionContext)) {
                case Success(Right(_)) => ProductionFrontendRoutes.gamePage
                case Success(Left(TrustedSeatFailure.Forbidden)) => complete(recovery)
                case Success(Left(TrustedSeatFailure.Application(_: GameApplicationError.StreamNotFound))) =>
                  complete(recovery)
                case _ => complete(internalError())
              }
            }
          } ~ pathSingleSlash {
            get { complete(HttpResponse(StatusCodes.SeeOther,
              headers = List(canonicalLocation(gameId)))) }
          } ~ pathPrefix("api") {
            pathEndOrSingleSlash {
              get { async {
                authenticate(request, gameId).flatMap(gateway.load(gameId, _))
                  .fold(publicError, projection => json(StatusCodes.OK, GameHttpWire.encodeProjection(projection)))
              } }
            } ~ path("commands") {
              post { sameOrigin {
                entity(as[String]) { body => async {
                  authenticate(request, gameId).fold(publicError, seat =>
                    AuthenticatedGameHttpWire.decodeCommand(body).fold(_ => malformed, command =>
                      gateway.submit(gameId, seat, command).fold(publicError,
                        projection => json(StatusCodes.OK, GameHttpWire.encodeProjection(projection)))))
                } }
              } }
            } ~ path("preview") {
              post { sameOrigin {
                entity(as[String]) { body => async {
                  authenticate(request, gameId).fold(publicError, seat =>
                    decodePreview(body).fold(_ => malformed, preview =>
                      gateway.preview(gameId, seat, preview).fold(publicError,
                        value => json(StatusCodes.OK, MajorActionPreviewCodec.encode(value)))))
                } }
              } }
            }
          }
        }
      }
    }
  }

  private def canonicalPath(gameId: String): String =
    "/games/" + URLEncoder.encode(gameId, StandardCharsets.UTF_8)

  // Preserve the same escaped path used by encodeURIComponent and cookie matching.
  // Rendering a modeled URI may normalize percent-encoded colons back to literals.
  private def canonicalLocation(gameId: String): RawHeader =
    RawHeader("Location", canonicalPath(gameId))

  private def noQuery(inner: => Route): Route = parameterMap { parameters =>
    if (parameters.nonEmpty) complete(malformed) else inner
  }

  private def sameOrigin(inner: => Route): Route = extractRequest { request =>
    val origins = request.headers.filter(_.is("origin")).map(_.value)
    if (origins.isEmpty || (origins.size == 1 && originMatches(origins.head))) inner
    else complete(error(StatusCodes.Forbidden, "csrf-validation-failed", "request origin is invalid"))
  }

  private def originMatches(raw: String): Boolean = Try(new URI(raw)).toOption.exists { origin =>
    def port(uri: URI): Int = if (uri.getPort >= 0) uri.getPort
      else if (uri.getScheme.equalsIgnoreCase("https")) 443 else 80
    origin.isAbsolute && origin.getHost != null && origin.getRawUserInfo == null &&
      origin.getRawQuery == null && origin.getRawFragment == null &&
      Option(origin.getRawPath).forall(_.isEmpty) &&
      origin.getScheme.equalsIgnoreCase(publicBaseUrl.getScheme) &&
      origin.getHost.equalsIgnoreCase(publicBaseUrl.getHost) && port(origin) == port(publicBaseUrl)
  }

  private def resolve(code: SeatCode): Either[TrustedSeatFailure, TrustedSeat] =
    identities.resolveTrustedSeat(code.digest).left.map {
      case IdentityFailure.TrustedSeatNotFound => TrustedSeatFailure.Forbidden
      case _ => TrustedSeatFailure.StorageFailure
    }

  private def authenticate(request: HttpRequest, gameId: String)
      : Either[TrustedSeatFailure, TrustedSeat] = request.cookies.filter(_.name == cookieName).map(_.value) match {
    case Seq(raw) => SeatCode.parse(raw).left.map(_ => TrustedSeatFailure.Forbidden)
      .flatMap(resolve).flatMap(seat => Either.cond(seat.gameId == gameId, seat, TrustedSeatFailure.Forbidden))
    case _ => Left(TrustedSeatFailure.Forbidden)
  }

  // The shared preview decoder is permissive about envelope fields. This boundary
  // rejects actor fields without changing the existing development/authenticated APIs.
  private def decodePreview(body: String): Either[Unit, MajorActionPreviewRequest] =
    Try(ujson.read(body)).toOption.collect { case obj: ujson.Obj => obj }
      .filter(_.value.keys.forall(Set("expectedNextSequence", "action", "baseParameters", "orderedModifiers")))
      .toRight(()).flatMap(_ => MajorActionPreviewCodec.decode(body).left.map(_ => ()))

  private def async(operation: => HttpResponse): Route =
    onComplete(Future(operation)(blockingExecutionContext)) {
      case Success(response) => complete(response)
      case Failure(_) => complete(internalError())
    }

  private def publicError(failure: TrustedSeatFailure): HttpResponse = failure match {
    case TrustedSeatFailure.Forbidden => error(StatusCodes.Forbidden, "forbidden", "access is denied")
    case TrustedSeatFailure.InvalidIntent => malformed
    case TrustedSeatFailure.Application(application) => application match {
      case _: GameApplicationError.StreamNotFound =>
        error(StatusCodes.NotFound, "stream-not-found", "the requested game does not exist")
      case _: GameApplicationError.StaleClientPosition =>
        error(StatusCodes.Conflict, "stale-client-position", "the client position is stale; refresh and retry")
      case _: GameApplicationError.SequenceConflict =>
        error(StatusCodes.Conflict, "sequence-conflict", "the game changed while the command was handled")
      case GameApplicationError.CommandRejected(violation) =>
        error(StatusCodes.UnprocessableContent, "command-rejected", CommandRejectionMessage.text(violation))
      case _ => internalError()
    }
    case TrustedSeatFailure.StorageFailure => internalError()
  }

  private def internalError(): HttpResponse = {
    // Exception messages and request URIs can contain credentials. Log only a
    // generated reference, never the request, principal, failure value or throwable.
    val reference = UUID.randomUUID().toString
    logger.error("Trusted seat request failed; reference={}", reference)
    error(StatusCodes.InternalServerError, "internal-error", "the server could not complete the request")
      .addHeader(RawHeader("X-Request-ID", reference))
  }

  private def invalidLink: HttpResponse = HttpResponse(StatusCodes.NotFound,
    entity = HttpEntity(ContentTypes.`text/html(UTF-8)`,
      "<!doctype html><html lang=\"en\"><title>Invalid seat link</title><body><h1>Invalid seat link</h1><p>Open the assigned seat link from your host.</p></body></html>"))
  private def recovery: HttpResponse = HttpResponse(StatusCodes.Forbidden,
    entity = HttpEntity(ContentTypes.`text/html(UTF-8)`,
      "<!doctype html><html lang=\"en\"><title>Open your seat link</title><body><h1>Open your seat link</h1><p>Open the assigned seat link from your host to return to your game.</p></body></html>"))
  private def malformed: HttpResponse = error(StatusCodes.BadRequest, "malformed-request", "the request is invalid")
  private def error(status: StatusCode, code: String, message: String): HttpResponse =
    json(status, GameHttpWire.encodeError(code, message))
  private def json(status: StatusCode, body: String): HttpResponse =
    HttpResponse(status, entity = HttpEntity(ContentTypes.`application/json`, body))
}
