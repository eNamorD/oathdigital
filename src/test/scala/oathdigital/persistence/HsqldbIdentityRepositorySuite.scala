package oathdigital.persistence

import java.nio.file.{Files, Path}
import java.sql.DriverManager

import oathdigital.application._
import oathdigital.application.IdentityFailure._
import oathdigital.application.MembershipRole._

class HsqldbIdentityRepositorySuite extends munit.FunSuite {
  private def databasePath(label: String): Path =
    Files.createTempDirectory(s"oathdigital-identity-$label-")
      .resolve("database")

  private def open(path: Path): HsqldbIdentityRepository =
    HsqldbIdentityRepository.open(path).toOption.get

  private val owner = UserId("user-owner")
  private val player = UserId("user-player")
  private val spectator = UserId("user-spectator")

  test("identity migration is idempotent and survives close and reopen") {
    val path = databasePath("migration")
    val first = open(path)
    assertEquals(first.schemaVersion, Right(2))
    assertEquals(first.initializeSchema(), Right(()))
    assertEquals(first.createUser(owner, "Owner", 10L), Right(()))
    assertEquals(first.createGame("game-1", owner, 11L), Right(()))
    first.close()

    val reopened = open(path)
    try {
      assertEquals(reopened.schemaVersion, Right(2))
      assertEquals(
        reopened.findMembership("game-1", owner),
        Right(Some(GameMembership("game-1", owner, Owner, None)))
      )
      assertEquals(reopened.initializeSchema(), Right(()))
    } finally reopened.close()
  }

  test("external identities are provider-subject unique and reference users") {
    val repository = open(databasePath("external"))
    try {
      val identity = ExternalIdentity("oidc.example", "subject-123")
      assertEquals(
        repository.linkExternalIdentity(identity, owner),
        Left(UserNotFound(owner))
      )
      repository.createUser(owner, "Owner", 0L)
      repository.createUser(player, "Player", 0L)
      assertEquals(repository.linkExternalIdentity(identity, owner), Right(()))
      assertEquals(repository.findUser(identity), Right(Some(owner)))
      assertEquals(
        repository.linkExternalIdentity(identity, player),
        Left(DuplicateExternalIdentity(identity))
      )
      assertEquals(
        repository.linkExternalIdentity(
          ExternalIdentity("another-provider", "subject-123"),
          player
        ),
        Right(())
      )
    } finally repository.close()
  }

  test("game resources precede streams and memberships enforce seats and roles") {
    val repository = open(databasePath("membership"))
    try {
      Vector(owner, player, spectator, UserId("user-other"))
        .foreach(user => repository.createUser(user, user.value, 0L))
      assertEquals(repository.createGame("game-1", owner, 1L), Right(()))
      assertEquals(
        repository.addMembership(
          GameMembership("missing", player, Player, Some("p1")),
          2L
        ),
        Left(GameNotFound("missing"))
      )
      assertEquals(
        repository.addMembership(
          GameMembership("game-1", player, Player, Some("p1")),
          2L
        ),
        Right(())
      )
      assertEquals(
        repository.addMembership(
          GameMembership("game-1", UserId("user-other"), Player, Some("p1")),
          2L
        ),
        Left(PlayerSeatOccupied("game-1", "p1"))
      )
      assertEquals(
        repository.addMembership(
          GameMembership("game-1", player, Spectator, None),
          2L
        ),
        Left(DuplicateMembership("game-1", player))
      )
      assert(repository.addMembership(
        GameMembership("game-1", spectator, Player, None), 2L
      ).left.toOption.get.isInstanceOf[InvalidMembership])
      assert(repository.addMembership(
        GameMembership("game-1", spectator, Spectator, Some("p2")), 2L
      ).left.toOption.get.isInstanceOf[InvalidMembership])
      assertEquals(
        repository.addMembership(
          GameMembership("game-1", spectator, Spectator, None),
          2L
        ),
        Right(())
      )
    } finally repository.close()
  }

  test("sessions resolve only fixed digests and distinguish expiry and revocation") {
    val repository = open(databasePath("sessions"))
    val digest = SessionTokenDigest.fromBytes(Vector.fill(32)(1.toByte))
      .toOption.get
    val second = SessionTokenDigest.fromBytes(Vector.fill(32)(2.toByte))
      .toOption.get
    try {
      repository.createUser(owner, "Owner", 0L)
      val session = StoredSession(digest, owner, 100L, 100L, 200L, 300L, None)
      assertEquals(repository.createSession(session), Right(()))
      assertEquals(repository.createSession(session), Left(DuplicateSession))
      assertEquals(repository.resolveSession(digest, 199L), Right(session))
      assertEquals(repository.touchSession(digest, 150L, 250L), Right(()))
      assertEquals(repository.resolveSession(digest, 225L).toOption.get
        .lastSeenAtMillis, 150L)
      assertEquals(repository.resolveSession(digest, 250L), Left(SessionExpired))
      assertEquals(repository.resolveSession(second, 100L), Left(SessionNotFound))

      val revoked = session.copy(digest = second, idleExpiresAtMillis = 400L,
        absoluteExpiresAtMillis = 500L)
      repository.createSession(revoked)
      assertEquals(repository.revokeSession(second, 150L), Right(()))
      assertEquals(repository.resolveSession(second, 151L), Left(SessionRevoked))
      assertEquals(
        SessionTokenDigest.fromBytes("raw-bearer-token".getBytes.toVector)
          .left.toOption.get,
        "session token digest must contain exactly 32 bytes"
      )
    } finally repository.close()
  }

  test("closed repository maps database faults to typed storage failure") {
    val repository = open(databasePath("storage-failure"))
    repository.close()
    assert(repository.createUser(owner, "Owner", 0L).left.toOption.get
      .isInstanceOf[StorageFailure])
  }

  test("session schema contains only the digest and never a raw token column") {
    val path = databasePath("digest-only")
    val repository = open(path)
    repository.close()
    val connection = DriverManager.getConnection(
      s"jdbc:hsqldb:file:${path.toAbsolutePath}", "SA", ""
    )
    try {
      val columns = connection.getMetaData
        .getColumns(null, null, "SESSIONS", null)
      val names = Vector.newBuilder[String]
      while (columns.next()) names += columns.getString("COLUMN_NAME")
      val result = names.result().map(_.toLowerCase)
      assert(result.contains("token_digest"))
      assert(!result.exists(name => name == "token" || name.contains("bearer")))
      val shutdown = connection.createStatement()
      try shutdown.execute("SHUTDOWN") finally shutdown.close()
    } finally connection.close()
  }
}
