package oathdigital.persistence

import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

import oathdigital.application.{
  ExpectedStream,
  RepositoryAppendResult,
  SetupApplicationError,
  SetupApplicationService
}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{CatalogRef, PlayerId}
import oathdigital.serialization.WireError.MalformedJson
import oathdigital.setup.SetupCommand.PlacePawn

class HsqldbEventStreamRepositorySuite extends munit.FunSuite {
  implicit private val executionContext: ExecutionContext =
    ExecutionContext.global

  private def databasePath(label: String): Path =
    Files.createTempDirectory(s"oathdigital-$label-").resolve("journal")

  private def open(path: Path): OwnedHsqldbEventStreamRepository =
    OwnedHsqldbEventStreamRepository.open(path).toOption.get

  private val catalog = ExecutableCatalog(
    schemaVersion = "test",
    ref = CatalogRef("test", "1"),
    denizens = Vector.empty,
    relics = Vector.empty,
    edifices = Vector.empty,
    legacies = Vector.empty,
    sites = Vector.empty,
    setupCards = Vector.empty,
    supplyBoards = Vector.empty,
    visions = Vector.empty
  )

  private def seedSchemaVersions(
      path: Path,
      versions: Vector[Int]
  ): Unit = {
    val connection =
      DriverManager.getConnection(s"jdbc:hsqldb:file:${path.toAbsolutePath}",
        "SA", "")
    try {
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
        try {
          insert.setInt(1, version)
          insert.executeUpdate()
        } finally insert.close()
      }
      val shutdown = connection.createStatement()
      try shutdown.execute("SHUTDOWN")
      finally shutdown.close()
    } finally connection.close()
  }

  test("schema upgrades from version zero and initialization is idempotent") {
    val repository = open(databasePath("schema"))
    try {
      assertEquals(repository.schemaVersion, Right(3))
      assertEquals(repository.initializeSchema(), Right(()))
      assertEquals(repository.initializeSchema(), Right(()))
      assertEquals(repository.schemaVersion, Right(3))
    } finally repository.close()
  }

  test("rejects newer and non-contiguous schema ledgers and releases files") {
    Vector(
      "newer" -> Vector(4),
      "gapped" -> Vector(0, 1)
    ).foreach { case (label, versions) =>
      val path = databasePath(label)
      seedSchemaVersions(path, versions)
      val result = HsqldbDatabaseOwner.open(path)
      assert(result.left.toOption.nonEmpty)

      val reopened = DriverManager.getConnection(
        s"jdbc:hsqldb:file:${path.toAbsolutePath}",
        "SA",
        ""
      )
      try {
        val shutdown = reopened.createStatement()
        try shutdown.execute("SHUTDOWN")
        finally shutdown.close()
      } finally reopened.close()
    }
  }

  test("rejects HSQLDB URL property delimiters in configured paths") {
    val unsafe = databasePath("unsafe").resolveSibling(
      "journal;shutdown=true"
    )
    assertEquals(
      HsqldbDatabaseOwner.open(unsafe),
      Left(oathdigital.application.RepositoryFailure.InvalidConfiguration(
        "database path contains an unsafe HSQLDB URL delimiter"
      ))
    )
  }

  test("creates a stream and loads exact records in sequence order") {
    val repository = open(databasePath("create"))
    try {
      assertEquals(
        repository.append(
          "game-create",
          ExpectedStream.MustNotExist,
          Vector("""{"event":"first"}""", """{"event":"second"}""")
        ),
        Right(RepositoryAppendResult.Appended(0L, 2))
      )
      val loaded = repository.load("game-create").toOption.flatten.get
      assertEquals(loaded.gameId, "game-create")
      assertEquals(
        loaded.records,
        Vector("""{"event":"first"}""", """{"event":"second"}""")
      )
      assertEquals(loaded.nextSequence, 2L)
      assertEquals(
        repository.append(
          "game-create",
          ExpectedStream.MustNotExist,
          Vector("duplicate")
        ),
        Right(RepositoryAppendResult.StreamAlreadyExists)
      )
      assertEquals(
        repository.append(
          "missing",
          ExpectedStream.AtNextSequence(0L),
          Vector("missing")
        ),
        Right(RepositoryAppendResult.StreamNotFound)
      )
    } finally repository.close()
  }

  test("appends an ordered multi-event batch atomically") {
    val repository = open(databasePath("batch"))
    try {
      repository.append(
        "game-batch",
        ExpectedStream.MustNotExist,
        Vector("zero")
      )
      assertEquals(
        repository.append(
          "game-batch",
          ExpectedStream.AtNextSequence(1L),
          Vector("one", "two", "three")
        ),
        Right(RepositoryAppendResult.Appended(1L, 3))
      )
      assertEquals(
        repository.load("game-batch").toOption.flatten.get.records,
        Vector("zero", "one", "two", "three")
      )
    } finally repository.close()
  }

  test("sequence conflict writes no part of a proposed batch") {
    val repository = open(databasePath("conflict"))
    try {
      repository.append(
        "game-conflict",
        ExpectedStream.MustNotExist,
        Vector("zero")
      )
      assertEquals(
        repository.append(
          "game-conflict",
          ExpectedStream.AtNextSequence(0L),
          Vector("stale-one", "stale-two")
        ),
        Right(RepositoryAppendResult.SequenceConflict(0L, 1L))
      )
      assertEquals(
        repository.load("game-conflict").toOption.flatten.get.records,
        Vector("zero")
      )
    } finally repository.close()
  }

  test("concurrent creation race has one winner and no partial stream") {
    val repository = open(databasePath("create-race"))
    try {
      val start = new CountDownLatch(1)
      val attempts = Vector("left", "right").map { record =>
        Future {
          start.await()
          repository.append(
            "game-create-race",
            ExpectedStream.MustNotExist,
            Vector(record)
          )
        }
      }
      start.countDown()
      val results = Await.result(Future.sequence(attempts), 20.seconds)

      assertEquals(results.count(_.exists(
        _.isInstanceOf[RepositoryAppendResult.Appended])), 1)
      assertEquals(
        results.count(_ == Right(
          RepositoryAppendResult.StreamAlreadyExists)),
        1
      )
      assertEquals(
        repository.load("game-create-race")
          .toOption.flatten.get.records.size,
        1
      )
    } finally repository.close()
  }

  test("concurrent same-position appends have one winner and one conflict") {
    val repository = open(databasePath("append-race"))
    try {
      repository.append(
        "game-append-race",
        ExpectedStream.MustNotExist,
        Vector("zero")
      )
      val start = new CountDownLatch(1)
      val attempts = Vector("left", "right").map { record =>
        Future {
          start.await()
          repository.append(
            "game-append-race",
            ExpectedStream.AtNextSequence(1L),
            Vector(record)
          )
        }
      }
      start.countDown()
      val results = Await.result(Future.sequence(attempts), 20.seconds)

      assertEquals(results.count(_.exists(
        _.isInstanceOf[RepositoryAppendResult.Appended])), 1)
      assertEquals(
        results.count(_ == Right(
          RepositoryAppendResult.SequenceConflict(1L, 2L))),
        1
      )
      assertEquals(
        repository.load("game-append-race")
          .toOption.flatten.get.records.size,
        2
      )
    } finally repository.close()
  }

  test("database failure rolls back stream creation and the entire batch") {
    val repository = open(databasePath("rollback"))
    try {
      val result = repository.append(
        "game-rollback",
        ExpectedStream.MustNotExist,
        Vector("valid", null)
      )
      assert(result.left.toOption.nonEmpty)
      assertEquals(repository.load("game-rollback"), Right(None))
    } finally repository.close()
  }

  test("close and reopen preserve the authoritative stream") {
    val path = databasePath("restart")
    val first = open(path)
    first.append(
      "game-restart",
      ExpectedStream.MustNotExist,
      Vector("zero", "one")
    )
    first.close()

    val reopened = open(path)
    try {
      assertEquals(
        reopened.load("game-restart").toOption.flatten.get.records,
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
    } finally reopened.close()
  }

  test("malformed stored envelopes propagate through the application service") {
    val repository = open(databasePath("malformed"))
    try {
      repository.append(
        "game-malformed",
        ExpectedStream.MustNotExist,
        Vector("{")
      )
      val result = new SetupApplicationService(catalog, repository)
        .handle(
          "game-malformed",
          PlacePawn(PlayerId("p1"), oathdigital.model.SiteId("site:test"))
        )

      assert(result.left.toOption.get match {
        case SetupApplicationError.DecodeFailure(_: MalformedJson) => true
        case _ => false
      })
      assertEquals(
        repository.load("game-malformed").toOption.flatten.get.records,
        Vector("{")
      )
    } finally repository.close()
  }

}
