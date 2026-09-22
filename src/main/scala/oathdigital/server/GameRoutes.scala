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
import akka.http.scaladsl.server.{Directives, Route}
import org.slf4j.LoggerFactory

import oathdigital.application.{
  GameApplicationError,
  GameCommand,
  GameIntentMapper
}
import oathdigital.protocol.{MajorActionPreviewRequest, MajorActionPreviewResponse,
  PreviewIgnoredRule, PreviewModifier, PreviewTarget}
import oathdigital.protocol.projection.GameProjection
import oathdigital.model.PlayerId

private[server] object CommandRejectionMessage {
  def text(violation: oathdigital.model.OathViolation): String =
    violation match {
      case oathdigital.model.OathViolation.NegotiationUnavailable(detail) => detail
      case oathdigital.model.OathViolation.InsufficientFavor(required, available) =>
        s"required favor $required exceeds available $available"
      case oathdigital.model.OathViolation.InsufficientSupply(required, available) =>
        s"required Supply $required exceeds available $available"
      case oathdigital.model.OathViolation.InsufficientSecrets(required, available) =>
        s"required secrets $required exceed available $available"
      case oathdigital.model.OathViolation.InvalidModifierInvocation(message) => message
      case other => other.toString
    }
}

final class GameServerGateway(
    service: oathdigital.application.GameApplicationService,
    projector: oathdigital.application.GameProjector
) {
  def preview(gameId: String, requestingPlayer: PlayerId,
      request: MajorActionPreviewRequest)
      : Either[GameApplicationError, MajorActionPreviewResponse] = for {
    action <- oathdigital.model.ActionKind.fromKey(request.action).toRight(
      GameApplicationError.BootstrapFailure("unknown major action"))
    selected <- GameIntentMapper.bindModifiers(requestingPlayer,
      request.orderedModifiers).left.map(error =>
        GameApplicationError.BootstrapFailure(s"${error.path}: ${error.message}"))
    accepted <- service.preview(gameId, request.expectedNextSequence,
      requestingPlayer, action, selected)
    projection = projector.project(gameId, accepted.loaded, requestingPlayer)
    _ <- Either.cond(projection.activeParticipantId.contains(requestingPlayer.value) &&
      projection.actionSelectionOpen, (), GameApplicationError.CommandRejected(
      oathdigital.model.OathViolation.InvalidModifierInvocation(
        "major-action preview is unavailable for this actor or phase")))
    _ <- MajorActionPreviewTargets.validate(projection, request)
  } yield MajorActionPreviewResponse(accepted.loaded.nextSequence, request.action,
    accepted.options.map(v => PreviewModifier(v.source.stableKey, v.handlerId,
      v.handlerId)), accepted.ignored.map(v => PreviewIgnoredRule(
      v.source.stableKey, v.handlerId, v.timing.key, v.reason)),
    MajorActionPreviewTargets.from(projection, request, accepted.targets))
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
      projector.project(
        gameId,
        oathdigital.application.LoadedGame(
          accepted.state,
          accepted.nextSequence
        ),
        requestingPlayer
      )
    }

  def load(
      gameId: String,
      requestingPlayer: PlayerId
  ): Either[GameApplicationError, GameProjection] =
    service.load(gameId).flatMap {
      case Some(loaded) =>
        Right(projector.project(gameId, loaded, requestingPlayer))
      case None =>
        Left(GameApplicationError.StreamNotFound(gameId))
    }
}

private[server] object MajorActionPreviewTargets {
  def validate(projection: GameProjection, request: MajorActionPreviewRequest)
      : Either[GameApplicationError, Unit] =
    request.baseParameters.get("procedure") match {
      case Some("facedown-adviser") if request.action == "search" =>
        Either.cond(projection.minorActions.exists(_.advisers.exists(
          _.placements.nonEmpty)), (), GameApplicationError.CommandRejected(
          oathdigital.model.OathViolation.InvalidModifierInvocation(
            "no facedown adviser can legally be resolved")))
      case Some(_) => Left(GameApplicationError.CommandRejected(
        oathdigital.model.OathViolation.InvalidModifierInvocation(
          "unknown major-action procedure")))
      case None => Right(())
    }

  /** `walker` is the targets the application already costed against the
    * modifiers THIS request selected (batch-1 Task 5). Every other branch
    * reads the projection, which is costed before the player has chosen
    * anything -- correct for opening a preview, wrong once a modifier is in
    * hand, which is why a walker action supplies its own.
    */
  def from(projection: GameProjection, request: MajorActionPreviewRequest,
      walker: Vector[PreviewTarget] = Vector.empty)
      : Vector[PreviewTarget] = request.action match {
    case "travel" => walker
    case "search" if request.baseParameters.get("procedure")
        .contains("facedown-adviser") => Vector.empty
    case "search" => projection.legalSearchSources.map(v => PreviewTarget(
      s"${v.kind}:${v.region.getOrElse("")}", v.supplyCost, "Search source"))
    case _ => Vector.empty
  }
}

final class GameRoutes(
    gateway: GameServerGateway,
    blockingExecutionContext: ExecutionContext
) extends Directives {
  private val logger = LoggerFactory.getLogger(classOf[GameRoutes])

  val route: Route =
    pathPrefix("api" / "dev" / "first-games" / Segment) { gameId =>
      path("events") {
        get {
          parameter("limit".as[Int].withDefault(25)) { limit =>
            DevelopmentTrustBoundary.validateIdentifier(gameId, "$.gameId") match {
              case Left(error) => complete(inputError(error))
              case Right(validGameId) if limit < 1 || limit > 100 =>
                complete(jsonResponse(StatusCodes.BadRequest, "malformed-request",
                  "$.limit: must be between 1 and 100"))
              case Right(validGameId) => completeRawHistory(
                gateway.rawEventHistory(validGameId, limit))
            }
          }
        }
      } ~ parameter("playerId") { playerId =>
        validateIdentifiers(gameId, playerId) match {
          case Left(error) =>
            complete(inputError(error))
          case Right((validGameId, validPlayerId)) =>
          pathEndOrSingleSlash {
            get {
              completeAsync(gateway.load(
                validGameId,
                PlayerId(validPlayerId)
              ))
            }
          } ~
            path("preview") {
              post {
                entity(as[String]) { body =>
                  oathdigital.protocol.MajorActionPreviewCodec.decode(body) match {
                    case Left(error) => complete(jsonResponse(StatusCodes.BadRequest,
                      "malformed-request", s"${error.path}: ${error.message}"))
                    case Right(request) => completePreview(gateway.preview(validGameId,
                      PlayerId(validPlayerId), request))
                  }
                }
              }
            } ~
            path("commands") {
              post {
                entity(as[String]) { body =>
                  GameHttpWire.decodeCommand(body) match {
                    case Left(error) =>
                      complete(jsonResponse(
                        StatusCodes.BadRequest,
                        "malformed-request",
                        s"${error.path}: ${error.message}"
                      ))
                    case Right(request) =>
                      GameIntentMapper.bind(PlayerId(validPlayerId), request.intent,
                        request.orderedModifiers) match {
                        case Left(error) => complete(jsonResponse(StatusCodes.BadRequest,
                          "malformed-request", s"${error.path}: ${error.message}"))
                        case Right(command) => completeAsync(gateway.submit(
                          validGameId, PlayerId(validPlayerId),
                          request.expectedNextSequence, command))
                      }
                  }
                }
              }
            }
        }
      }
    }

  private def completePreview(operation: => Either[GameApplicationError,
      MajorActionPreviewResponse]): Route =
    onComplete(Future(operation)(blockingExecutionContext)) {
      case Success(Right(value)) => complete(HttpResponse(StatusCodes.OK,
        entity = HttpEntity(ContentTypes.`application/json`,
          oathdigital.protocol.MajorActionPreviewCodec.encode(value))))
      case Success(Left(error)) =>
        val (status, code, message, _) = publicError(error)
        complete(jsonResponse(status, code, message))
      case Failure(error) =>
        logger.error("Unhandled major-action preview failure", error)
        complete(jsonResponse(StatusCodes.InternalServerError, "internal-error",
          "the server could not complete the request"))
    }

  private def completeRawHistory(
      operation: => Either[GameApplicationError, Vector[String]]
  ): Route = onComplete(Future(operation)(blockingExecutionContext)) {
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
  }

  private def completeAsync(
      operation: => Either[GameApplicationError, GameProjection]
  ): Route =
    onComplete(Future(operation)(blockingExecutionContext)) {
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
        if (internal)
          logger.error("Internal first-game application failure: {}", error)
        complete(jsonResponse(status, code, message))
      case Failure(error) =>
        logger.error("Unhandled first-game route failure", error)
        complete(jsonResponse(
          StatusCodes.InternalServerError,
          "internal-error",
          "the server could not complete the request"
        ))
    }

  private def publicError(
      error: GameApplicationError
  ): (StatusCode, String, String, Boolean) =
    error match {
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
    }

  private def validateIdentifiers(
      gameId: String,
      playerId: String
  ): Either[HttpInputError, (String, String)] =
    for {
      game <- DevelopmentTrustBoundary.validateIdentifier(gameId, "$.gameId")
      player <- DevelopmentTrustBoundary.validateIdentifier(
        playerId,
        "$.playerId"
      )
    } yield game -> player

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
}
