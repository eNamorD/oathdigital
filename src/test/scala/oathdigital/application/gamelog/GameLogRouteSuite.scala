package oathdigital.application.gamelog

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.PlayerId
import oathdigital.protocol.projection.LogPageCodec
import oathdigital.server.{DevelopmentRoutes, GameServerGateway, TrustedGameGateway,
  TrustedSeatFailure}

class GameLogRouteSuite extends munit.FunSuite:
  test("the projector pages the log after a sequence, and refuses past the end"):
    val script = LogScripts.round
    val history = script.history
    val projector = new GameProjector(catalog)
    val whole = projector.logPage("round", history, 0L, script.actor).get
    assertEquals(whole.nextSequence, history.nextSequence)
    assert(whole.entries.nonEmpty)
    val after = whole.entries(whole.entries.size / 2).sequence
    val tail = projector.logPage("round", history, after, script.actor).get
    assertEquals(tail.entries, whole.entries.filter(_.sequence >= after))
    assertEquals(tail.after, after)
    assertEquals(projector.logPage("round", history, history.nextSequence,
      script.actor).map(_.entries), Some(Vector.empty))
    assertEquals(projector.logPage("round", history, history.nextSequence + 1,
      script.actor), None)

  test("the trusted gateway pages for the seat and refuses another game"):
    val script = LogScripts.woken
    val gateway = new TrustedGameGateway(script.service,
      new GameProjector(catalog))
    val seat = TrustedSeat(script.name, script.actor.value)
    assert(gateway.log(script.name, seat, 0L).isRight)
    assertEquals(gateway.log("other", seat, 0L),
      Left(TrustedSeatFailure.Forbidden))
    assertEquals(gateway.log(script.name, seat, 10_000L),
      Left(TrustedSeatFailure.InvalidIntent))

  test("the development route binds playerId, checks the cursor, and leaves raw events alone"):
    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "game-log-route-test")
    val blocking = system.dispatchers.lookup(
      DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val script = LogScripts.woken
    val gateway = new GameServerGateway(script.service,
      new GameProjector(catalog))
    val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(
      DevelopmentRoutes.route(gateway, blocking)), 10.seconds)
    val base = s"http://127.0.0.1:${binding.localAddress.getPort}"
    val client = HttpClient.newHttpClient()
    def get(path: String) = client.send(HttpRequest.newBuilder(
      URI.create(base + path)).GET().build(), JavaResponse.BodyHandlers.ofString())
    try
      val page = get(s"/api/dev/first-games/woken/log/0?playerId=${script.actor.value}")
      assertEquals(page.statusCode(), 200, page.body())
      val decoded = LogPageCodec.decode(page.body()).toOption.get
      assertEquals(decoded.gameId, "woken")
      assertEquals(decoded.entries.head.spans.head.text, "Setup")
      assertEquals(get(s"/api/dev/first-games/woken/log/-1?playerId=p1")
        .statusCode(), 400)
      assertEquals(get(s"/api/dev/first-games/woken/log/99999?playerId=p1")
        .statusCode(), 400)
      assertEquals(get(s"/api/dev/first-games/woken/log/x?playerId=p1")
        .statusCode(), 400)
      val events = get("/api/dev/first-games/woken/events?limit=5")
      assertEquals(events.statusCode(), 200)
      assert(events.body().contains("\"warning\""))
    finally
      Await.result(binding.terminate(5.seconds), 10.seconds)
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
