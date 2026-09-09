package oathdigital.server

import java.net.{CookieManager, CookiePolicy, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}
import java.nio.file.{Files, Paths}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.protocol._

class TrustedSeatRoutesSuite extends munit.FunSuite {
  test("trusted gateway projects only the seat and binds command identity") {
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    assert(service.handle("trusted", 0L, GameCommand.Begin(plan)).isRight)
    val gateway = new TrustedGameGateway(service, new GameProjector(catalog))
    val seat = TrustedSeat("trusted", "p2")
    val command = GameHttpWire.decodeCommand(s"""{"expectedNextSequence":1,"intent":{"type":"placePawn","siteId":"${sites.head.value}"}}""").toOption.get
    val accepted = gateway.submit("trusted", seat, command).toOption.get
    assert(accepted.pendingCardDecision.nonEmpty)
    assert(gateway.load("trusted", seat).toOption.get.pendingCardDecision.nonEmpty)
    assert(gateway.load("trusted", TrustedSeat("trusted", "p3")).toOption.get.pendingCardDecision.isEmpty)
    assertEquals(gateway.load("other", seat), Left(TrustedSeatFailure.Forbidden))
    assertEquals(gateway.submit("other", seat, command), Left(TrustedSeatFailure.Forbidden))
    assertEquals(gateway.submit("trusted", seat, command),
      Left(TrustedSeatFailure.Application(GameApplicationError.StaleClientPosition(1L, 2L))))
    assertEquals(service.load("trusted").toOption.flatten.get.nextSequence, 2L)
    assert(GameHttpWire.decodeCommand("""{"expectedNextSequence":2,"intent":{"type":"placePawn","playerId":"p3","siteId":"S1"}}""").isLeft)
  }

  test("trusted preview binds resolved actor and rejects a different game") {
    val repository = new InMemoryEventStreamRepository
    val (state, events) = execute(new oathdigital.gameplay.setup.FirstGameSetupRules(catalog))
    val oathdigital.gameplay.OathState.Ready(ready) = state: @unchecked
    val actor = ready.game.current.turn.activePlayer
    val act = new oathdigital.gameplay.OathRules(catalog).handle(state,
      oathdigital.gameplay.phases.WakeCommand.EndWake(actor)).toOption.get
    repository.seed("preview", (events ++ act.events).zipWithIndex.map { case (event, index) =>
      ujson.write(oathdigital.serialization.GameEventWire.encodeEvent("preview", catalog.ref,
        index.toLong, event).toOption.get)
    })
    val service = new GameApplicationService(catalog, repository)
    val gateway = new TrustedGameGateway(service, new GameProjector(catalog))
    val sequence = (events ++ act.events).size.toLong
    val request = MajorActionPreviewRequest(sequence, "travel")
    assert(gateway.preview("preview", TrustedSeat("preview", actor.value), request).isRight)
    val other = ready.game.current.players.find(_.player != actor).get.player.value
    assert(gateway.preview("preview", TrustedSeat("preview", other), request).isLeft)
    assertEquals(gateway.preview("other", TrustedSeat("preview", actor.value), request),
      Left(TrustedSeatFailure.Forbidden))
    assertEquals(service.load("preview").toOption.flatten.get.nextSequence, sequence)
  }

  test("creation returns ordered public links; exchange scopes an HttpOnly cookie and reloads private API") {
    withServer() { (base, _) =>
      val client = HttpClient.newHttpClient()
      val created = create(client, base, "game-a")
      assertEquals(created.gameId, "game-a")
      assertEquals(created.seats.map(_.playerId), Vector("p1", "p2"))
      assert(created.seats.forall(_.url.startsWith("http://127.0.0.1:8080/s/")))
      val code = URI.create(created.seats(1).url).getPath.stripPrefix("/s/")
      val entry = send(client, base, s"/s/$code")
      assertEquals(entry.statusCode(), 303, entry.body())
      assertEquals(entry.headers().firstValue("Location").orElse(""), "/games/game-a")
      assert(!entry.body().contains(code))
      assert(!entry.headers().firstValue("Location").orElse("").contains(code))
      val cookie = entry.headers().firstValue("Set-Cookie").orElse("")
      assertCookie(cookie, code, "/games/game-a", secure = false)
      val canonical = send(client, base, "/games/game-a", cookie = Some(s"oath_seat=$code"))
      assertEquals(canonical.statusCode(), 200, canonical.body())
      assert(canonical.body().contains("/assets/main.js"))
      assert(!canonical.body().contains(code))
      val api = "/games/game-a/api"
      val loaded = send(client, base, api, cookie = Some(s"oath_seat=$code"))
      assertEquals(loaded.statusCode(), 200, loaded.body())
      val site = ujson.read(loaded.body())("world")(0)("sites")(0)("siteId").str
      val command = ujson.write(ujson.Obj("expectedNextSequence" -> 1,
        "intent" -> ujson.Obj("type" -> "placePawn", "siteId" -> site)))
      val spoofed = command.replace("\"type\":\"placePawn\"", "\"type\":\"placePawn\",\"playerId\":\"p1\"")
      assertEquals(send(client, base, api + "/commands", Some(spoofed), Some(s"oath_seat=$code")).statusCode(), 400)
      val stale = send(client, base, api + "/commands", Some(command.replace(":1,", ":0,")), Some(s"oath_seat=$code"))
      assertEquals(stale.statusCode(), 409, stale.body())
      assertEquals(ujson.read(send(client, base, api, cookie = Some(s"oath_seat=$code")).body())("nextSequence").num, 1.0)
      val accepted = send(client, base, api + "/commands", Some(command), Some(s"oath_seat=$code"))
      assertEquals(accepted.statusCode(), 200, accepted.body())
      assert(ujson.read(accepted.body())("pendingCardDecision")("cards").arr.nonEmpty)
      val reloaded = send(client, base, api, cookie = Some(s"oath_seat=$code"))
      assert(ujson.read(reloaded.body())("pendingCardDecision")("cards").arr.nonEmpty)
      val otherCode = URI.create(created.seats.head.url).getPath.stripPrefix("/s/")
      val other = send(client, base, api, cookie = Some(s"oath_seat=$otherCode"))
      assertEquals(other.statusCode(), 200)
      assert(ujson.read(other.body())("pendingCardDecision").isNull)
      Vector(accepted.body(), reloaded.body(), other.body()).foreach { body =>
        assert(!body.contains(code))
        assert(!body.contains("denizenOrder"))
        assert(!body.contains("relicOrder"))
      }
    }
  }

  test("HTTPS public origin sets Secure even when the proxy connection uses HTTP") {
    withServer(Some("https://games.example.test")) { (base, _) =>
      val client = HttpClient.newHttpClient()
      val created = create(client, base, "tls")
      assert(created.seats.forall(_.url.startsWith("https://games.example.test/s/")))
      val code = URI.create(created.seats.head.url).getPath.stripPrefix("/s/")
      val exchanged = send(client, base, s"/s/$code")
      assertEquals(exchanged.statusCode(), 303)
      assertCookie(exchanged.headers().firstValue("Set-Cookie").orElse(""), code, "/games/tls", secure = true)
    }
  }

  test("invalid links are generic and canonical pages recover absent, malformed, wrong-game and missing-game cookies") {
    withServer() { (base, runtime) =>
      val client = HttpClient.newHttpClient()
      val created = create(client, base, "private-game")
      val code = URI.create(created.seats.head.url).getPath.stripPrefix("/s/")
      val invalid = send(client, base, "/s/malformed-secret")
      val unknown = send(client, base, "/s/AAAAAAAAAAAAAAAAAAAAAA")
      assertEquals(invalid.statusCode(), 404)
      assertEquals(invalid.body(), unknown.body())
      assert(!invalid.body().contains("malformed-secret"))
      assert(!invalid.body().contains("private-game"))
      assert(invalid.headers().firstValue("Set-Cookie").isEmpty)
      val recovery = send(client, base, "/games/private-game")
      assertEquals(recovery.statusCode(), 403)
      assert(recovery.body().contains("assigned seat link"))
      Vector(None, Some("oath_seat=broken"), Some(s"oath_seat=$code")).foreach { cookie =>
        val response = send(client, base, "/games/another-game", cookie = cookie)
        assertEquals(response.statusCode(), 403)
        assertEquals(response.body(), recovery.body())
        assert(!response.body().contains(code))
        assertEquals(send(client, base, "/games/another-game/api", cookie = cookie).statusCode(), 403)
      }
      val missingCode = SeatCode.parse("AQEBAQEBAQEBAQEBAQEBAQ").toOption.get
      assert(runtime.identities.createTrustedSeats("missing-stream", Vector(missingCode.digest -> "p1"), 0L).isRight)
      val missing = send(client, base, "/games/missing-stream", cookie = Some(s"oath_seat=${missingCode.raw}"))
      assertEquals(missing.statusCode(), 403)
      assertEquals(missing.body(), recovery.body())
    }
  }

  test("trusted endpoints reject queries and cross-origin mutations while accepting configured or missing Origin") {
    withServer(Some("https://games.example.test")) { (base, _) =>
      val client = HttpClient.newHttpClient()
      val body = creationBody("origin-game")
      Vector("https://evil.example.test", "null", "https://games.example.test.evil", "http://games.example.test").foreach { origin =>
        assertEquals(send(client, base, "/games", Some(body), origin = Some(origin)).statusCode(), 403)
      }
      val accepted = send(client, base, "/games", Some(body), origin = Some("https://games.example.test"))
      assertEquals(accepted.statusCode(), 201, accepted.body())
      val created = TrustedGameCreateResponseCodec.decode(accepted.body()).toOption.get
      val code = URI.create(created.seats(1).url).getPath.stripPrefix("/s/")
      val cookie = Some(s"oath_seat=$code")
      val command = """{"expectedNextSequence":0,"intent":{"type":"endWake"}}"""
      val preview = """{"expectedNextSequence":1,"action":"travel"}"""
      Vector("/games", s"/s/$code", "/games/origin-game", "/games/origin-game/api",
        "/games/origin-game/api/commands", "/games/origin-game/api/preview").foreach { path =>
        val payload = if (path == "/games") Some(body) else if (path.endsWith("commands")) Some(command)
          else if (path.endsWith("preview")) Some(preview) else None
        val result = send(client, base, path + "?playerId=p1", payload, cookie)
        assertEquals(result.statusCode(), 400, result.body())
        assert(!result.body().contains(code))
      }
      Vector("commands" -> command, "preview" -> preview).foreach { case (endpoint, payload) =>
        assertEquals(send(client, base, s"/games/origin-game/api/$endpoint", Some(payload), cookie,
          Some("https://evil.example.test")).statusCode(), 403)
      }
      assertEquals(send(client, base, "/games/origin-game/api/commands", Some(command), cookie,
        Some("https://games.example.test")).statusCode(), 409)
      assertEquals(send(client, base, "/games/origin-game/api/preview",
        Some(preview.replace(":1,", ":0,")), cookie).statusCode(), 409)
      assertEquals(send(client, base, "/games/origin-game/api/preview",
        Some(preview.dropRight(1) + ",\"playerId\":\"p1\"}"), cookie).statusCode(), 400)
    }
  }

  test("same-name cookies for two game paths coexist and restore each seat") {
    withServer() { (base, _) =>
      val cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL)
      val client = HttpClient.newBuilder().cookieHandler(cookies).build()
      Vector("one", "two").foreach { game =>
        val created = create(client, base, game)
        val path = URI.create(created.seats(1).url).getPath
        assertEquals(send(client, base, path).statusCode(), 303)
      }
      assertEquals(cookies.getCookieStore.getCookies.asScala.map(_.getPath).toSet,
        Set("/games/one", "/games/two"))
      Vector("one", "two").foreach { game =>
        assertEquals(send(client, base, s"/games/$game").statusCode(), 200)
        val loaded = send(client, base, s"/games/$game/api")
        assertEquals(loaded.statusCode(), 200, loaded.body())
        assertEquals(ujson.read(loaded.body())("gameId").str, game)
      }
    }
  }

  test("storage failures never expose seat credentials in responses or log events") {
    withServer() { (base, runtime) =>
      val client = HttpClient.newHttpClient()
      val created = create(client, base, "failure")
      val code = URI.create(created.seats.head.url).getPath.stripPrefix("/s/")
      val logger = org.slf4j.LoggerFactory.getLogger("oathdigital.server.TrustedSeatRoutes")
        .asInstanceOf[ch.qos.logback.classic.Logger]
      val captured = new ch.qos.logback.core.read.ListAppender[ch.qos.logback.classic.spi.ILoggingEvent]
      captured.start(); logger.addAppender(captured)
      try {
        runtime.close()
        Vector(s"/s/$code", "/games/failure", "/games/failure/api").foreach { path =>
          val failed = send(client, base, path, cookie = Some(s"oath_seat=$code"))
          assertEquals(failed.statusCode(), 500)
          assert(!failed.body().contains(code))
          assert(!failed.body().toLowerCase.contains("closed"))
        }
        assert(captured.list.size() >= 3)
        captured.list.asScala.foreach { event =>
          assert(!event.getFormattedMessage.contains(code))
          assertEquals(event.getThrowableProxy, null)
          assert(!Option(event.getArgumentArray).toVector.flatMap(_.toVector).mkString.contains(code))
        }
      } finally { logger.detachAppender(captured); captured.stop() }
    }
  }

  private def assertCookie(raw: String, code: String, path: String, secure: Boolean): Unit = {
    val attributes = raw.split(";\\s*").toSet
    val expected = Set(s"oath_seat=$code", s"Path=$path", "HttpOnly", "SameSite=Lax", "Max-Age=31536000")
    assertEquals(attributes, if (secure) expected + "Secure" else expected)
  }

  private def creationBody(gameId: String): String = TrustedGameCreateRequestCodec.encode(
    TrustedGameCreateRequest(gameId, Vector(BootstrapParticipantRequest("p1", "l1", "red"),
      BootstrapParticipantRequest("p2", "l2", "blue")), "p2"))

  private def create(client: HttpClient, base: String, gameId: String): TrustedGameCreateResponse = {
    val response = send(client, base, "/games", Some(creationBody(gameId)))
    assertEquals(response.statusCode(), 201, response.body())
    TrustedGameCreateResponseCodec.decode(response.body()).toOption.get
  }

  private def send(client: HttpClient, base: String, path: String, body: Option[String] = None,
      cookie: Option[String] = None, origin: Option[String] = None): JavaResponse[String] = {
    val builder = HttpRequest.newBuilder(URI.create(base + path))
    cookie.foreach(builder.header("Cookie", _))
    origin.foreach(builder.header("Origin", _))
    body.fold(builder.GET())(value => builder.header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(value)))
    client.send(builder.build(), JavaResponse.BodyHandlers.ofString())
  }

  private def withServer(origin: Option[String] = None)(body: (String, ServerRuntime) => Unit): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "trusted-seat-route-test")
    val blocking = system.dispatchers.lookup(DispatcherSelector.fromConfig("oathdigital.blocking-dispatcher"))
    val database = Files.createTempDirectory("trusted-seat-routes-").resolve("database")
    val catalogPath = Paths.get("docs/catalog/new-foundations-component-catalog.json")
    val runtime = ServerRuntime.open(database, catalogPath).toOption.get
    val config = ServerConfig("127.0.0.1", 8080, origin.map(URI.create), database, catalogPath,
      ServerMode.TrustedAlpha, None, "test")
    val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(
      ServerRoutes.route(runtime, blocking, config, ServerReadiness.starting("test"))), 10.seconds)
    try body(s"http://127.0.0.1:${binding.localAddress.getPort}", runtime)
    finally {
      Await.result(binding.terminate(5.seconds), 10.seconds)
      runtime.close(); system.terminate(); Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
