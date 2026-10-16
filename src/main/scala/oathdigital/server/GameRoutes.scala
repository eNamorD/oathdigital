package oathdigital.server

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpResponse,
  StatusCode,
  StatusCodes
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.slf4j.LoggerFactory

import oathdigital.application.{
  GameApplicationError,
  GameCommand,
  GameIntentMapper
}
import oathdigital.protocol.projection.{GameProjection, LogPage,
  LogPageCodec}
import oathdigital.model.PlayerId

private[server] object CommandRejectionMessage:
  def text(violation: oathdigital.model.OathViolation): String =
    violation match
      case oathdigital.model.OathViolation.NegotiationUnavailable(detail) => detail
      case oathdigital.model.OathViolation.InsufficientFavor(required, available) =>
        s"required favor $required exceeds available $available"
      case oathdigital.model.OathViolation.InsufficientSupply(required, available) =>
        s"required Supply $required exceeds available $available"
      case oathdigital.model.OathViolation.InsufficientSecrets(required, available) =>
        s"required secrets $required exceed available $available"
      case oathdigital.model.OathViolation.ConsentPending =>
        "Waiting for an answer to a consent request"
      case oathdigital.model.OathViolation.ConsentUnavailable(reason) => reason
      // Worded as the unwaivable rejection is: the waiver is for the search.
      case oathdigital.model.OathViolation.WaivableOperationRejected(code,
          detail, _) =>
        oathdigital.model.OathViolation.CoreOperationRejected(code, detail)
          .toString
      case other => other.toString

final class GameServerGateway(
    service: oathdigital.application.GameApplicationService,
    projector: oathdigital.application.GameProjector
):
  def rawEventHistory(gameId: String, limit: Int)
      : Either[GameApplicationError, Vector[String]] =
    service.rawEventHistory(gameId, limit)

  def submit(
      gameId: String,
      requestingPlayer: PlayerId,
      expectedNextSequence: Long,
      command: GameCommand
  ): Either[GameApplicationError, GameProjection] =
    service.handle(gameId, expectedNextSequence, command).map { accepted =>
      projector.project(gameId, accepted.loaded, requestingPlayer)
    }

  def load(
      gameId: String,
      requestingPlayer: PlayerId
  ): Either[GameApplicationError, GameProjection] =
    service.load(gameId).flatMap:
      case Some(loaded) =>
        Right(projector.project(gameId, loaded, requestingPlayer))
      case None =>
        Left(GameApplicationError.StreamNotFound(gameId))

  /** `None` when `after` is past the journal's end. */
  def log(gameId: String, requestingPlayer: PlayerId, after: Long)
      : Either[GameApplicationError, Option[LogPage]] =
    service.history(gameId).flatMap:
      case Some(history) =>
        Right(projector.logPage(gameId, history, after, requestingPlayer))
      case None => Left(GameApplicationError.StreamNotFound(gameId))

final class GameRoutes(
    gateway: GameServerGateway,
    blockingExecutionContext: ExecutionContext
):
  private val logger = LoggerFactory.getLogger(classOf[GameRoutes])

  val route: Route =
    pathPrefix("api" / "dev" / "first-games" / Segment) { gameId =>
      path("events") {
        get:
          parameter("limit".as[Int].withDefault(25)) { limit =>
            DevelopmentTrustBoundary.validateIdentifier(gameId, "$.gameId") match
              case Left(error) => complete(inputError(error))
              case Right(validGameId) if limit < 1 || limit > 100 =>
                complete(jsonResponse(StatusCodes.BadRequest, "malformed-request",
                  "$.limit: must be between 1 and 100"))
              case Right(validGameId) => completeRawHistory(
                gateway.rawEventHistory(validGameId, limit))
          }
      } ~ parameter("playerId") { playerId =>
        validateIdentifiers(gameId, playerId) match
          case Left(error) =>
            complete(inputError(error))
          case Right((validGameId, validPlayerId)) =>
            pathEndOrSingleSlash {
              get:
                completeAsync(gateway.load(
                  validGameId,
                  PlayerId(validPlayerId)
                ))
            } ~
              path("log" / Segment) { raw =>
                get:
                  raw.toLongOption.filter(_ >= 0) match
                    case None => complete(jsonResponse(StatusCodes.BadRequest,
                      "malformed-request", "$.after: expected a journal sequence"))
                    case Some(after) => completeLog(gateway.log(validGameId,
                      PlayerId(validPlayerId), after))
              } ~
              path("commands"):
                post:
                  entity(as[String]) { body =>
                    GameHttpWire.decodeCommand(body) match
                      case Left(error) =>
                        complete(jsonResponse(
                          StatusCodes.BadRequest,
                          "malformed-request",
                          s"${error.path}: ${error.message}"
                        ))
                      case Right(request) =>
                        GameIntentMapper.bind(PlayerId(validPlayerId), request.intent) match
                          case Left(error) => complete(jsonResponse(StatusCodes.BadRequest,
                            "malformed-request", s"${error.path}: ${error.message}"))
                          case Right(command) => completeAsync(gateway.submit(
                            validGameId, PlayerId(validPlayerId),
                            request.expectedNextSequence, command))
                  }
      }
    }

  private def completeLog(
      operation: => Either[GameApplicationError, Option[LogPage]]
  ): Route = onComplete(Future(operation)(using blockingExecutionContext)):
    case Success(Right(Some(page))) => complete(HttpResponse(StatusCodes.OK,
      entity = HttpEntity(ContentTypes.`application/json`,
        LogPageCodec.encode(page))))
    case Success(Right(None)) => complete(jsonResponse(StatusCodes.BadRequest,
      "malformed-request", "$.after: past the end of the journal"))
    case Success(Left(error)) =>
      val (status, code, message, _) = publicError(error)
      complete(jsonResponse(status, code, message))
    case Failure(error) =>
      logger.error("Unhandled game-log route failure", error)
      complete(jsonResponse(StatusCodes.InternalServerError, "internal-error",
        "the server could not complete the request"))

  private def completeRawHistory(
      operation: => Either[GameApplicationError, Vector[String]]
  ): Route = onComplete(Future(operation)(using blockingExecutionContext)):
    case Success(Right(records)) =>
      val values = records.map(record => ujson.read(record))
      complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(
        ContentTypes.`application/json`, ujson.write(ujson.Obj(
          "warning" -> "Raw authoritative events may reveal hidden outcomes.",
          "events" -> ujson.Arr.from(values))))))
    case Success(Left(error)) =>
      val (status, code, message, _) = publicError(error)
      complete(jsonResponse(status, code, message))
    case Failure(error) =>
      logger.error("Unhandled raw event-history route failure", error)
      complete(jsonResponse(StatusCodes.InternalServerError, "internal-error",
        "the server could not complete the request"))

  private def completeAsync(
      operation: => Either[GameApplicationError, GameProjection]
  ): Route =
    onComplete(Future(operation)(using blockingExecutionContext)):
      case Success(Right(projection)) =>
        complete(HttpResponse(
          StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            GameHttpWire.encodeProjection(projection)
          )
        ))
      case Success(Left(error)) =>
        val (status, code, message, internal) = publicError(error)
        if internal then
          logger.error("Internal first-game application failure: {}", error)
        complete(jsonResponse(status, code, message))
      case Failure(error) =>
        logger.error("Unhandled first-game route failure", error)
        complete(jsonResponse(
          StatusCodes.InternalServerError,
          "internal-error",
          "the server could not complete the request"
        ))

  private def publicError(
      error: GameApplicationError
  ): (StatusCode, String, String, Boolean) =
    error match
      case _: GameApplicationError.StreamNotFound =>
        (StatusCodes.NotFound, "stream-not-found",
          "the requested game does not exist", false)
      case _: GameApplicationError.StaleClientPosition =>
        (StatusCodes.Conflict, "stale-client-position",
          "the client position is stale; refresh and retry", false)
      case _: GameApplicationError.SequenceConflict =>
        (StatusCodes.Conflict, "sequence-conflict",
          "the game changed while the command was handled", false)
      case _: GameApplicationError.DuplicateGame =>
        (StatusCodes.Conflict, "duplicate-game",
          "the game already exists", false)
      case GameApplicationError.CommandRejected(violation) =>
        (StatusCodes.UnprocessableContent, "command-rejected",
          CommandRejectionMessage.text(violation), false)
      case _: GameApplicationError.BootstrapFailure =>
        (StatusCodes.UnprocessableContent, "bootstrap-failed",
          "the development setup configuration is invalid", false)
      case _ =>
        (StatusCodes.InternalServerError, "internal-error",
          "the server could not complete the request", true)

  private def validateIdentifiers(
      gameId: String,
      playerId: String
  ): Either[HttpInputError, (String, String)] =
    for
      game <- DevelopmentTrustBoundary.validateIdentifier(gameId, "$.gameId")
      player <- DevelopmentTrustBoundary.validateIdentifier(
        playerId,
        "$.playerId"
      )
    yield game -> player

  private def inputError(error: HttpInputError): HttpResponse =
    jsonResponse(
      StatusCodes.BadRequest,
      "malformed-request",
      s"${error.path}: ${error.message}"
    )

  private def jsonResponse(
      status: StatusCode,
      code: String,
      message: String
  ): HttpResponse =
    HttpResponse(
      status,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        GameHttpWire.encodeError(code, message)
      )
    )
