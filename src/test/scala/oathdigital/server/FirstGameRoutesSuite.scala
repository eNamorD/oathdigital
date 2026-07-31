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
  FirstGameApplicationService,
  FirstGameProjector,
  InMemoryEventStreamRepository
}
import oathdigital.serialization.FirstGameEventWire
import oathdigital.setup.FirstGameSetupEvent.FirstGameStarted
import oathdigital.setup.FirstGameSetupFixture._

class FirstGameRoutesSuite extends munit.FunSuite {
  test("health load malformed request and stale command status mappings") {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "first-game-route-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher")
    )
    val repository = new InMemoryEventStreamRepository
    val service = new FirstGameApplicationService(catalog, repository)
    val gateway = new FirstGameServerGateway(
      service,
      new FirstGameProjector(catalog)
    )
    val binding = Await.result(
      Http().newServerAt("127.0.0.1", 0).bind(
        DevelopmentRoutes.route(gateway, blocking, serveFrontend = false)
      ),
      10.seconds
    )
    val base =
      s"http://127.0.0.1:${binding.localAddress.getPort}"
    val client = HttpClient.newHttpClient()

    try {
      assertEquals(get(client, s"$base/health").statusCode(), 200)
      val malformed = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        """{"expectedNextSequence":1,"command":{"type":"placePawn"}}"""
      )
      assertEquals(malformed.statusCode(), 400)

      val started = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        beginBody()
      )
      assertEquals(started.statusCode(), 200)
      assertEquals(ujson.read(started.body())("nextSequence").num.toLong, 1L)

      val stale = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        s"""{"expectedNextSequence":0,"command":{"type":"placePawn",
           |"playerId":"p2","siteId":"${sites.head.value}"}}""".stripMargin
      )
      assertEquals(stale.statusCode(), 409)
      assertEquals(
        ujson.read(stale.body())("error").str,
        "stale-client-position"
      )

      val placed = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        s"""{"expectedNextSequence":1,"command":{"type":"placePawn",
           |"playerId":"p2","siteId":"${sites.head.value}"}}""".stripMargin
      )
      assertEquals(placed.statusCode(), 200)
      assertEquals(ujson.read(placed.body())("phase").str,
        "awaiting-adviser")

      val adviser = plan.denizenOrder(9)
      val chosen = post(
        client,
        s"$base/api/dev/first-games/route-game/commands?playerId=p2",
        s"""{"expectedNextSequence":2,"command":{"type":"chooseAdviser",
           |"playerId":"p2","adviserId":"${adviser.value}"}}""".stripMargin
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

  private def beginBody(): String = {
    val event = FirstGameEventWire.encodeEvent(
      "route-game",
      catalogRef,
      0L,
      FirstGameStarted(plan)
    ).toOption.get
    ujson.write(ujson.Obj(
      "expectedNextSequence" -> 0,
      "command" -> ujson.Obj(
        "type" -> "begin",
        "plan" -> event("payload")
      )
    ))
  }

  private def get(
      client: HttpClient,
      url: String
  ): JavaHttpResponse[String] =
    client.send(
      HttpRequest.newBuilder(URI.create(url)).GET().build(),
      JavaHttpResponse.BodyHandlers.ofString()
    )

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
