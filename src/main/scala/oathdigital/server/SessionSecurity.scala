package oathdigital.server

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import akka.http.scaladsl.model.HttpRequest

import oathdigital.application._

final case class AuthenticatedHttpSession(
    principal: AuthenticatedPrincipal,
    csrfTokenDigest: CsrfTokenDigest
)

trait HttpSessionAuthenticator {
  def authenticate(request: HttpRequest)
      : Future[Either[AuthenticationFailure, AuthenticatedHttpSession]]
}

object SensitiveTokenDigest {
  private val TokenPattern = "[A-Za-z0-9_-]{43,128}".r

  def sha256(rawToken: String): Vector[Byte] =
    MessageDigest.getInstance("SHA-256").digest(
      rawToken.getBytes(StandardCharsets.UTF_8)
    ).toVector

  def isWellFormed(rawToken: String): Boolean =
    TokenPattern.pattern.matcher(rawToken).matches()

  def constantTimeEquals(left: Vector[Byte], right: Vector[Byte]): Boolean =
    MessageDigest.isEqual(left.toArray, right.toArray)
}

final class SessionCookieAuthenticator(
    repository: IdentityRepository,
    cookieName: String,
    nowMillis: () => Long,
    blockingExecutionContext: ExecutionContext
) extends HttpSessionAuthenticator {
  require(cookieName.matches("[A-Za-z0-9_-]{1,64}"), "invalid cookie name")

  override def authenticate(request: HttpRequest) = Future {
    val rawTokens = request.cookies.filter(_.name == cookieName).map(_.value)
    rawTokens match {
      case Seq() => Left(AuthenticationFailure.MissingCredential)
      case Seq(value) if !SensitiveTokenDigest.isWellFormed(value) =>
        Left(AuthenticationFailure.InvalidCredential("invalid session"))
      case Seq(value) =>
        SessionTokenDigest.fromBytes(SensitiveTokenDigest.sha256(value))
          .left.map(_ => AuthenticationFailure.InvalidCredential(
            "invalid session"
          ))
          .flatMap(digest => repository.resolveSession(digest, nowMillis())
            .left.map {
              case IdentityFailure.StorageFailure(message) =>
                AuthenticationFailure.StorageFailure(message)
              case _ => AuthenticationFailure.InvalidCredential(
                "invalid session"
              )
            })
          .flatMap(session => session.csrfTokenDigest match {
            case Some(csrf) => Right(AuthenticatedHttpSession(
              AuthenticatedUser(session.userId),
              csrf
            ))
            case None => Left(AuthenticationFailure.InvalidCredential(
              "invalid session"
            ))
          })
      case _ => Left(AuthenticationFailure.InvalidCredential("invalid session"))
    }
  }(blockingExecutionContext)
}

final class SameOriginCsrfProtection(
    expectedOrigin: String,
    headerName: String = "X-CSRF-Token"
) {
  require(
    validOrigin(expectedOrigin),
    "public origin must be HTTPS or an explicit loopback HTTP origin"
  )

  def validate(
      request: HttpRequest,
      session: AuthenticatedHttpSession
  ): Boolean = {
    val origins = request.headers.filter(_.is("origin")).map(_.value)
    val csrfTokens = request.headers.filter(_.is(headerName.toLowerCase))
      .map(_.value)
    origins == Seq(expectedOrigin) && csrfTokens.size == 1 &&
      SensitiveTokenDigest.isWellFormed(csrfTokens.head) &&
      SensitiveTokenDigest.constantTimeEquals(
        SensitiveTokenDigest.sha256(csrfTokens.head),
        session.csrfTokenDigest.bytes
      )
  }

  private def validOrigin(value: String): Boolean = Try(new URI(value))
    .toOption.exists { uri =>
      val scheme = Option(uri.getScheme).map(_.toLowerCase)
      val host = Option(uri.getHost).map(_.toLowerCase)
      val secure = scheme.contains("https") && host.exists(_.nonEmpty)
      val loopback = scheme.contains("http") && host.exists(
        Set("localhost", "127.0.0.1", "::1").contains)
      (secure || loopback) && uri.getRawUserInfo == null &&
        uri.getRawQuery == null && uri.getRawFragment == null &&
        Option(uri.getRawPath).forall(_.isEmpty)
    }
}
