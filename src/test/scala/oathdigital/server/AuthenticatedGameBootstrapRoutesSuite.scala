package oathdigital.server

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}
import java.nio.file.Files

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest => AkkaRequest}

import oathdigital.application._
import oathdigital.application.MembershipRole._
import oathdigital.persistence.HsqldbDatabaseOwner
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState

class AuthenticatedGameBootstrapRoutesSuite extends munit.FunSuite {
  test("owner bootstrap uses exactly the provisioned player memberships") {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "authenticated-bootstrap-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher")
    )
    val database = HsqldbDatabaseOwner.open(
      Files.createTempDirectory("auth-bootstrap-").resolve("database")
    ).toOption.get
    val identities = database.identities
    val owner = UserId("owner")
    val otherOwner = UserId("other-owner")
    val playerUsers = Vector("p1", "p2", "p3").map(id =>
      id -> UserId(s"user-$id"))
    val spectator = UserId("spectator")
    val outsider = UserId("outsider")
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
      new DevelopmentFirstGamePlanFactory(catalog)
    )
    val csrfToken = "c" * 43
    val csrfDigest = CsrfTokenDigest.fromBytes(
      SensitiveTokenDigest.sha256(csrfToken)
    ).toOption.get
    val authenticator = new HttpSessionAuthenticator {
      override def authenticate(request: AkkaRequest) =
        Future.successful(request.headers.find(_.name == "X-Test-User") match {
          case Some(header) => Right(AuthenticatedHttpSession(
            AuthenticatedUser(UserId(header.value)), csrfDigest))
          case None => Left(AuthenticationFailure.MissingCredential)
        })
    }
    val binding = Await.result(
      Http().newServerAt("127.0.0.1", 0).bind(
        new AuthenticatedGameRoutes(
          authenticator,
          new SameOriginCsrfProtection("http://127.0.0.1"),
          gateway,
          blocking
        ).route
      ),
      10.seconds
    )
    val base = s"http://127.0.0.1:${binding.localAddress.getPort}" +
      "/api/authenticated/first-games/bootstrap-game/bootstrap"
    val client = HttpClient.newHttpClient()
    val valid = bootstrapBody(Vector("p2", "p3", "p1"), "p2")

    try {
      assertEquals(post(client, base, None, "not-json").statusCode(), 401)
      Vector(playerUsers.head._2, spectator, outsider, otherOwner).foreach {
        user => assertEquals(
          post(client, base, Some(user.value), valid).statusCode(),
          403
        )
      }

      val invalidSeats = Vector(
        bootstrapBody(Vector("p1", "p2"), "p1"),
        bootstrapBody(Vector("p1", "p2", "p3", "p4"), "p1"),
        bootstrapBody(Vector("p1", "p1", "p3"), "p1"),
        bootstrapBody(Vector("p1", "p2", "unmapped"), "p1")
      )
      invalidSeats.foreach { body =>
        val response = post(client, base, Some(owner.value), body)
        assertEquals(response.statusCode(), 422, response.body())
        assertEquals(
          ujson.read(response.body())("error").str,
          "membership-configuration-mismatch"
        )
      }
      assertEquals(service.load("bootstrap-game"), Right(None))
      assertEquals(events.appendCalls, 0)

      Vector(
        None -> Some("c" * 43),
        Some("http://evil.example") -> Some("c" * 43),
        Some("http://127.0.0.1") -> None,
        Some("http://127.0.0.1") -> Some("w" * 43)
      ).foreach { case (origin, csrf) =>
        val denied = postWithSecurity(
          client, base, Some(owner.value), valid, origin, csrf)
        assertEquals(denied.statusCode(), 403)
        assertEquals(
          ujson.read(denied.body())("error").str,
          "csrf-validation-failed"
        )
      }
      assertEquals(events.appendCalls, 0)

      val hiddenInput = ujson.read(valid).obj
      hiddenInput("worldDeckOrder") = ujson.Arr("card:injected")
      assertEquals(
        post(client, base, Some(owner.value), ujson.write(hiddenInput))
          .statusCode(),
        400
      )

      val created = post(client, base, Some(owner.value), valid)
      assertEquals(created.statusCode(), 200, created.body())
      assert(ujson.read(created.body())("pendingCardDecision").isNull)
      assertNoHiddenPlan(created.body())
      assertEquals(
        ujson.read(created.body())("players").arr.map(_("playerId").str)
          .toVector,
        Vector("p2", "p3", "p1")
      )
      val firstHistory = events.load("bootstrap-game").toOption.flatten.get
      val replayed = service.load("bootstrap-game").toOption.flatten.get
      // GameStarted plus the WalkerParked fact from Setup's immediate first
      // park (2026-09-21 Chronicle design, slice 2).
      assertEquals(replayed.nextSequence, 2L)
      val ready = replayed.state.asInstanceOf[OathState.Ready].value
      assertEquals(
        ready.game.current.players.map(_.player.value),
        Vector("p2", "p3", "p1")
      )
      assertEquals(ready.setup.firstPlayer.value, "p2")

      val duplicate = post(client, base, Some(owner.value), valid)
      assertEquals(duplicate.statusCode(), 409, duplicate.body())
      assertEquals(events.appendCalls, 1)
      assertEquals(
        events.load("bootstrap-game").toOption.flatten.get,
        firstHistory
      )

      database.close()
      val storage = post(client, base, Some(owner.value), valid)
      assertEquals(storage.statusCode(), 500)
      assertEquals(ujson.read(storage.body())("error").str, "internal-error")
      assert(!storage.body().toLowerCase.contains("closed"))
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
      database.close()
    }
  }

  private def bootstrapBody(
      playerIds: Vector[String],
      firstPlayer: String
  ): String = ujson.write(ujson.Obj(
    "expectedNextSequence" -> 0,
    "participants" -> ujson.Arr.from(playerIds.zipWithIndex.map {
      case (playerId, index) => ujson.Obj(
        "playerId" -> playerId,
        "lineageId" -> s"lineage-${index + 1}",
        "color" -> Vector("red", "blue", "yellow", "green")(index % 4)
      )
    }),
    "firstPlayer" -> firstPlayer
  ))

  private def assertNoHiddenPlan(body: String): Unit = {
    Vector(
      "worldDeckOrder",
      "relicOrder",
      "denizenOrder",
      "orderedSites",
      "userId",
      "actor"
    ).foreach(field => assert(!body.contains(field)))
  }

  private def post(
      client: HttpClient,
      url: String,
      user: Option[String],
      body: String
  ) = postWithSecurity(
    client,
    url,
    user,
    body,
    Some("http://127.0.0.1"),
    Some("c" * 43)
  )

  private def postWithSecurity(
      client: HttpClient,
      url: String,
      user: Option[String],
      body: String,
      origin: Option[String],
      csrf: Option[String]
  ) = {
    val builder = HttpRequest.newBuilder(URI.create(url))
      .header("Content-Type", "application/json")
    user.foreach(value => builder.header("X-Test-User", value))
    origin.foreach(value => builder.header("Origin", value))
    csrf.foreach(value => builder.header("X-CSRF-Token", value))
    client.send(
      builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
      JavaResponse.BodyHandlers.ofString()
    )
  }

  private final class CountingEventStreamRepository
      extends EventStreamRepository {
    private val delegate = new InMemoryEventStreamRepository
    var appendCalls: Int = 0

    override def load(gameId: String) = delegate.load(gameId)
    override def append(
        gameId: String,
        expected: ExpectedStream,
        records: Vector[String]
    ) = {
      appendCalls += 1
      delegate.append(gameId, expected, records)
    }
  }
}
