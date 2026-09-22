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
    val publicOrigin = config.publicBaseUrl.getOrElse(
      new java.net.URI("http", null, config.host, config.port, null, null, null))
    // Development mode binds only to loopback, so a browser may reach it through
    // any loopback alias; accept those origins on the same port.
    val loopbackAliases =
      if (config.mode == ServerMode.Development && config.publicBaseUrl.isEmpty)
        Seq("localhost", "127.0.0.1", "[::1]").map(host =>
          new java.net.URI(s"http://$host:${config.port}"))
      else Nil
    val trustedSeats = new TrustedSeatRoutes(runtime.identities,
      runtime.trustedGameProvisioning, runtime.trustedGame, publicOrigin,
      blockingExecutionContext, loopbackAliases).route
    val application = config.mode match {
      case ServerMode.Development =>
        // The development start page creates games through the same trusted
        // provisioning as trusted-alpha mode, so both get the generated setup.
        val development = DevelopmentRoutes.route(
          runtime.firstGame,
          blockingExecutionContext
        ) ~ trustedSeats
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
      case ServerMode.TrustedAlpha =>
        trustedSeats ~ ProductionFrontendRoutes.route
    }
    HealthRoutes.route(readiness) ~ application
  }
}
