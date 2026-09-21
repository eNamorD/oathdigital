package oathdigital.persistence

import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

import oathdigital.application._
import oathdigital.application.MembershipRole.Player

class HsqldbDatabaseOwnerSuite extends munit.FunSuite {
  implicit private val executionContext: ExecutionContext =
    ExecutionContext.global

  private def path(label: String): Path =
    Files.createTempDirectory(s"oathdigital-owner-$label-")
      .resolve("database")

  private def open(databasePath: Path): HsqldbDatabaseOwner =
    HsqldbDatabaseOwner.open(databasePath)
      .fold(error => fail(s"database open failed: $error"), identity)

  test("schema ledger records applied-at times from the injected clock") {
    val databasePath = path("clock")
    HsqldbDatabaseOwner.open(databasePath, () => 1234L)
      .fold(error => fail(s"database open failed: $error"), identity).close()

    val connection = DriverManager.getConnection(
      s"jdbc:hsqldb:file:${databasePath.toAbsolutePath}", "SA", "")
    try {
      val rows = connection.createStatement().executeQuery(
        "SELECT version, applied_at_epoch_millis FROM schema_versions " +
          "ORDER BY version")
      val ledger = Iterator.continually(rows).takeWhile(_.next())
        .map(row => row.getInt(1) -> row.getLong(2)).toVector
      assertEquals(ledger, Vector(1 -> 1234L, 2 -> 1234L, 3 -> 1234L, 4 -> 1234L))
    } finally connection.close()
  }

  test("one owner serves identity and journal adapters concurrently") {
    val owner = open(path("concurrent"))
    val identity = owner.identities
    val journal = owner.eventStreams
    try {
      assert(!classOf[AutoCloseable].isAssignableFrom(identity.getClass))
      assert(!classOf[AutoCloseable].isAssignableFrom(journal.getClass))
      identity.createUser(UserId("owner"), "Owner", 0L)
      identity.createGame("game-shared", UserId("owner"), 1L)

      val start = new CountDownLatch(1)
      val journalOperation = Future {
          start.await()
          journal.append(
            "game-shared",
            ExpectedStream.MustNotExist,
            Vector("zero", "one")
          )
        }
      val identityOperation = Future {
          start.await()
          identity.createUser(UserId("player"), "Player", 2L)
            .flatMap(_ => identity.addMembership(
              GameMembership(
                "game-shared",
                UserId("player"),
                Player,
                Some("p1")
              ),
              3L
            ))
        }
      start.countDown()
      assert(Await.result(journalOperation, 20.seconds).isRight)
      assert(Await.result(identityOperation, 20.seconds).isRight)
      assertEquals(
        journal.load("game-shared").toOption.flatten.get.records,
        Vector("zero", "one")
      )
      assert(identity.findMembership("game-shared", UserId("player"))
        .toOption.flatten.nonEmpty)
    } finally owner.close()
  }

  test("coordinated close is idempotent and one reopen reconstructs both stores") {
    val databasePath = path("reopen")
    val first = open(databasePath)
    first.identities.createUser(UserId("owner"), "Owner", 0L)
    first.identities.createGame("game-reopen", UserId("owner"), 1L)
    first.eventStreams.append(
      "game-reopen",
      ExpectedStream.MustNotExist,
      Vector("event-zero")
    )
    first.close()
    first.close()
    assertEquals(first.shutdownCount, 1)

    val reopened = open(databasePath)
    try {
      assertEquals(
        reopened.eventStreams.load("game-reopen")
          .toOption.flatten.get.records,
        Vector("event-zero")
      )
      assertEquals(
        reopened.identities.findMembership("game-reopen", UserId("owner")),
        Right(Some(GameMembership(
          "game-reopen",
          UserId("owner"),
          oathdigital.application.MembershipRole.Owner,
          None
        )))
      )
    } finally reopened.close()
  }

  test("reopen retry is limited to the exact lock-heartbeat failure") {
    val exactChain = Vector(
      "java.sql.SQLException" -> "Database lock acquisition failure",
      "org.hsqldb.persist.LockFile$LockHeldExternallyException" ->
        "lockFile: database.lck method: checkHeartbeat"
    )
    assert(HsqldbDatabaseOwner.isTransientLockHeartbeatChain(exactChain))
    assert(!HsqldbDatabaseOwner.isTransientLockHeartbeatChain(
      exactChain.updated(1, "java.io.IOException" -> exactChain(1)._2)))
    assert(!HsqldbDatabaseOwner.isTransientLockHeartbeatChain(
      exactChain.updated(1, exactChain(1)._1 -> "database is corrupt")))

    var attempts = 0
    var sleeps = 0
    val failure = HsqldbDatabaseOwner.ConnectionFailure(
      new RuntimeException("transient"))
    val result = HsqldbDatabaseOwner.retryTransientLock[Int](
      () => {
        attempts += 1
        Left(failure)
      },
      maxAttempts = 2,
      deadlineNanos = 100L,
      nanoTime = () => 0L,
      sleep = _ => sleeps += 1,
      retryable = _ => true
    )
    assertEquals(result, Left(failure))
    assertEquals(attempts, 2)
    assertEquals(sleeps, 1)

    attempts = 0
    HsqldbDatabaseOwner.retryTransientLock[Int](
      () => {
        attempts += 1
        Left(HsqldbDatabaseOwner.InitializationFailure(
          RepositoryFailure.StorageFailure("schema failure")))
      },
      maxAttempts = 2,
      deadlineNanos = 100L,
      nanoTime = () => 0L,
      sleep = _ => (),
      retryable = _ => true
    )
    assertEquals(attempts, 1)

    attempts = 0
    HsqldbDatabaseOwner.retryTransientLock[Int](
      () => {
        attempts += 1
        Left(failure)
      },
      maxAttempts = 2,
      deadlineNanos = 0L,
      nanoTime = () => 0L,
      sleep = _ => (),
      retryable = _ => true
    )
    assertEquals(attempts, 1)
  }
}
