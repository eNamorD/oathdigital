package oathdigital.server

import java.nio.file.Files
import java.util.concurrent.{Executors, TimeUnit}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Cookie, HttpCookiePair, RawHeader}

import oathdigital.application._
import oathdigital.persistence.HsqldbDatabaseOwner
import oathdigital.testkit.HttpTestServer.{csrfDigest, csrfToken}
import oathdigital.testkit.IdentityRepositoryStub
import oathdigital.testkit.Unwrap.accepted

class SessionSecuritySuite extends munit.FunSuite:
  private val cookieName = "oath_session"

  test("session cookie authentication accepts a live session and collapses " +
      "every failure to invalid"):
    val database = accepted(HsqldbDatabaseOwner.open(
      Files.createTempDirectory("session-auth-").resolve("database")))
    val identities = database.identities
    val user = UserId("session-user")
    identities.createUser(user, "Session User", 0L)
    given executionContext: ExecutionContext = ExecutionContext.global
    val authenticator = new SessionCookieAuthenticator(
      identities, cookieName, () => 150L, executionContext)

    // Now is 150: an idle expiry of 140 has passed and 300 has not, and a
    // session revoked at 120 was revoked before now.
    val valid = "v" * 43
    val expired = "e" * 43
    val revoked = "r" * 43
    identities.createSession(session(valid, user, idleExpiresAtMillis = 300L,
      revokedAtMillis = None))
    identities.createSession(session(expired, user, idleExpiresAtMillis = 140L,
      revokedAtMillis = None))
    identities.createSession(session(revoked, user, idleExpiresAtMillis = 300L,
      revokedAtMillis = Some(120L)))

    try
      assertEquals(authenticate(authenticator, HttpRequest()),
        Left(AuthenticationFailure.MissingCredential))
      val malformed = authenticate(authenticator, requestWithCookie("short"))
      assertEquals(malformed,
        Left(AuthenticationFailure.InvalidCredential("invalid session")))
      Vector("u" * 43, expired, revoked).foreach { raw =>
        assertEquals(
          authenticate(authenticator, requestWithCookie(raw)),
          Left(AuthenticationFailure.InvalidCredential("invalid session"))
        )
      }
      val live = accepted(authenticate(authenticator, requestWithCookie(valid)))
      assertEquals(live.principal, AuthenticatedUser(user))
      assertEquals(live.csrfTokenDigest, csrfDigest)
      assert(!malformed.toString.contains("short"))
      assertEquals(
        accepted(identities.resolveSession(sessionDigest(valid), 150L)).digest,
        sessionDigest(valid)
      )
    finally database.close()

  test("database session resolution runs on the supplied blocking executor"):
    @volatile var resolutionThread = ""
    // Spy of IdentityRepository: resolveSession records the thread it ran on
    // in `resolutionThread` and answers SessionNotFound.
    val repository = new IdentityRepositoryStub:
      override def resolveSession(digest: SessionTokenDigest, now: Long): Either[IdentityFailure, StoredSession] =
        resolutionThread = Thread.currentThread().getName
        Left(IdentityFailure.SessionNotFound)
    val executor = Executors.newSingleThreadExecutor { runnable =>
      new Thread(runnable, "session-auth-blocking-test")
    }
    val blocking = ExecutionContext.fromExecutor(executor)
    try
      val result = authenticate(
        new SessionCookieAuthenticator(
          repository, cookieName, () => 0L, blocking),
        requestWithCookie("x" * 43)
      )
      assertEquals(result,
        Left(AuthenticationFailure.InvalidCredential("invalid session")))
      assertEquals(resolutionThread, "session-auth-blocking-test")
    finally
      executor.shutdown()
      executor.awaitTermination(5L, TimeUnit.SECONDS)

  test("same-origin CSRF validation uses fixed token digests"):
    val session = AuthenticatedHttpSession(
      AuthenticatedUser(UserId("user")), csrfDigest)
    val protection = new SameOriginCsrfProtection("https://oath.example")
    val valid = HttpRequest(headers = List(
      RawHeader("Origin", "https://oath.example"),
      RawHeader("X-CSRF-Token", csrfToken)
    ))
    assert(protection.validate(valid, session))
    assert(!protection.validate(valid.withHeaders(
      RawHeader("Origin", "https://evil.example"),
      RawHeader("X-CSRF-Token", csrfToken)
    ), session))
    assert(!protection.validate(valid.withHeaders(
      RawHeader("Origin", "https://oath.example"),
      RawHeader("X-CSRF-Token", "w" * 43)
    ), session))
    assert(SensitiveTokenDigest.constantTimeEquals(
      Vector.fill(32)(1.toByte), Vector.fill(32)(1.toByte)))
    assert(!SensitiveTokenDigest.constantTimeEquals(
      Vector.fill(32)(1.toByte), Vector.fill(32)(2.toByte)))

  test("CSRF public origins allow HTTPS and explicit loopback HTTP only"):
    Vector(
      "https://oath.example",
      "http://localhost:8080",
      "http://127.0.0.1:8080"
    ).foreach { origin =>
      assertEquals(
        SameOriginCsrfProtection.validateOrigin(origin),
        Right(origin)
      )
    }

    Vector(
      "ftp://localhost",
      "https:///missing-host",
      "http://oath.example",
      "https://oath.example/path",
      "https://oath.example?query=yes",
      "https://oath.example#fragment"
    ).foreach { origin =>
      assertEquals(
        SameOriginCsrfProtection.validateOrigin(origin),
        Left(SameOriginCsrfProtection.InvalidOriginMessage),
        origin
      )
    }
    intercept[IllegalArgumentException]:
      new SameOriginCsrfProtection("http://localhost.evil.example")

  /** A session for `rawToken`, created and last seen at 100, that expires
    * absolutely at 300. */
  private def session(
      rawToken: String,
      user: UserId,
      idleExpiresAtMillis: Long,
      revokedAtMillis: Option[Long]
  ) = StoredSession(
    sessionDigest(rawToken),
    user,
    createdAtMillis = 100L,
    lastSeenAtMillis = 100L,
    idleExpiresAtMillis = idleExpiresAtMillis,
    absoluteExpiresAtMillis = 300L,
    revokedAtMillis = revokedAtMillis,
    csrfTokenDigest = Some(csrfDigest)
  )

  private def sessionDigest(rawToken: String): SessionTokenDigest =
    accepted(
      SessionTokenDigest.fromBytes(SensitiveTokenDigest.sha256(rawToken)))

  private def requestWithCookie(rawToken: String): HttpRequest =
    HttpRequest(headers = List(Cookie(HttpCookiePair(cookieName, rawToken))))

  private def authenticate(
      authenticator: HttpSessionAuthenticator,
      request: HttpRequest
  ) = Await.result(authenticator.authenticate(request), 5.seconds)
