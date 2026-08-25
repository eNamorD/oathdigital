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

import oathdigital.application._
import oathdigital.protocol.ActorlessCommandRequest

sealed trait AuthenticatedGameFailure extends Product with Serializable
object AuthenticatedGameFailure {
  final case class Authorization(error: AuthorizationFailure)
      extends AuthenticatedGameFailure
  final case class Application(error: GameApplicationError)
      extends AuthenticatedGameFailure
  final case class Identity(error: IdentityFailure)
      extends AuthenticatedGameFailure
  final case class BootstrapConfiguration(message: String)
      extends AuthenticatedGameFailure
  final case class InvalidIntent(error: GameIntentMappingFailure)
      extends AuthenticatedGameFailure
}

final class AuthenticatedGameGateway(
    service: GameApplicationService,
    projector: GameProjector,
    authorization: MembershipAuthorizationService,
    identities: IdentityRepository,
    planFactory: FirstGamePlanFactory
) {
  import AuthenticatedGameFailure._

  def load(
      gameId: String,
      principal: AuthenticatedPrincipal
  ): Either[AuthenticatedGameFailure, GameProjection] =
    authorization.authorizeProjection(gameId, principal)
      .left.map(Authorization)
      .flatMap { access =>
        service.load(gameId).left.map(Application).flatMap {
          case None => Left(Application(
            GameApplicationError.StreamNotFound(gameId)
          ))
          case Some(loaded) => access.scope match {
            case ProjectionScope.PlayerPrivate(playerId) =>
              Right(projector.project(gameId, loaded, playerId))
            case ProjectionScope.PublicOnly =>
              Right(projector.projectPublic(gameId, loaded))
          }
        }
      }

  def submit(
      gameId: String,
      principal: AuthenticatedPrincipal,
      request: ActorlessCommandRequest
  ): Either[AuthenticatedGameFailure, GameProjection] =
    authorization.authorizeCommand(gameId, principal)
      .left.map(Authorization)
      .flatMap { actor =>
        GameIntentMapper.bind(actor.access.playerId, request.intent)
          .left.map(InvalidIntent)
          .flatMap(command => service.handle(gameId,
            request.expectedNextSequence, command).left.map(Application))
          .map(accepted => projector.project(
            gameId,
            LoadedGame(accepted.state, accepted.nextSequence),
            actor.access.playerId
          ))
      }

  def bootstrap(
      gameId: String,
      principal: AuthenticatedPrincipal,
      request: AuthenticatedBootstrapRequest
  ): Either[AuthenticatedGameFailure, GameProjection] =
    authorization.authorizeBootstrap(gameId, principal)
      .left.map(Authorization)
      .flatMap { _ =>
        identities.listMemberships(gameId).left.map(Identity).flatMap {
          memberships =>
            validateSeats(memberships, request.config).flatMap { _ =>
              planFactory.build(request.config)
                .left.map(failure => BootstrapConfiguration(failure.message))
                .flatMap(plan => service.handle(
                  gameId,
                  request.expectedNextSequence,
                  GameCommand.Begin(plan)
                ).left.map(Application))
                .map(accepted => projector.projectPublic(
                  gameId,
                  LoadedGame(accepted.state, accepted.nextSequence)
                ))
            }
        }
      }

  private def validateSeats(
      memberships: Vector[GameMembership],
      config: FirstGameBootstrapConfig
  ): Either[AuthenticatedGameFailure, Unit] = {
    val playerMemberships = memberships.filter(_.role == MembershipRole.Player)
    val provisionedSeats = playerMemberships.flatMap(_.playerId)
    val requestedSeats = config.participants.map(_.playerId.value)
    if (provisionedSeats.size != playerMemberships.size ||
        provisionedSeats.distinct.size != provisionedSeats.size)
      Left(BootstrapConfiguration("provisioned player memberships are invalid"))
    else if (requestedSeats.distinct.size != requestedSeats.size)
      Left(BootstrapConfiguration("participant player IDs must be unique"))
    else if (requestedSeats.toSet != provisionedSeats.toSet)
      Left(BootstrapConfiguration(
        "participants must exactly match provisioned player memberships"
      ))
    else if (!provisionedSeats.contains(config.firstPlayer.value))
      Left(BootstrapConfiguration(
        "first player must be a provisioned player membership"
      ))
    else Right(())
  }
}

final class AuthenticatedGameRoutes(
    authenticator: HttpSessionAuthenticator,
    csrfProtection: SameOriginCsrfProtection,
    gateway: AuthenticatedGameGateway,
    blockingExecutionContext: ExecutionContext
) extends Directives {
  private val logger = LoggerFactory.getLogger(
    classOf[AuthenticatedGameRoutes]
  )

  val route: Route =
    pathPrefix("api" / "authenticated" / "first-games" / Segment) { gameId =>
      extractRequest { request =>
        onComplete(authenticator.authenticate(request)) {
          case Success(Left(AuthenticationFailure.StorageFailure(message))) =>
            logger.error("Session authentication storage failure: {}", message)
            complete(response(
              StatusCodes.InternalServerError,
              "internal-error",
              "the server could not complete the request"
            ))
          case Success(Left(_)) => complete(response(
            StatusCodes.Unauthorized,
            "authentication-required",
            "valid authentication is required"
          ))
          case Failure(error) =>
            logger.error("Unhandled session authentication failure", error)
            complete(response(
              StatusCodes.InternalServerError,
              "internal-error",
              "the server could not complete the request"
            ))
          case Success(Right(session)) =>
            val principal = session.principal
            parameterMap { query =>
              if (query.nonEmpty)
                complete(response(
                  StatusCodes.BadRequest,
                  "malformed-request",
                  "query parameters are not accepted"
                ))
              else DevelopmentTrustBoundary.validateIdentifier(
                gameId,
                "$.gameId"
              ) match {
                case Left(error) => complete(response(
                  StatusCodes.BadRequest,
                  "malformed-request",
                  s"${error.path}: ${error.message}"
                ))
                case Right(validGameId) =>
                  pathEndOrSingleSlash {
                    get {
                      completeAsync(gateway.load(validGameId, principal))
                    }
                  } ~ path("commands") {
                    post {
                      if (!csrfProtection.validate(request, session))
                        complete(csrfFailure)
                      else entity(as[String]) { body =>
                        AuthenticatedGameHttpWire.decodeCommand(body) match {
                          case Left(error) => complete(response(
                            StatusCodes.BadRequest,
                            "malformed-request",
                            s"${error.path}: ${error.message}"
                          ))
                          case Right(command) => completeAsync(
                            gateway.submit(validGameId, principal, command)
                          )
                        }
                      }
                    }
                  } ~ path("bootstrap") {
                    post {
                      if (!csrfProtection.validate(request, session))
                        complete(csrfFailure)
                      else entity(as[String]) { body =>
                        AuthenticatedGameHttpWire.decodeBootstrap(body) match {
                          case Left(error) => complete(response(
                            StatusCodes.BadRequest,
                            "malformed-request",
                            s"${error.path}: ${error.message}"
                          ))
                          case Right(bootstrap) => completeAsync(
                            gateway.bootstrap(validGameId, principal, bootstrap)
                          )
                        }
                      }
                    }
                  }
              }
            }
        }
      }
    }

  private def csrfFailure: HttpResponse = response(
    StatusCodes.Forbidden,
    "csrf-validation-failed",
    "request origin or CSRF token is invalid"
  )

  private def completeAsync(
      operation: => Either[AuthenticatedGameFailure, GameProjection]
  ): Route =
    onComplete(Future(operation)(blockingExecutionContext)) {
      case Success(Right(projection)) => complete(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          GameHttpWire.encodeProjection(projection)
        )
      ))
      case Success(Left(error)) =>
        val (status, code, message, internal) = publicError(error)
        if (internal) logger.error("Authenticated game failure: {}", error)
        complete(response(status, code, message))
      case Failure(error) =>
        logger.error("Unhandled authenticated game route failure", error)
        complete(response(
          StatusCodes.InternalServerError,
          "internal-error",
          "the server could not complete the request"
        ))
    }

  private def publicError(
      failure: AuthenticatedGameFailure
  ): (StatusCode, String, String, Boolean) = failure match {
    case AuthenticatedGameFailure.Authorization(
          _: AuthorizationFailure.NotMember) =>
      (StatusCodes.Forbidden, "forbidden", "access is denied", false)
    case AuthenticatedGameFailure.Authorization(
          _: AuthorizationFailure.Forbidden) =>
      (StatusCodes.Forbidden, "forbidden", "access is denied", false)
    case AuthenticatedGameFailure.Authorization(_) =>
      (StatusCodes.InternalServerError, "internal-error",
        "the server could not complete the request", true)
    case AuthenticatedGameFailure.Identity(
          IdentityFailure.GameNotFound(_)) =>
      (StatusCodes.NotFound, "game-not-found",
        "the requested game resource does not exist", false)
    case AuthenticatedGameFailure.Identity(_) =>
      (StatusCodes.InternalServerError, "internal-error",
        "the server could not complete the request", true)
    case _: AuthenticatedGameFailure.BootstrapConfiguration =>
      (StatusCodes.UnprocessableContent, "membership-configuration-mismatch",
        "participants must exactly match the provisioned player seats", false)
    case AuthenticatedGameFailure.InvalidIntent(_) =>
      (StatusCodes.BadRequest, "malformed-request",
        "the command contains an invalid domain identifier", false)
    case AuthenticatedGameFailure.Application(error) => error match {
      case _: GameApplicationError.StreamNotFound =>
        (StatusCodes.NotFound, "stream-not-found",
          "the requested game does not exist", false)
      case _: GameApplicationError.StaleClientPosition =>
        (StatusCodes.Conflict, "stale-client-position",
          "the client position is stale; refresh and retry", false)
      case _: GameApplicationError.SequenceConflict =>
        (StatusCodes.Conflict, "sequence-conflict",
          "the game changed while the command was handled", false)
      case _: GameApplicationError.CommandRejected =>
        (StatusCodes.UnprocessableContent, "command-rejected",
          "the setup rules rejected the command", false)
      case _: GameApplicationError.DuplicateGame =>
        (StatusCodes.Conflict, "duplicate-game",
          "the game event stream already exists", false)
      case _ =>
        (StatusCodes.InternalServerError, "internal-error",
          "the server could not complete the request", true)
    }
  }

  private def response(
      status: StatusCode,
      code: String,
      message: String
  ): HttpResponse = HttpResponse(
    status,
    entity = HttpEntity(
      ContentTypes.`application/json`,
      GameHttpWire.encodeError(code, message)
    )
  )
}
