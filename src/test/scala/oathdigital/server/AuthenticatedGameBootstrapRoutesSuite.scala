package oathdigital.server

import java.nio.file.Files

import oathdigital.application._
import oathdigital.application.MembershipRole._
import oathdigital.persistence.HsqldbDatabaseOwner
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.testkit.{HttpTestServer, Outcomes,
  TestUserHeaderAuthenticator}
import oathdigital.testkit.ChronicleShuffles.unshuffled
import oathdigital.testkit.Unwrap.{accepted, present}

class AuthenticatedGameBootstrapRoutesSuite extends munit.FunSuite:
  private val owner = UserId("owner")
  private val otherOwner = UserId("other-owner")
  private val playerUsers = Vector("p1", "p2", "p3").map(id =>
    id -> UserId(s"user-$id"))
  private val spectator = UserId("spectator")
  private val outsider = UserId("outsider")

  private val bootstrap =
    "/api/authenticated/first-games/bootstrap-game/bootstrap"

  /** A bootstrap request seating p2, p3 and p1, the provisioned seats, with
    * p2 first. */
  private val seatingBody = bootstrapBody(Vector("p2", "p3", "p1"), "p2")

  /** "bootstrap-game" is provisioned but not yet begun: `owner` owns it,
    * user-p1 to user-p3 hold seats p1 to p3, and `spectator` watches.
    * `otherOwner` owns "other-game"; `outsider` belongs to neither. The
    * journal counts its appends. */
  private final case class ProvisionedGame(server: HttpTestServer,
      database: HsqldbDatabaseOwner, service: GameApplicationService,
      events: CountingEventStreamRepository)

  /** `body`'s result; closes `database` and rethrows if `body` fails, since
    * munit runs a fixture's teardown only after its setup succeeds. */
  private def closingOnFailure[A](database: HsqldbDatabaseOwner)(
      body: => A): A =
    try body
    catch
      case error: Throwable =>
        database.close()
        throw error

  private val provisioned = FunFixture[ProvisionedGame](
    setup = _ =>
      val database = accepted(HsqldbDatabaseOwner.open(
        Files.createTempDirectory("auth-bootstrap-").resolve("database")))
      closingOnFailure(database):
        val identities = database.identities
        (Vector(owner, otherOwner, spectator, outsider) ++
          playerUsers.map(_._2)).foreach(user =>
          identities.createUser(user, user.value, 0L))
        identities.createGame("bootstrap-game", owner, 0L)
        identities.createGame("other-game", otherOwner, 0L)
        playerUsers.foreach { case (playerId, userId) =>
          identities.addMembership(GameMembership(
            "bootstrap-game", userId, Player, Some(playerId)), 0L)
        }
        identities.addMembership(GameMembership(
          "bootstrap-game", spectator, Spectator, None), 0L)

        val events = new CountingEventStreamRepository
        val service = new GameApplicationService(catalog, events)
        val gateway = new AuthenticatedGameGateway(
          service,
          new GameProjector(catalog),
          new MembershipAuthorizationService(identities),
          identities,
          // The plan must keep the exact participant order and first player
          // it was sent, so seating must not shuffle.
          new GeneratedFirstGamePlanFactory(catalog, unshuffled)
        )
        val server = HttpTestServer.start("authenticated-bootstrap-test")(
          blocking => new AuthenticatedGameRoutes(
            TestUserHeaderAuthenticator,
            new SameOriginCsrfProtection(HttpTestServer.origin),
            gateway,
            blocking
          ).route)
        ProvisionedGame(server, database, service, events),
    teardown = game =>
      game.server.close()
      game.database.close())

  provisioned.test("an unauthenticated bootstrap and a non-owner's are " +
      "refused"):
    case ProvisionedGame(server, _, _, _) =>
      assertEquals(server.post(bootstrap, "not-json").statusCode(), 401)
      Vector(playerUsers.head._2, spectator, outsider, otherOwner).foreach:
        user => assertEquals(
          server.post(bootstrap, seatingBody, Some(user.value)).statusCode(),
          403
        )

  provisioned.test("a seat set that differs from the provisioned seats is " +
      "refused and writes nothing"):
    case ProvisionedGame(server, _, service, events) =>
      val invalidSeats = Vector(
        bootstrapBody(Vector("p1", "p2"), "p1"),
        bootstrapBody(Vector("p1", "p2", "p3", "p4"), "p1"),
        bootstrapBody(Vector("p1", "p1", "p3"), "p1"),
        bootstrapBody(Vector("p1", "p2", "unmapped"), "p1")
      )
      invalidSeats.foreach { body =>
        val response = server.post(bootstrap, body, Some(owner.value))
        assertEquals(response.statusCode(), 422, response.body())
        assertEquals(
          ujson.read(response.body())("error").str,
          "membership-configuration-mismatch"
        )
      }
      assertEquals(service.load("bootstrap-game"), Right(None))
      assertEquals(events.appendCalls, 0)

  provisioned.test("a bootstrap without the same origin and its CSRF token " +
      "is refused and writes nothing"):
    case ProvisionedGame(server, _, _, events) =>
      val csrfToken = HttpTestServer.csrfToken
      Vector(
        None -> Some(csrfToken),
        Some("http://evil.example") -> Some(csrfToken),
        Some(HttpTestServer.origin) -> None,
        // A well-formed token whose digest is not the session's.
        Some(HttpTestServer.origin) -> Some("w" * csrfToken.length)
      ).foreach { case (origin, csrf) =>
        val denied = server.post(bootstrap, seatingBody, Some(owner.value),
          origin, csrf)
        assertEquals(denied.statusCode(), 403)
        assertEquals(
          ujson.read(denied.body())("error").str,
          "csrf-validation-failed"
        )
      }
      assertEquals(events.appendCalls, 0)

  provisioned.test("a bootstrap carrying a hidden plan field is refused"):
    case ProvisionedGame(server, _, _, _) =>
      val hiddenInput = ujson.read(seatingBody).obj
      hiddenInput("worldDeckOrder") = ujson.Arr("card:injected")
      assertEquals(
        server.post(bootstrap, ujson.write(hiddenInput), Some(owner.value))
          .statusCode(),
        400
      )

  provisioned.test("the owner's bootstrap begins the game in the sent seat " +
      "order and replays it"):
    case ProvisionedGame(server, _, service, _) =>
      val created = server.post(bootstrap, seatingBody, Some(owner.value))
      assertEquals(created.statusCode(), 200, created.body())
      assertNoHiddenPlan(created.body())
      assertEquals(
        ujson.read(created.body())("players").arr.map(_("playerId").str)
          .toVector,
        Vector("p2", "p3", "p1")
      )
      val replayed = present(accepted(service.load("bootstrap-game")),
        "the bootstrapped game")
      // GameStarted plus the WalkerParked fact from Setup's immediate first
      // park (2026-09-21 Chronicle design, slice 2).
      assertEquals(replayed.nextSequence, 2L)
      val ready = Outcomes.ready(replayed.state)
      assertEquals(
        ready.game.current.players.map(_.player.value),
        Vector("p2", "p3", "p1")
      )
      assertEquals(ready.setup.firstPlayer.value, "p2")

  provisioned.test("a second bootstrap conflicts and leaves the journal as " +
      "the first wrote it"):
    case ProvisionedGame(server, _, _, events) =>
      assertEquals(
        server.post(bootstrap, seatingBody, Some(owner.value)).statusCode(),
        200)
      val firstHistory = present(accepted(events.load("bootstrap-game")),
        "the bootstrapped journal")
      val duplicate = server.post(bootstrap, seatingBody, Some(owner.value))
      assertEquals(duplicate.statusCode(), 409, duplicate.body())
      assertEquals(events.appendCalls, 1)
      assertEquals(
        present(accepted(events.load("bootstrap-game")),
          "the bootstrapped journal"),
        firstHistory
      )

  provisioned.test("a storage failure answers 500 without its details"):
    case ProvisionedGame(server, database, _, _) =>
      database.close()
      val storage = server.post(bootstrap, seatingBody, Some(owner.value))
      assertEquals(storage.statusCode(), 500)
      assertEquals(ujson.read(storage.body())("error").str, "internal-error")
      assert(!storage.body().toLowerCase.contains("closed"))

  private def bootstrapBody(
      playerIds: Vector[String],
      firstPlayer: String
  ): String = ujson.write(ujson.Obj(
    "expectedNextSequence" -> 0,
    "participants" -> ujson.Arr.from(playerIds.zipWithIndex.map {
      case (playerId, index) => ujson.Obj(
        "playerId" -> playerId,
        "color" -> oathdigital.model.PlayerColor.all(index).key
      )
    }),
    "firstPlayer" -> firstPlayer
  ))

  private def assertNoHiddenPlan(body: String): Unit =
    Vector(
      "worldDeckOrder",
      "relicOrder",
      "denizenOrder",
      "orderedSites",
      "userId",
      "actor"
    ).foreach(field => assert(!body.contains(field)))

  /** Spy of EventStreamRepository: delegates to an
    * InMemoryEventStreamRepository, which is a fake, and counts the appends
    * in `appendCalls`. */
  private final class CountingEventStreamRepository
      extends EventStreamRepository:
    private val delegate = new InMemoryEventStreamRepository
    var appendCalls: Int = 0

    override def load(gameId: String) = delegate.load(gameId)
    override def append(
        gameId: String,
        expected: ExpectedStream,
        records: Vector[String]
    ) =
      appendCalls += 1
      delegate.append(gameId, expected, records)
