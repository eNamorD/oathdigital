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
  FirstGameApplicationError,
  FirstGameCommand,
  FirstGameProjection
}
import oathdigital.model.PlayerId

final class FirstGameServerGateway(
    service: oathdigital.application.FirstGameApplicationService,
    projector: oathdigital.application.FirstGameProjector,
    planFactory: oathdigital.application.DevelopmentFirstGamePlanFactory
) {
  def bootstrap(
      gameId: String,
      requestingPlayer: PlayerId,
      request: FirstGameBootstrapRequest
  ): Either[FirstGameApplicationError, FirstGameProjection] =
    planFactory.build(request.config)
      .left.map(failure =>
        FirstGameApplicationError.BootstrapFailure(failure.message))
      .flatMap(plan =>
        submit(
          gameId,
          requestingPlayer,
          request.expectedNextSequence,
          FirstGameCommand.Begin(plan)
        ))

  def submit(
      gameId: String,
      requestingPlayer: PlayerId,
      expectedNextSequence: Long,
      command: FirstGameCommand
  ): Either[FirstGameApplicationError, FirstGameProjection] =
    service.handle(gameId, expectedNextSequence, command).map { accepted =>
      projector.project(
        gameId,
        oathdigital.application.LoadedFirstGame(
          accepted.state,
          accepted.nextSequence
        ),
        requestingPlayer
      )
    }

  def load(
      gameId: String,
      requestingPlayer: PlayerId
  ): Either[FirstGameApplicationError, FirstGameProjection] =
    service.load(gameId).flatMap {
      case Some(loaded) =>
        Right(projector.project(gameId, loaded, requestingPlayer))
      case None =>
        Left(FirstGameApplicationError.StreamNotFound(gameId))
    }
}

final class FirstGameRoutes(
    gateway: FirstGameServerGateway,
    blockingExecutionContext: ExecutionContext
) extends Directives {
  private val logger = LoggerFactory.getLogger(classOf[FirstGameRoutes])

  val route: Route =
    pathPrefix("api" / "dev" / "first-games" / Segment) { gameId =>
      parameter("playerId") { playerId =>
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
            path("commands") {
              post {
                entity(as[String]) { body =>
                  FirstGameHttpWire.decodeCommand(body) match {
                    case Left(error) =>
                      complete(jsonResponse(
                        StatusCodes.BadRequest,
                        "malformed-request",
                        s"${error.path}: ${error.message}"
                      ))
                    case Right(request) =>
                      validateActor(validPlayerId, request.command) match {
                        case Left(error) =>
                          complete(jsonResponse(
                            StatusCodes.BadRequest,
                            "actor-selector-mismatch",
                            s"${error.path}: ${error.message}"
                          ))
                        case Right(_) =>
                          completeAsync(gateway.submit(
                            validGameId,
                            PlayerId(validPlayerId),
                            request.expectedNextSequence,
                            request.command
                          ))
                      }
                  }
                }
              }
            } ~
            path("bootstrap") {
              post {
                entity(as[String]) { body =>
                  FirstGameHttpWire.decodeBootstrap(body) match {
                    case Left(error) =>
                      complete(jsonResponse(
                        StatusCodes.BadRequest,
                        "malformed-request",
                        s"${error.path}: ${error.message}"
                      ))
                    case Right(request) =>
                      completeAsync(gateway.bootstrap(
                        validGameId,
                        PlayerId(validPlayerId),
                        request
                      ))
                  }
                }
              }
            }
        }
      }
    }

  private def completeAsync(
      operation: => Either[FirstGameApplicationError, FirstGameProjection]
  ): Route =
    onComplete(Future(operation)(blockingExecutionContext)) {
      case Success(Right(projection)) =>
        complete(HttpResponse(
          StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            FirstGameHttpWire.encodeProjection(projection)
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
      error: FirstGameApplicationError
  ): (StatusCode, String, String, Boolean) =
    error match {
      case _: FirstGameApplicationError.StreamNotFound =>
        (StatusCodes.NotFound, "stream-not-found",
          "the requested game does not exist", false)
      case _: FirstGameApplicationError.StaleClientPosition =>
        (StatusCodes.Conflict, "stale-client-position",
          "the client position is stale; refresh and retry", false)
      case _: FirstGameApplicationError.SequenceConflict =>
        (StatusCodes.Conflict, "sequence-conflict",
          "the game changed while the command was handled", false)
      case _: FirstGameApplicationError.DuplicateGame =>
        (StatusCodes.Conflict, "duplicate-game",
          "the game already exists", false)
      case _: FirstGameApplicationError.CommandRejected =>
        (StatusCodes.UnprocessableContent, "command-rejected",
          "the setup rules rejected the command", false)
      case _: FirstGameApplicationError.BootstrapFailure =>
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

  private def validateActor(
      selector: String,
      command: FirstGameCommand
  ): Either[HttpInputError, Unit] = {
    val actor = command match {
      case FirstGameCommand.PlacePawn(playerId, _) => Some(playerId.value)
      case FirstGameCommand.ChooseAdviser(playerId, _) => Some(playerId.value)
      case FirstGameCommand.TakeWealth(playerId, _) => Some(playerId.value)
      case FirstGameCommand.EndWake(playerId) => Some(playerId.value)
      case FirstGameCommand.Begin(_) => None
    }
    actor match {
      case Some(value) if value != selector =>
        Left(HttpInputError(
          "$.command.playerId",
          "must match the development playerId selector"
        ))
      case _ => Right(())
    }
  }

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
        FirstGameHttpWire.encodeError(code, message)
      )
    )
}
