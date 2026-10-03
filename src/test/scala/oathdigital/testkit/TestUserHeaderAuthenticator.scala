package oathdigital.testkit

import scala.concurrent.Future

import akka.http.scaladsl.model.{HttpRequest => AkkaRequest}

import oathdigital.application.{AuthenticatedUser, AuthenticationFailure,
  UserId}
import oathdigital.server.{AuthenticatedHttpSession, HttpSessionAuthenticator}

/** Stub of HttpSessionAuthenticator: trusts the X-Test-User header (the one
  * `HttpTestServer` sends for its `user`) as the signed-in user, with
  * `HttpTestServer.csrfDigest` as the session's CSRF token digest, and
  * answers MissingCredential when the header is absent. */
object TestUserHeaderAuthenticator extends HttpSessionAuthenticator:
  override def authenticate(request: AkkaRequest): Future[Either[
      AuthenticationFailure, AuthenticatedHttpSession]] = Future.successful(
    request.headers.find(_.name == "X-Test-User").map(header =>
      Right(AuthenticatedHttpSession(AuthenticatedUser(UserId(header.value)),
        HttpTestServer.csrfDigest)))
      .getOrElse(Left(AuthenticationFailure.MissingCredential)))
