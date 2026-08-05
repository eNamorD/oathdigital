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

sealed trait AuthenticatedGameFailure extends Product with Serializable
object AuthenticatedGameFailure {
  final case class Authorization(error: AuthorizationFailure)
      extends AuthenticatedGameFailure
  final case class Application(error: FirstGameApplicationError)
      extends AuthenticatedGameFailure
  final case class Identity(error: IdentityFailure)
      extends AuthenticatedGameFailure
  final case class BootstrapConfiguration(message: String)
      extends AuthenticatedGameFailure
}

final class AuthenticatedFirstGameGateway(
    service: FirstGameApplicationService,
    projector: FirstGameProjector,
    authorization: MembershipAuthorizationService,
    identities: IdentityRepository,
    planFactory: FirstGamePlanFactory
) {
  import AuthenticatedGameFailure._

  def load(
      gameId: String,
      principal: AuthenticatedPrincipal
  ): Either[AuthenticatedGameFailure, FirstGameProjection] =
    authorization.authorizeProjection(gameId, principal)
      .left.map(Authorization)
      .flatMap { access =>
        service.load(gameId).left.map(Application).flatMap {
          case None => Left(Application(
            FirstGameApplicationError.StreamNotFound(gameId)
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
      request: AuthenticatedCommandRequest
  ): Either[AuthenticatedGameFailure, FirstGameProjection] =
    authorization.authorizeCommand(gameId, principal)
      .left.map(Authorization)
      .flatMap { actor =>
        val command = request.intent match {
          case FirstGameIntent.PlacePawn(siteId) => actor.placePawn(siteId)
          case FirstGameIntent.ChooseAdviser(adviserId) =>
            actor.chooseAdviser(adviserId)
        }
        service.handle(gameId, request.expectedNextSequence, command)
          .left.map(Application)
          .map(accepted => projector.project(
            gameId,
            LoadedFirstGame(accepted.state, accepted.nextSequence),
            actor.access.playerId
          ))
      }

  def bootstrap(
      gameId: String,
      principal: AuthenticatedPrincipal,
      request: AuthenticatedBootstrapRequest
  ): Either[AuthenticatedGameFailure, FirstGameProjection] =
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
                  FirstGameCommand.Begin(plan)
                ).left.map(Application))
                .map(accepted => projector.projectPublic(
                  gameId,
                  LoadedFirstGame(accepted.state, accepted.nextSequence)
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

final class AuthenticatedFirstGameRoutes(
    authenticator: HttpSessionAuthenticator,
    csrfProtection: SameOriginCsrfProtection,
    gateway: AuthenticatedFirstGameGateway,
    blockingExecutionContext: ExecutionContext
) extends Directives {
  private val logger = LoggerFactory.getLogger(
    classOf[AuthenticatedFirstGameRoutes]
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
                        AuthenticatedFirstGameHttpWire.decodeCommand(body) match {
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
                        AuthenticatedFirstGameHttpWire.decodeBootstrap(body) match {
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
      operation: => Either[AuthenticatedGameFailure, FirstGameProjection]
  ): Route =
    onComplete(Future(operation)(blockingExecutionContext)) {
      case Success(Right(projection)) => complete(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          FirstGameHttpWire.encodeProjection(projection)
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
    case AuthenticatedGameFailure.Application(error) => error match {
      case _: FirstGameApplicationError.StreamNotFound =>
        (StatusCodes.NotFound, "stream-not-found",
          "the requested game does not exist", false)
      case _: FirstGameApplicationError.StaleClientPosition =>
        (StatusCodes.Conflict, "stale-client-position",
          "the client position is stale; refresh and retry", false)
      case _: FirstGameApplicationError.SequenceConflict =>
        (StatusCodes.Conflict, "sequence-conflict",
          "the game changed while the command was handled", false)
      case _: FirstGameApplicationError.CommandRejected =>
        (StatusCodes.UnprocessableContent, "command-rejected",
          "the setup rules rejected the command", false)
      case _: FirstGameApplicationError.DuplicateGame =>
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
      FirstGameHttpWire.encodeError(code, message)
    )
  )
}
