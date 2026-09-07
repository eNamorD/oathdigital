package oathdigital.server

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

final case class AuthenticatedRouteMountConfiguration(
    sessionCookieName: String,
    publicOrigin: String
)

object AuthenticatedRouteMountConfiguration {
  def fromOptions(
      sessionCookieName: Option[String],
      publicOrigin: Option[String]
  ): Either[String, Option[AuthenticatedRouteMountConfiguration]] =
    (sessionCookieName, publicOrigin) match {
      case (None, None) => Right(None)
      case (Some(cookie), Some(origin)) if cookie.trim.nonEmpty &&
          origin.trim.nonEmpty =>
        Right(Some(AuthenticatedRouteMountConfiguration(cookie, origin)))
      case _ => Left(
        "authenticated routes require both session cookie name and public origin"
      )
    }
}

object ServerRoutes {
  def route(
      runtime: ServerRuntime,
      blockingExecutionContext: ExecutionContext,
      config: ServerConfig,
      readiness: ServerReadiness,
      nowMillis: () => Long = () => System.currentTimeMillis()
  ): Route = {
    val application = config.mode match {
      case ServerMode.Development =>
        val development = DevelopmentRoutes.route(
          runtime.firstGame,
          blockingExecutionContext
        )
        config.authenticatedRouteMount.fold(development) { configuration =>
          val authenticator = new SessionCookieAuthenticator(
            runtime.identities,
            configuration.sessionCookieName,
            nowMillis,
            blockingExecutionContext
          )
          val csrf = new SameOriginCsrfProtection(configuration.publicOrigin)
          development ~ new AuthenticatedGameRoutes(
            authenticator,
            csrf,
            runtime.authenticatedGame,
            blockingExecutionContext
          ).route
        }
      case ServerMode.TrustedAlpha => ProductionFrontendRoutes.route
    }
    HealthRoutes.route(readiness) ~ application
  }
}
