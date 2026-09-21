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

  private def open(path: Path): OwnedHsqldbIdentityRepository =
    OwnedHsqldbIdentityRepository.open(path)
      .fold(error => fail(s"database open failed: $error"), identity)

  private val owner = UserId("user-owner")
  private val player = UserId("user-player")
  private val spectator = UserId("user-spectator")

  test("identity migration is idempotent and survives close and reopen") {
    val path = databasePath("migration")
    val first = open(path)
    assertEquals(first.schemaVersion, Right(4))
    assertEquals(first.initializeSchema(), Right(()))
    assertEquals(first.createUser(owner, "Owner", 10L), Right(()))
    assertEquals(first.createGame("game-1", owner, 11L), Right(()))
    first.close()

    val reopened = open(path)
    try {
      assertEquals(reopened.schemaVersion, Right(4))
      assertEquals(
        reopened.findMembership("game-1", owner),
        Right(Some(GameMembership("game-1", owner, Owner, None)))
      )
      assertEquals(reopened.initializeSchema(), Right(()))
    } finally reopened.close()
  }

  test("schema upgrades contiguously from v1, v2, and v3 and revokes old sessions") {
    val v1Path = databasePath("upgrade-v1")
    seedVersionLedger(v1Path, 1)
    val upgradedV1 = open(v1Path)
    assertEquals(upgradedV1.schemaVersion, Right(4))
    upgradedV1.close()

    val v2Path = databasePath("upgrade-v2")
    val oldDigest = SessionTokenDigest.fromBytes(Vector.fill(32)(9.toByte))
      .toOption.get
    seedVersion2(v2Path, oldDigest)
    val upgradedV2 = open(v2Path)
    try {
      assertEquals(upgradedV2.schemaVersion, Right(4))
      assertEquals(
        upgradedV2.resolveSession(oldDigest, 150L),
        Left(SessionRevoked)
      )
      assertEquals(upgradedV2.initializeSchema(), Right(()))
    } finally upgradedV2.close()

    val v3Path = databasePath("upgrade-v3")
    seedVersion3(v3Path)
    val upgradedV3 = open(v3Path)
    try assertEquals(upgradedV3.schemaVersion, Right(4))
    finally upgradedV3.close()

  }

  test("trusted seats atomically create a resource and resolve digests after reopen") {
    val path = databasePath("trusted-seats")
    val first = open(path)
    val seats = Vector(seatDigest(1) -> "p1", seatDigest(2) -> "p2")
    try {
      assertEquals(first.createTrustedSeats("game-seats", seats, 50L), Right(()))
      assertEquals(
        first.resolveTrustedSeat(seatDigest(1)),
        Right(TrustedSeat("game-seats", "p1"))
      )
      assertEquals(first.listMemberships("game-seats"), Right(Vector.empty))
    } finally first.close()

    val reopened = open(path)
    try assertEquals(
      reopened.resolveTrustedSeat(seatDigest(2)),
      Right(TrustedSeat("game-seats", "p2"))
    ) finally reopened.close()
  }

  test("trusted seat creation rejects invalid input and rolls back duplicate digests") {
    val repository = open(databasePath("trusted-seat-rollback"))
    val repeated = seatDigest(3)
    try {
      assert(repository.createTrustedSeats("empty", Vector.empty, 0L)
        .left.toOption.get.isInstanceOf[InvalidTrustedSeat])
      assert(repository.createTrustedSeats(
        "blank", Vector(seatDigest(4) -> " "), 0L
      ).left.toOption.get.isInstanceOf[InvalidTrustedSeat])
      assert(repository.createTrustedSeats(
        "same-player", Vector(seatDigest(5) -> "p1", seatDigest(6) -> "p1"), 0L
      ).left.toOption.get.isInstanceOf[InvalidTrustedSeat])
      assertEquals(
        repository.createTrustedSeats(
          "same-player", Vector(seatDigest(5) -> "p1"), 0L
        ),
        Right(())
      )
      assertEquals(
        repository.createTrustedSeats(
          "duplicate-digest", Vector(repeated -> "p1", repeated -> "p2"), 0L
        ),
        Left(DuplicateTrustedSeat)
      )
      assertEquals(
        repository.createTrustedSeats(
          "duplicate-digest", Vector(seatDigest(7) -> "p1"), 0L
        ),
        Right(())
      )
    } finally repository.close()
  }

  test("trusted seat creation rejects a null player ID before transaction") {
    val repository = open(databasePath("trusted-seat-null-player"))
    try {
      assertEquals(
        repository.createTrustedSeats(
          "null-player", Vector(seatDigest(8) -> null), 0L
        ),
        Left(InvalidTrustedSeat("trusted seat requires playerId"))
      )
      assertEquals(
        repository.createTrustedSeats(
          "null-player", Vector(seatDigest(8) -> "p1"), 0L
        ),
        Right(())
      )
    } finally repository.close()
  }

  test("deleting a game resource cascades to its trusted seats") {
    val path = databasePath("trusted-seat-cascade")
    val digest = seatDigest(8)
    val repository = open(path)
    try assertEquals(
      repository.createTrustedSeats("game-cascade", Vector(digest -> "p1"), 0L),
      Right(())
    ) finally repository.close()

    val connection = DriverManager.getConnection(
      s"jdbc:hsqldb:file:${path.toAbsolutePath}", "SA", "")
    try {
      val statement = connection.createStatement()
      try {
        statement.executeUpdate("DELETE FROM game_resources WHERE game_id = 'game-cascade'")
        statement.execute("SHUTDOWN")
      } finally statement.close()
    } finally connection.close()

    val reopened = open(path)
    try assertEquals(reopened.resolveTrustedSeat(digest), Left(TrustedSeatNotFound))
    finally reopened.close()
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
          GameMembership("game-1", UserId("user-other"), Player, Some("p0")),
          2L
        ),
        Right(())
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
      assertEquals(
        repository.listMemberships("game-1"),
        Right(Vector(
          GameMembership("game-1", owner, Owner, None),
          GameMembership(
            "game-1", UserId("user-other"), Player, Some("p0")),
          GameMembership("game-1", player, Player, Some("p1")),
          GameMembership("game-1", spectator, Spectator, None)
        ))
      )
      assertEquals(
        repository.listMemberships("missing"),
        Left(GameNotFound("missing"))
      )
    } finally repository.close()
  }

  test("typed create-game failure rolls back resource and owner membership") {
    val repository = open(databasePath("create-rollback"))
    try {
      repository.createUser(owner, "Owner", 0L)
      repository.createUser(player, "Player", 0L)
      val failed = repository.createGameWithBeforeOwnerMembership(
        "game-rollback",
        owner,
        1L
      )(_ => Left(InvalidMembership("injected second-write failure")))
      assertEquals(
        failed,
        Left(InvalidMembership("injected second-write failure"))
      )
      assertEquals(
        repository.findMembership("game-rollback", owner),
        Right(None)
      )
      assertEquals(
        repository.addMembership(
          GameMembership("game-rollback", player, Player, Some("p1")),
          2L
        ),
        Left(GameNotFound("game-rollback"))
      )
      assertEquals(repository.createGame("game-rollback", owner, 3L), Right(()))
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
      val session = StoredSession(
        digest, owner, 100L, 100L, 200L, 300L, None, Some(csrfDigest(1))
      )
      assertEquals(repository.createSession(session), Right(()))
      assertEquals(repository.createSession(session), Left(DuplicateSession))
      assertEquals(repository.resolveSession(digest, 199L), Right(session))
      assertEquals(repository.touchSession(digest, 150L, 250L), Right(()))
      assertEquals(repository.resolveSession(digest, 225L).toOption.get
        .lastSeenAtMillis, 150L)
      assert(repository.touchSession(digest, 125L, 290L).left.toOption.get
        .isInstanceOf[InvalidSession])
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

  test("session creation rejects inconsistent expiry and revocation times") {
    val repository = open(databasePath("session-times"))
    val digest = SessionTokenDigest.fromBytes(Vector.fill(32)(3.toByte))
      .toOption.get
    try {
      repository.createUser(owner, "Owner", 0L)
      val base = StoredSession(
        digest, owner, 100L, 100L, 200L, 300L, None, Some(csrfDigest(2))
      )
      assert(repository.createSession(
        base.copy(idleExpiresAtMillis = 301L)
      ).left.toOption.get.isInstanceOf[InvalidSession])
      assert(repository.createSession(
        base.copy(revokedAtMillis = Some(99L))
      ).left.toOption.get.isInstanceOf[InvalidSession])
      assert(repository.createSession(
        base.copy(csrfTokenDigest = None)
      ).left.toOption.get.isInstanceOf[InvalidSession])
      assertEquals(repository.resolveSession(digest, 100L), Left(SessionNotFound))
    } finally repository.close()
  }

  test("closed repository maps database faults to typed storage failure") {
    val repository = open(databasePath("storage-failure"))
    repository.close()
    assert(repository.createUser(owner, "Owner", 0L).left.toOption.get
      .isInstanceOf[StorageFailure])
    assert(repository.listMemberships("game-1").left.toOption.get
      .isInstanceOf[StorageFailure])
  }

  test("session schema contains only the digest and never a raw token column") {
    val path = databasePath("digest-only")
    val repository = open(path)
    try {
      val result = repository.sessionColumnNames.toOption.get
      assert(result.contains("token_digest"))
      assert(!result.exists(name => name == "token" || name.contains("bearer")))
    } finally repository.close()
  }

  test("trusted seat schema contains only the digest and never a raw code column") {
    val path = databasePath("trusted-digest-only")
    val repository = open(path)
    try {
      val columns = repository.trustedSeatColumnNames.toOption.get
      assert(columns.contains("token_digest"))
      assert(!columns.exists(name => name == "token" || name.contains("code")))
    } finally repository.close()
  }

  private def csrfDigest(value: Byte): CsrfTokenDigest =
    CsrfTokenDigest.fromBytes(Vector.fill(32)(value)).toOption.get

  private def seatDigest(value: Byte): SeatCodeDigest =
    SeatCodeDigest.fromBytes(Vector.fill(32)(value)).toOption.get

  private def seedVersionLedger(path: Path, version: Int): Unit = {
    val connection = DriverManager.getConnection(
      s"jdbc:hsqldb:file:${path.toAbsolutePath}", "SA", "")
    try {
      val statement = connection.createStatement()
      try {
        statement.execute(
          """CREATE TABLE schema_versions (
            |version INTEGER PRIMARY KEY,
            |applied_at_epoch_millis BIGINT NOT NULL)""".stripMargin)
        (1 to version).foreach(installed => statement.execute(
          s"INSERT INTO schema_versions VALUES ($installed, 0)"))
        statement.execute("SHUTDOWN")
      } finally statement.close()
    } finally connection.close()
  }

  private def seedVersion2(
      path: Path,
      digest: SessionTokenDigest
  ): Unit = {
    seedVersionLedger(path, 2)
    val connection = DriverManager.getConnection(
      s"jdbc:hsqldb:file:${path.toAbsolutePath}", "SA", "")
    try {
      val statement = connection.createStatement()
      try {
        statement.execute(
          """CREATE TABLE users (
            |user_id VARCHAR(128) PRIMARY KEY,
            |display_name VARCHAR(128) NOT NULL,
            |created_at_millis BIGINT NOT NULL)""".stripMargin)
        statement.execute(
          """CREATE TABLE sessions (
            |token_digest BINARY(32) PRIMARY KEY,
            |user_id VARCHAR(128) NOT NULL,
            |created_at_millis BIGINT NOT NULL,
            |last_seen_at_millis BIGINT NOT NULL,
            |idle_expires_at_millis BIGINT NOT NULL,
            |absolute_expires_at_millis BIGINT NOT NULL,
            |revoked_at_millis BIGINT)""".stripMargin)
        statement.execute(
          """CREATE TABLE game_resources (
            |game_id VARCHAR(255) PRIMARY KEY,
            |created_at_millis BIGINT NOT NULL)""".stripMargin)
        statement.execute("INSERT INTO users VALUES ('old-user', 'Old', 0)")
      } finally statement.close()
      val insert = connection.prepareStatement(
        "INSERT INTO sessions VALUES (?, 'old-user', 100, 100, 200, 300, NULL)")
      try {
        insert.setBytes(1, digest.bytes.toArray)
        insert.executeUpdate()
      } finally insert.close()
      val shutdown = connection.createStatement()
      try shutdown.execute("SHUTDOWN") finally shutdown.close()
    } finally connection.close()
  }

  private def seedVersion3(path: Path): Unit = {
    seedVersion2(path, SessionTokenDigest.fromBytes(Vector.fill(32)(9.toByte))
      .toOption.get)
    val connection = DriverManager.getConnection(
      s"jdbc:hsqldb:file:${path.toAbsolutePath}", "SA", "")
    try {
      val statement = connection.createStatement()
      try {
        statement.execute(
          "ALTER TABLE sessions ADD COLUMN csrf_token_digest BINARY(32)"
        )
        statement.execute("INSERT INTO schema_versions VALUES (3, 0)")
        statement.execute("SHUTDOWN")
      } finally statement.close()
    } finally connection.close()
  }
}
