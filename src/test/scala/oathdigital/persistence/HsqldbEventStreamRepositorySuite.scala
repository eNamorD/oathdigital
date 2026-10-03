package oathdigital.persistence

import java.nio.file.{Files, Path}
import java.sql.DriverManager

import oathdigital.application.{
  EventStreamRepositoryContract,
  ExpectedStream,
  RepositoryAppendResult,
  RepositoryFailure,
  StoredEventStream
}
import oathdigital.testkit.Unwrap.{accepted, as, present, rejected}

class HsqldbEventStreamRepositorySuite
    extends EventStreamRepositoryContract[OwnedHsqldbEventStreamRepository]:
  protected def make() = open(databasePath("contract"))

  override protected def release(
      repository: OwnedHsqldbEventStreamRepository): Unit =
    repository.close()

  private def databasePath(label: String): Path =
    Files.createTempDirectory(s"oathdigital-$label-").resolve("journal")

  /** The schema version every journal is migrated to. */
  private val targetVersion = new EventJournalSchema(() => 0L).TargetVersion

  private def open(path: Path): OwnedHsqldbEventStreamRepository =
    OwnedHsqldbEventStreamRepository.open(path)
      .fold(error => fail(s"database open failed: $error"), identity)

  /** The stream `gameId` as stored; fails with the load's failure or when
    * there is no such stream. */
  private def storedStream(repository: OwnedHsqldbEventStreamRepository,
      gameId: String): StoredEventStream =
    present(accepted(repository.load(gameId)), s"stream $gameId")

  private def seedSchemaVersions(
      path: Path,
      versions: Vector[Int]
  ): Unit =
    val connection =
      DriverManager.getConnection(s"jdbc:hsqldb:file:${path.toAbsolutePath}",
        "SA", "")
    try
      val create = connection.createStatement()
      try create.execute(
        """CREATE TABLE schema_versions (
          |version INTEGER PRIMARY KEY,
          |applied_at_epoch_millis BIGINT NOT NULL
          |)""".stripMargin
      )
      finally create.close()
      versions.foreach { version =>
        val insert = connection.prepareStatement(
          """INSERT INTO schema_versions
            |(version, applied_at_epoch_millis) VALUES (?, 0)""".stripMargin
        )
        try
          insert.setInt(1, version)
          insert.executeUpdate()
        finally insert.close()
      }
      val shutdown = connection.createStatement()
      try shutdown.execute("SHUTDOWN")
      finally shutdown.close()
    finally connection.close()

  test("schema upgrades from version zero and initialization is idempotent"):
    val repository = open(databasePath("schema"))
    try
      assertEquals(repository.schemaVersion, Right(targetVersion))
      assertEquals(repository.initializeSchema(), Right(()))
      assertEquals(repository.initializeSchema(), Right(()))
      assertEquals(repository.schemaVersion, Right(targetVersion))
    finally repository.close()

  test("rejects newer and non-contiguous schema ledgers and releases files"):
    Vector(
      ("newer", Vector(targetVersion + 1),
        s"schema version ${targetVersion + 1} is newer than supported " +
          s"version $targetVersion"),
      ("gapped", Vector(0, 1),
        "schema version ledger must be contiguous from 1; found 0,1")
    ).foreach { case (label, versions, reason) =>
      val path = databasePath(label)
      seedSchemaVersions(path, versions)
      val result = HsqldbDatabaseOwner.open(path)
      assertEquals(result.left.toOption, Some(
        oathdigital.application.RepositoryFailure.StorageFailure(
          s"initialize schema failed: $reason")), label)

      val reopened = DriverManager.getConnection(
        s"jdbc:hsqldb:file:${path.toAbsolutePath}",
        "SA",
        ""
      )
      try
        val shutdown = reopened.createStatement()
        try shutdown.execute("SHUTDOWN")
        finally shutdown.close()
      finally reopened.close()
    }

  test("rejects HSQLDB URL property delimiters in configured paths"):
    val unsafe = databasePath("unsafe").resolveSibling(
      "journal;shutdown=true"
    )
    assertEquals(
      HsqldbDatabaseOwner.open(unsafe),
      Left(oathdigital.application.RepositoryFailure.InvalidConfiguration(
        "database path contains an unsafe HSQLDB URL delimiter"
      ))
    )

  test("refuses to create a stream that exists"):
    val repository = open(databasePath("duplicate"))
    try
      repository.append(
        "game-create",
        ExpectedStream.MustNotExist,
        Vector("""{"event":"first"}""")
      )
      assertEquals(
        repository.append(
          "game-create",
          ExpectedStream.MustNotExist,
          Vector("duplicate")
        ),
        Right(RepositoryAppendResult.StreamAlreadyExists)
      )
    finally repository.close()

  test("refuses to append to a stream that does not exist"):
    val repository = open(databasePath("missing"))
    try
      assertEquals(
        repository.append(
          "missing",
          ExpectedStream.AtNextSequence(0L),
          Vector("missing")
        ),
        Right(RepositoryAppendResult.StreamNotFound)
      )
    finally repository.close()

  test("database failure rolls back stream creation and the entire batch"):
    val repository = open(databasePath("rollback"))
    try
      // The journal's envelope_json column is NOT NULL, so inserting the
      // second record fails after the stream row and the first record.
      val result = repository.append(
        "game-rollback",
        ExpectedStream.MustNotExist,
        Vector("valid", null)
      )
      val failure = as[RepositoryFailure.StorageFailure](rejected(result))
      assert(failure.message.startsWith("append event stream failed:"),
        failure)
      assertEquals(repository.load("game-rollback"), Right(None))
    finally repository.close()

  test("close and reopen preserve the authoritative stream"):
    val path = databasePath("restart")
    val first = open(path)
    first.append(
      "game-restart",
      ExpectedStream.MustNotExist,
      Vector("zero", "one")
    )
    first.close()

    val reopened = open(path)
    try
      assertEquals(
        storedStream(reopened, "game-restart").records,
        Vector("zero", "one")
      )
      assertEquals(
        reopened.append(
          "game-restart",
          ExpectedStream.AtNextSequence(2L),
          Vector("two")
        ),
        Right(RepositoryAppendResult.Appended(2L, 1))
      )
    finally reopened.close()
