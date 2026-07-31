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

import oathdigital.application.{
  FirstGameApplicationError,
  FirstGameCommand,
  FirstGameProjection
}
import oathdigital.model.PlayerId

final class FirstGameServerGateway(
    service: oathdigital.application.FirstGameApplicationService,
    projector: oathdigital.application.FirstGameProjector
) {
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
  val route: Route =
    pathPrefix("api" / "dev" / "first-games" / Segment) { gameId =>
      parameter("playerId") { playerId =>
        if (playerId.trim.isEmpty)
          complete(jsonResponse(
            StatusCodes.BadRequest,
            "malformed-request",
            "$.playerId: must not be blank"
          ))
        else
          pathEndOrSingleSlash {
            get {
              completeAsync(gateway.load(gameId, PlayerId(playerId)))
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
                      completeAsync(gateway.submit(
                        gameId,
                        PlayerId(playerId),
                        request.expectedNextSequence,
                        request.command
                      ))
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
        val (status, code) = statusFor(error)
        complete(jsonResponse(status, code, error.toString))
      case Failure(error) =>
        complete(jsonResponse(
          StatusCodes.InternalServerError,
          "internal-error",
          Option(error.getMessage).getOrElse("unexpected server failure")
        ))
    }

  private def statusFor(
      error: FirstGameApplicationError
  ): (StatusCode, String) =
    error match {
      case _: FirstGameApplicationError.StreamNotFound =>
        StatusCodes.NotFound -> "stream-not-found"
      case _: FirstGameApplicationError.StaleClientPosition =>
        StatusCodes.Conflict -> "stale-client-position"
      case _: FirstGameApplicationError.SequenceConflict =>
        StatusCodes.Conflict -> "sequence-conflict"
      case _: FirstGameApplicationError.DuplicateGame =>
        StatusCodes.Conflict -> "duplicate-game"
      case _: FirstGameApplicationError.CommandRejected =>
        StatusCodes.UnprocessableContent -> "command-rejected"
      case _ =>
        StatusCodes.InternalServerError -> "stream-failure"
    }

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
