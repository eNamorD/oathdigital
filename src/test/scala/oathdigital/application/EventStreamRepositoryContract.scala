package oathdigital.application

import java.util.concurrent.CountDownLatch

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

import oathdigital.testkit.Unwrap.*

/** What every `EventStreamRepository` promises (the trait's doc): one stream
  * per game, appended atomically, with the expected position compared in
  * the same step. The in-memory repository every service test uses and the
  * Hsqldb store both run it, so the fake cannot drift from the real one. */
abstract class EventStreamRepositoryContract[R <: EventStreamRepository]
    extends munit.FunSuite:
  /** A new, empty repository. */
  protected def make(): R
  /** Releases what `make` opened. */
  protected def release(repository: R): Unit = ()

  private given ExecutionContext = ExecutionContext.global

  private val fresh = FunFixture[R](_ => make(), release)

  private def stream(repository: R, gameId: String): StoredEventStream =
    present(accepted(repository.load(gameId)), s"a stream for $gameId")

  fresh.test("an unknown game loads no stream"): repository =>
    assertEquals(repository.load("nobody"), Right(None))

  fresh.test("creates a stream and loads its records in order"): repository =>
    assertEquals(
      repository.append(
        "game", ExpectedStream.MustNotExist, Vector("first", "second")),
      Right(RepositoryAppendResult.Appended(0L, 2)))
    val loaded = stream(repository, "game")
    assertEquals(loaded.gameId, "game")
    assertEquals(loaded.records, Vector("first", "second"))
    assertEquals(loaded.nextSequence, 2L)

  fresh.test(
    "creating a stream that exists, or appending to one that does not, is refused"):
    repository =>
      repository.append("game", ExpectedStream.MustNotExist, Vector("zero"))
      assertEquals(
        repository.append(
          "game", ExpectedStream.MustNotExist, Vector("again")),
        Right(RepositoryAppendResult.StreamAlreadyExists))
      assertEquals(
        repository.append(
          "missing", ExpectedStream.AtNextSequence(0L), Vector("one")),
        Right(RepositoryAppendResult.StreamNotFound))
      assertEquals(stream(repository, "game").records, Vector("zero"))

  fresh.test("appends a batch at the next sequence, in order"): repository =>
    repository.append("game", ExpectedStream.MustNotExist, Vector("zero"))
    assertEquals(
      repository.append(
        "game", ExpectedStream.AtNextSequence(1L),
        Vector("one", "two", "three")),
      Right(RepositoryAppendResult.Appended(1L, 3)))
    assertEquals(stream(repository, "game").records,
      Vector("zero", "one", "two", "three"))

  fresh.test("a sequence conflict writes no part of the batch"): repository =>
    repository.append("game", ExpectedStream.MustNotExist, Vector("zero"))
    assertEquals(
      repository.append(
        "game", ExpectedStream.AtNextSequence(0L),
        Vector("stale-one", "stale-two")),
      Right(RepositoryAppendResult.SequenceConflict(0L, 1L)))
    assertEquals(stream(repository, "game").records, Vector("zero"))

  fresh.test("two racing creations have one winner and no partial stream"):
    repository =>
      val start = new CountDownLatch(1)
      val attempts = Vector("left", "right").map { record =>
        Future:
          start.await()
          repository.append(
            "game", ExpectedStream.MustNotExist, Vector(record))
      }
      start.countDown()
      val results = Await.result(Future.sequence(attempts), 20.seconds)
      assertEquals(results.count(_.exists(
        _.isInstanceOf[RepositoryAppendResult.Appended])), 1)
      assertEquals(results.count(_ ==
        Right(RepositoryAppendResult.StreamAlreadyExists)), 1)
      assertEquals(stream(repository, "game").records.size, 1)

  fresh.test(
    "two racing appends at one position have one winner and one conflict"):
    repository =>
      repository.append("game", ExpectedStream.MustNotExist, Vector("zero"))
      val start = new CountDownLatch(1)
      val attempts = Vector("left", "right").map { record =>
        Future:
          start.await()
          repository.append(
            "game", ExpectedStream.AtNextSequence(1L), Vector(record))
      }
      start.countDown()
      val results = Await.result(Future.sequence(attempts), 20.seconds)
      assertEquals(results.count(_.exists(
        _.isInstanceOf[RepositoryAppendResult.Appended])), 1)
      assertEquals(results.count(_ ==
        Right(RepositoryAppendResult.SequenceConflict(1L, 2L))), 1)
      assertEquals(stream(repository, "game").records.size, 2)
