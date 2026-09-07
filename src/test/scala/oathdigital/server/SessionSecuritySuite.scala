package oathdigital.server

import java.nio.file.Files
import java.util.concurrent.{Executors, TimeUnit}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Cookie, HttpCookiePair, RawHeader}

import oathdigital.application._
import oathdigital.persistence.HsqldbDatabaseOwner

class SessionSecuritySuite extends munit.FunSuite {
  private val cookieName = "oath_session"
  private val csrfToken = "c" * 43

  test("session cookie authentication collapses expected failures to invalid") {
    val database = HsqldbDatabaseOwner.open(
      Files.createTempDirectory("session-auth-").resolve("database")
    ).toOption.get
    val identities = database.identities
    val user = UserId("session-user")
    identities.createUser(user, "Session User", 0L)
    implicit val executionContext: ExecutionContext = ExecutionContext.global
    val authenticator = new SessionCookieAuthenticator(
      identities, cookieName, () => 150L, executionContext)

    val valid = "v" * 43
    val expired = "e" * 43
    val revoked = "r" * 43
    identities.createSession(session(valid, user, 300L, None))
    identities.createSession(session(expired, user, 140L, None))
    identities.createSession(session(revoked, user, 300L, Some(120L)))

    try {
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
      val accepted = authenticate(authenticator, requestWithCookie(valid))
        .toOption.get
      assertEquals(accepted.principal, AuthenticatedUser(user))
      assertEquals(
        accepted.csrfTokenDigest,
        CsrfTokenDigest.fromBytes(SensitiveTokenDigest.sha256(csrfToken))
          .toOption.get
      )
      assert(!malformed.toString.contains("short"))
      assertEquals(
        identities.resolveSession(sessionDigest(valid), 150L).toOption.get
          .digest,
        sessionDigest(valid)
      )
    } finally database.close()
  }

  test("database session resolution runs on the supplied blocking executor") {
    @volatile var resolutionThread = ""
    val repository = new IdentityRepositoryStub {
      override def resolveSession(digest: SessionTokenDigest, now: Long) = {
        resolutionThread = Thread.currentThread().getName
        Left(IdentityFailure.SessionNotFound)
      }
    }
    val executor = Executors.newSingleThreadExecutor { runnable =>
      new Thread(runnable, "session-auth-blocking-test")
    }
    val blocking = ExecutionContext.fromExecutor(executor)
    try {
      val result = authenticate(
        new SessionCookieAuthenticator(
          repository, cookieName, () => 0L, blocking),
        requestWithCookie("x" * 43)
      )
      assert(result.isLeft)
      assertEquals(resolutionThread, "session-auth-blocking-test")
    } finally {
      executor.shutdown()
      executor.awaitTermination(5L, TimeUnit.SECONDS)
    }
  }

  test("same-origin CSRF validation uses fixed token digests") {
    val digest = CsrfTokenDigest.fromBytes(
      SensitiveTokenDigest.sha256(csrfToken)).toOption.get
    val session = AuthenticatedHttpSession(
      AuthenticatedUser(UserId("user")), digest)
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
    intercept[IllegalArgumentException] {
      new SameOriginCsrfProtection("http://localhost.evil.example")
    }
  }

  test("CSRF public origins allow HTTPS and explicit loopback HTTP only") {
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
      assert(
        SameOriginCsrfProtection.validateOrigin(origin).isLeft,
        origin
      )
    }
  }

  private def session(
      rawToken: String,
      user: UserId,
      idleExpiry: Long,
      revokedAt: Option[Long]
  ) = StoredSession(
    sessionDigest(rawToken),
    user,
    100L,
    100L,
    idleExpiry,
    300L,
    revokedAt,
    Some(CsrfTokenDigest.fromBytes(
      SensitiveTokenDigest.sha256(csrfToken)).toOption.get)
  )

  private def sessionDigest(rawToken: String): SessionTokenDigest =
    SessionTokenDigest.fromBytes(SensitiveTokenDigest.sha256(rawToken))
      .toOption.get

  private def requestWithCookie(rawToken: String): HttpRequest =
    HttpRequest(headers = List(Cookie(HttpCookiePair(cookieName, rawToken))))

  private def authenticate(
      authenticator: HttpSessionAuthenticator,
      request: HttpRequest
  ) = Await.result(authenticator.authenticate(request), 5.seconds)

  private abstract class IdentityRepositoryStub extends IdentityRepository {
    private def unused[A]: Either[IdentityFailure, A] =
      fail("unexpected identity repository operation")
    override def createUser(id: UserId, name: String, now: Long) = unused
    override def linkExternalIdentity(identity: ExternalIdentity, id: UserId) =
      unused
    override def findUser(identity: ExternalIdentity) = unused
    override def createGame(gameId: String, owner: UserId, now: Long) = unused
    override def addMembership(membership: GameMembership, now: Long) = unused
    override def findMembership(gameId: String, userId: UserId) = unused
    override def listMemberships(gameId: String) = unused
    override def createSession(session: StoredSession) = unused
    override def revokeSession(digest: SessionTokenDigest, now: Long) = unused
    override def touchSession(digest: SessionTokenDigest, seen: Long, idle: Long) =
      unused
  }
}
