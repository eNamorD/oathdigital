package oathdigital.server

import java.net.URI
import java.net.http.{
  HttpClient,
  HttpRequest,
  HttpResponse => JavaHttpResponse
}

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http

import oathdigital.application.{
  GameApplicationService,
  GameCommand,
  GameProjector,
  InMemoryEventStreamRepository
}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.protocol.{ActorlessCommandCodec, ActorlessCommandRequest,
  GameIntent}

class GameRoutesSuite extends munit.FunSuite {
  test("health load malformed request and stale command status mappings") {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "first-game-route-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher")
    )
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    assert(service.handle("route-game", 0L, GameCommand.Begin(plan)).isRight)
    val gateway = new GameServerGateway(
      service,
      new GameProjector(catalog)
    )
    val binding = Await.result(
      Http().newServerAt("127.0.0.1", 0).bind(
        DevelopmentRoutes.route(gateway, blocking)
      ),
      10.seconds
    )
    val base =
      s"http://127.0.0.1:${binding.localAddress.getPort}"
    val client = HttpClient.newHttpClient()

    try {
      val index = get(client, s"$base/")
      assertEquals(index.statusCode(), 200)
      assertEquals(cacheControl(index),
        Some("no-store, no-cache, must-revalidate, max-age=0"))
      assert(index.body().contains("main.js?dev-cache=no-store-v1"))
      val stylesheet = get(client, s"$base/styles.css")
      assertEquals(stylesheet.statusCode(), 200)
      assertEquals(cacheControl(stylesheet),
        Some("no-store, no-cache, must-revalidate, max-age=0"))

      val health = get(client, s"$base/health")
      assertEquals(health.statusCode(), 404)
      assertEquals(cacheControl(health), None)
      val malformed = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        """{"expectedNextSequence":1,"intent":{"type":"placePawn"}}"""
      )
      assertEquals(malformed.statusCode(), 400)

      val history = get(client,
        s"$base/api/dev/first-games/route-game/events?limit=1")
      assertEquals(history.statusCode(), 200)
      assertEquals(ujson.read(history.body())("events").arr.size, 1)
      assertEquals(ujson.read(history.body())("events")(0)("sequence").num.toLong, 0L)
      assert(ujson.read(history.body())("warning").str.contains("hidden"))
      assertEquals(get(client,
        s"$base/api/dev/first-games/route-game/events?limit=101").statusCode(), 400)

      val stale = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        s"""{"expectedNextSequence":0,"intent":{"type":"placePawn",
           |"siteId":"${sites.head.value}"}}""".stripMargin
      )
      assertEquals(stale.statusCode(), 409)
      assertEquals(
        ujson.read(stale.body())("error").str,
        "stale-client-position"
      )

      val placed = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        ActorlessCommandCodec.encode(ActorlessCommandRequest(
          1L,
          GameIntent.PlacePawn("site:ancient-city")
        ))
      )
      assertEquals(placed.statusCode(), 200)
      assertEquals(ujson.read(placed.body())("phase").str,
        "awaiting-adviser")
      val placedHistory = get(client,
        s"$base/api/dev/first-games/route-game/events?limit=2")
      assertEquals(placedHistory.statusCode(), 200)
      assertEquals(ujson.read(placedHistory.body())("events").arr.size, 2)
      assertEquals(
        ujson.read(placedHistory.body())("events")(1)("sequence").num.toLong,
        1L
      )

      val adviser = plan.denizenOrder(9)
      val chosen = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        s"""{"expectedNextSequence":2,"intent":{"type":"resolveCardDecision",
           |"decisionId":"setup-adviser-0-p2",
           |"resolution":{"kind":"starting-adviser","adviserId":"${adviser.value}"}}}""".stripMargin
      )
      assertEquals(chosen.statusCode(), 200)

      val reloaded = get(
        client,
        s"$base/api/dev/first-games/route-game?playerId=p3"
      )
      assertEquals(reloaded.statusCode(), 200)
      assertEquals(
        ujson.read(reloaded.body())("nextSequence").num.toLong,
        3L
      )
      assertEquals(ujson.read(reloaded.body())("phase").str,
        "awaiting-pawn")
    } finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }

  private def get(
      client: HttpClient,
      url: String
  ): JavaHttpResponse[String] =
    client.send(
      HttpRequest.newBuilder(URI.create(url)).GET().build(),
      JavaHttpResponse.BodyHandlers.ofString()
    )

  private def cacheControl(
      response: JavaHttpResponse[String]
  ): Option[String] = {
    val value = response.headers().firstValue("Cache-Control")
    if (value.isPresent) Some(value.get()) else None
  }

  private def post(
      client: HttpClient,
      url: String,
      body: String
  ): JavaHttpResponse[String] =
    client.send(
      HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build(),
      JavaHttpResponse.BodyHandlers.ofString()
    )
}
