package oathdigital.persistence

import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

import oathdigital.application._
import oathdigital.application.MembershipRole.Player
import oathdigital.testkit.Unwrap.{accepted, present}

class HsqldbDatabaseOwnerSuite extends munit.FunSuite:
  private given executionContext: ExecutionContext =
    ExecutionContext.global

  /** The schema version every database is migrated to. */
  private val targetVersion = new EventJournalSchema(() => 0L).TargetVersion

  private def path(label: String): Path =
    Files.createTempDirectory(s"oathdigital-owner-$label-")
      .resolve("database")

  private def open(databasePath: Path): HsqldbDatabaseOwner =
    HsqldbDatabaseOwner.open(databasePath)
      .fold(error => fail(s"database open failed: $error"), identity)

  test("schema ledger records applied-at times from the injected clock"):
    val databasePath = path("clock")
    HsqldbDatabaseOwner.open(databasePath, () => 1234L)
      .fold(error => fail(s"database open failed: $error"), identity).close()

    val connection = DriverManager.getConnection(
      s"jdbc:hsqldb:file:${databasePath.toAbsolutePath}", "SA", "")
    try
      val rows = connection.createStatement().executeQuery(
        "SELECT version, applied_at_epoch_millis FROM schema_versions " +
          "ORDER BY version")
      val ledger = Iterator.continually(rows).takeWhile(_.next())
        .map(row => row.getInt(1) -> row.getLong(2)).toVector
      // One row per schema version, each stamped by the injected clock.
      assertEquals(ledger,
        (1 to targetVersion).map(_ -> 1234L).toVector)
    finally connection.close()

  test("adapters do not own the connection (not AutoCloseable)"):
    val owner = open(path("adapters"))
    def closeable(adapter: AnyRef) =
      classOf[AutoCloseable].isAssignableFrom(adapter.getClass)
    try
      assert(!closeable(owner.identities))
      assert(!closeable(owner.eventStreams))
    finally owner.close()

  test("one owner serves identity and journal adapters concurrently"):
    val owner = open(path("concurrent"))
    val identity = owner.identities
    val journal = owner.eventStreams
    try
      identity.createUser(UserId("owner"), "Owner", 0L)
      identity.createGame("game-shared", UserId("owner"), 1L)

      val start = new CountDownLatch(1)
      val journalOperation = Future:
          start.await()
          journal.append(
            "game-shared",
            ExpectedStream.MustNotExist,
            Vector("zero", "one")
          )
      val identityOperation = Future:
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
      start.countDown()
      assert(Await.result(journalOperation, 20.seconds).isRight)
      assert(Await.result(identityOperation, 20.seconds).isRight)
      assertEquals(
        present(accepted(journal.load("game-shared")), "stream game-shared")
          .records,
        Vector("zero", "one")
      )
      assert(identity.findMembership("game-shared", UserId("player"))
        .toOption.flatten.nonEmpty)
    finally owner.close()

  test("coordinated close is idempotent and one reopen reconstructs both stores"):
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
    try
      assertEquals(
        present(accepted(reopened.eventStreams.load("game-reopen")),
          "stream game-reopen").records,
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
    finally reopened.close()

  /** What one `retryTransientLock` run did: its result, how many times it
    * attempted the open, and how many times it slept between attempts. */
  private final case class RetryTrace(
      result: Either[HsqldbDatabaseOwner.OpenAttemptFailure, Int],
      attempts: Int, sleeps: Int)

  /** Runs `retryTransientLock` with `attempt`, which receives its 1-based
    * attempt number, and records the attempts and sleeps. The clock reads
    * 0 unless `clock` is given; every failure is retryable unless
    * `retryable` is false. */
  private def retryRun(
      attempt: Int => Either[HsqldbDatabaseOwner.OpenAttemptFailure, Int],
      maxAttempts: Int, deadlineNanos: Long, clock: () => Long = () => 0L,
      retryable: Boolean = true): RetryTrace =
    var attempts = 0
    var sleeps = 0
    val result = HsqldbDatabaseOwner.retryTransientLock[Int](
      () => { attempts += 1; attempt(attempts) },
      maxAttempts = maxAttempts,
      deadlineNanos = deadlineNanos,
      nanoTime = clock,
      sleep = _ => sleeps += 1,
      retryable = _ => retryable)
    RetryTrace(result, attempts, sleeps)

  private val transient = HsqldbDatabaseOwner.ConnectionFailure(
    new RuntimeException("transient"))

  test("only the exact lock-heartbeat exception chain is transient"):
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

  test("reopen retry stops after its last attempt, on a non-lock failure, " +
      "and when the deadline is exhausted"):
    val exhausted = retryRun(_ => Left(transient), maxAttempts = 2,
      deadlineNanos = 100L)
    assertEquals(exhausted.result, Left(transient))
    assertEquals(exhausted.attempts, 2)
    assertEquals(exhausted.sleeps, 1)

    val initializationFailed = retryRun(
      _ => Left(HsqldbDatabaseOwner.InitializationFailure(
        RepositoryFailure.StorageFailure("schema failure"))),
      maxAttempts = 2, deadlineNanos = 100L)
    assertEquals(initializationFailed.attempts, 1)

    val pastDeadline = retryRun(_ => Left(transient), maxAttempts = 2,
      deadlineNanos = 0L)
    assertEquals(pastDeadline.attempts, 1)

  test("reopen retry returns the first success and rechecks the deadline " +
      "after backoff"):
    val recovered = retryRun(
      attempt => if attempt == 1 then Left(transient) else Right(7),
      maxAttempts = 3, deadlineNanos = 100L)
    assertEquals(recovered.result, Right(7))
    assertEquals(recovered.attempts, 2)
    assertEquals(recovered.sleeps, 1)

    // The clock reads 0 before the backoff and 200 after it.
    val clock = Iterator(0L, 200L)
    val expiredDuringBackoff = retryRun(_ => Left(transient), maxAttempts = 3,
      deadlineNanos = 100L, clock = () => clock.next())
    assertEquals(expiredDuringBackoff.result, Left(transient))
    assertEquals(expiredDuringBackoff.attempts, 1)
    assertEquals(expiredDuringBackoff.sleeps, 1)
    assert(!clock.hasNext)

  test("reopen retry does not retry a failure the predicate refuses"):
    val notRetryable = retryRun(_ => Left(transient), maxAttempts = 3,
      deadlineNanos = 100L, retryable = false)
    assertEquals(notRetryable.result, Left(transient))
    assertEquals(notRetryable.attempts, 1)
    assertEquals(notRetryable.sleeps, 0)
