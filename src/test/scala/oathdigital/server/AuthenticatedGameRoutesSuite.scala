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
import oathdigital.setup.FirstGameSetupFixture._

class AuthenticatedGameRoutesSuite extends munit.FunSuite {
  test("authenticated routes derive projection scope and command actor") {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "authenticated-route-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher")
    )
    val database = HsqldbDatabaseOwner.open(
      Files.createTempDirectory("auth-route-").resolve("database")
    ).toOption.get
    val identities = database.identities
    val owner = UserId("owner")
    val p2User = UserId("user-p2")
    val p3User = UserId("user-p3")
    val spectator = UserId("spectator")
    val outsider = UserId("outsider")
    Vector(owner, p2User, p3User, spectator, outsider)
      .foreach(user => identities.createUser(user, user.value, 0L))
    identities.createGame("auth-game", owner, 0L)
    identities.addMembership(
      GameMembership("auth-game", p2User, Player, Some("p2")), 0L)
    identities.addMembership(
      GameMembership("auth-game", p3User, Player, Some("p3")), 0L)
    identities.addMembership(
      GameMembership("auth-game", spectator, Spectator, None), 0L)

    val events = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, events)
    service.handle("auth-game", 0L, GameCommand.Begin(plan))
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
    val gateway = new AuthenticatedGameGateway(
      service,
      new GameProjector(catalog),
      new MembershipAuthorizationService(identities),
      identities,
      new DevelopmentFirstGamePlanFactory(catalog)
    )
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
      "/api/authenticated/first-games/auth-game"
    val client = HttpClient.newHttpClient()

    try {
      assertEquals(get(client, base, None).statusCode(), 401)
      assertEquals(get(client, base, Some(outsider.value)).statusCode(), 403)
      assertEquals(get(client, base + "?playerId=p2", Some(p2User.value))
        .statusCode(), 400)

      val pawn = post(client, base + "/commands", p2User.value,
        intentBody(1L, "placePawn", "siteId", sites.head.value))
      assertEquals(pawn.statusCode(), 200, pawn.body())
      assert(ujson.read(pawn.body())("privateAdviserChoices").arr.nonEmpty)
      assertNoHiddenPlan(pawn.body())

      Vector(owner, spectator, p3User).foreach { user =>
        val projection = get(client, base, Some(user.value))
        assertEquals(projection.statusCode(), 200)
        assertEquals(
          ujson.read(projection.body())("privateAdviserChoices").arr.size,
          0
        )
        assertNoHiddenPlan(projection.body())
      }

      val impersonation = post(client, base + "/commands", p2User.value,
        ujson.write(ujson.Obj(
          "expectedNextSequence" -> 2,
          "intent" -> ujson.Obj(
            "type" -> "chooseAdviser",
            "playerId" -> "p3",
            "adviserId" -> plan.denizenOrder(9).value
          )
        )))
      assertEquals(impersonation.statusCode(), 400)
      Vector(owner, spectator).foreach { user =>
        assertEquals(post(client, base + "/commands", user.value,
          intentBody(2L, "placePawn", "siteId", "S1")
        ).statusCode(), 403)
      }

      val stale = post(client, base + "/commands", p2User.value,
        intentBody(1L, "chooseAdviser", "adviserId",
          plan.denizenOrder(9).value))
      assertEquals(stale.statusCode(), 409)
      val accepted = post(client, base + "/commands", p2User.value,
        intentBody(2L, "chooseAdviser", "adviserId",
          plan.denizenOrder(9).value))
      assertEquals(accepted.statusCode(), 200)

      database.close()
      val storage = get(client, base, Some(owner.value))
      assertEquals(storage.statusCode(), 500)
      assertEquals(
        ujson.read(storage.body())("error").str,
        "internal-error"
      )
      assert(!storage.body().toLowerCase.contains("closed"))
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
      database.close()
    }
  }

  private def assertNoHiddenPlan(body: String): Unit = {
    assert(!body.contains("worldDeckOrder"))
    assert(!body.contains("relicOrder"))
    assert(!body.contains("denizenOrder"))
  }

  private def intentBody(
      expected: Long,
      intentType: String,
      field: String,
      value: String
  ): String = ujson.write(ujson.Obj(
    "expectedNextSequence" -> ujson.Num(expected.toDouble),
    "intent" -> ujson.Obj("type" -> intentType, field -> value)
  ))

  private def get(client: HttpClient, url: String, user: Option[String]) = {
    val builder = HttpRequest.newBuilder(URI.create(url)).GET()
    user.foreach(value => builder.header("X-Test-User", value))
    client.send(builder.build(), JavaResponse.BodyHandlers.ofString())
  }

  private def post(client: HttpClient, url: String, user: String, body: String) =
    client.send(HttpRequest.newBuilder(URI.create(url))
      .header("X-Test-User", user)
      .header("Origin", "http://127.0.0.1")
      .header("X-CSRF-Token", "c" * 43)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
      JavaResponse.BodyHandlers.ofString())
}
