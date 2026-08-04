package oathdigital.persistence

import java.nio.file.{Files, Path}
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

  test("one owner serves identity and journal adapters concurrently") {
    val owner = HsqldbDatabaseOwner.open(path("concurrent")).toOption.get
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
    val first = HsqldbDatabaseOwner.open(databasePath).toOption.get
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

    val reopened = HsqldbDatabaseOwner.open(databasePath).toOption.get
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
}
