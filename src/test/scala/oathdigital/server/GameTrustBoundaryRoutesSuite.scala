package oathdigital.server

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http

import oathdigital.application._
import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.CatalogRef

class GameTrustBoundaryRoutesSuite extends munit.FunSuite {
  private val catalog = ExecutableCatalog(
    "test",
    CatalogRef("test", "1"),
    Vector.empty,
    Vector.empty,
    Vector.empty,
    Vector.empty,
    Vector.empty
  )

  test("route validates identity actor and internal-error boundaries") {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "trust-boundary-route-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher")
    )
    val secret = "internal-database-password-and-path"
    val repository = new EventStreamRepository {
      override def load(gameId: String) =
        Left(RepositoryFailure.StorageFailure(secret))
      override def append(
          gameId: String,
          expected: ExpectedStream,
          records: Vector[String]
      ) = Left(RepositoryFailure.StorageFailure(secret))
    }
    val gateway = new GameServerGateway(
      new GameApplicationService(catalog, repository),
      new GameProjector(catalog),
      new DevelopmentFirstGamePlanFactory(catalog)
    )
    val binding = Await.result(
      Http().newServerAt("127.0.0.1", 0).bind(
        DevelopmentRoutes.route(gateway, blocking, serveFrontend = false)
      ),
      10.seconds
    )
    val base = s"http://127.0.0.1:${binding.localAddress.getPort}"
    val client = HttpClient.newHttpClient()

    try {
      Vector(
        s"$base/api/dev/first-games/%20?playerId=p1",
        s"$base/api/dev/first-games/game?playerId=bad!",
        s"$base/api/dev/first-games/game?playerId=${"x" * 129}",
        s"$base/api/dev/first-games/game?playerId="
      ).foreach(url => assertEquals(get(client, url).statusCode(), 400))

      val begin = post(
        client,
        s"$base/api/dev/first-games/game/commands?playerId=p1",
        """{"expectedNextSequence":0,"command":{"type":"begin",
          |"plan":{"relicOrder":["hidden"]}}}""".stripMargin
      )
      assertEquals(begin.statusCode(), 400)
      assert(ujson.read(begin.body())("message").str.contains("bootstrap"))

      val mismatch = post(
        client,
        s"$base/api/dev/first-games/game/commands?playerId=p1",
        """{"expectedNextSequence":0,"command":{"type":"placePawn",
          |"playerId":"p2","siteId":"S1"}}""".stripMargin
      )
      assertEquals(mismatch.statusCode(), 400)
      assertEquals(
        ujson.read(mismatch.body())("error").str,
        "actor-selector-mismatch"
      )
      val travelMismatch = post(
        client,
        s"$base/api/dev/first-games/game/commands?playerId=p1",
        """{"expectedNextSequence":0,"command":{"type":"travel",
          |"playerId":"p2","destinationSiteId":"S2"}}""".stripMargin
      )
      assertEquals(travelMismatch.statusCode(), 400)
      assertEquals(ujson.read(travelMismatch.body())("error").str,
        "actor-selector-mismatch")

      val internal = get(
        client,
        s"$base/api/dev/first-games/game?playerId=p1"
      )
      assertEquals(internal.statusCode(), 500)
      assertEquals(ujson.read(internal.body())("error").str, "internal-error")
      assert(!internal.body().contains(secret))
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }

  private def get(client: HttpClient, url: String): JavaResponse[String] =
    client.send(
      HttpRequest.newBuilder(URI.create(url)).GET().build(),
      JavaResponse.BodyHandlers.ofString()
    )

  private def post(
      client: HttpClient,
      url: String,
      body: String
  ): JavaResponse[String] =
    client.send(
      HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
      JavaResponse.BodyHandlers.ofString()
    )
}
