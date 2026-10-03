package oathdigital.server

import oathdigital.model.PlayerColor

import java.net.{CookieManager, CookiePolicy, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JavaResponse}
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import oathdigital.application._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.protocol._
import oathdigital.testkit.{CatalogNames, HttpTestServer}
import oathdigital.testkit.ChronicleShuffles.reversing
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

class TrustedSeatRoutesSuite extends munit.FunSuite:
  /** The server generates game IDs. These tests name the ID each creation
    * receives, like `alpha:one` for the path-encoding tests, by queueing it
    * before the POST; an empty queue falls back to a generated ID. */
  private val nextGameIds = new java.util.concurrent.ConcurrentLinkedQueue[String]()
  private val gameIds: () => String = () =>
    Option(nextGameIds.poll()).getOrElse(TrustedGameProvisioning.generateGameId())

  /** The seat cookie's lifetime: one year, in seconds. */
  private val oneYearSeconds = 365 * 24 * 3600

  /** "trusted", begun with p2 first and parked on p2's Setup pawn
    * placement, behind a trusted gateway; `afterBegin` is the sequence after
    * Begin. */
  private final case class TrustedGatewayGame(service: GameApplicationService,
      gateway: TrustedGameGateway, afterBegin: Long)

  private def begunTrustedGame(): TrustedGatewayGame =
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository)
    val begun = accepted(service.handle("trusted", 0L,
      GameCommand.Begin(chronicle, orders.copy(firstPlayer = p2))))
    TrustedGatewayGame(service,
      new TrustedGameGateway(service, new GameProjector(catalog)),
      begun.nextSequence)

  private val trustedP2Seat = TrustedSeat("trusted", "p2")
  private val trustedP3Seat = TrustedSeat("trusted", "p3")

  /** p2's answer to its pawn placement: Ancient City. */
  private def placeP2Pawn(afterBegin: Long): ActorlessCommandRequest =
    val pawnDecision = SetupProcedure.pawnDecisionId(p2)
    val ancientCity = CatalogNames.site("Ancient City").value
    accepted(GameHttpWire.decodeCommand(
      s"""{"expectedNextSequence":$afterBegin,""" +
        s""""intent":{"type":"resolveWalker",""" +
        s""""decisionId":"$pawnDecision","payload":{"kind":"choose-one",""" +
        s""""optionKind":"site","optionId":"$ancientCity"}}}"""))

  test("the trusted gateway projects only the seat's own view"):
    val TrustedGatewayGame(service, gateway, _) = begunTrustedGame()
    assertEquals(
      accepted(gateway.load("trusted", trustedP3Seat)).viewerPlayerId,
      Some("p3"))
    val development = new GameProjector(catalog).project("trusted",
      present(accepted(service.load("trusted")), "the trusted game"), p2)
    assertEquals(development.viewerPlayerId, None)
    assert(!oathdigital.protocol.projection.GameProjectionCodec.encode(development)
      .contains("viewerPlayerId"))
    assert(accepted(gateway.load("trusted", trustedP2Seat)).walkerDecision
      .nonEmpty)
    assert(accepted(gateway.load("trusted", trustedP3Seat)).walkerDecision
      .isEmpty)

  test("the trusted gateway binds the command actor to the seat"):
    val TrustedGatewayGame(service, gateway, afterBegin) = begunTrustedGame()
    val placed = accepted(
      gateway.submit("trusted", trustedP2Seat, placeP2Pawn(afterBegin)))
    assertEquals(placed.viewerPlayerId, Some("p2"))
    assert(placed.walkerDecision.nonEmpty)
    assertEquals(
      present(accepted(service.load("trusted")), "the trusted game")
        .nextSequence,
      placed.nextSequence)
    // A fresh load after the answer shows p2's next decision to p2 only.
    assert(accepted(gateway.load("trusted", trustedP2Seat)).walkerDecision
      .nonEmpty)
    assert(accepted(gateway.load("trusted", trustedP3Seat)).walkerDecision
      .isEmpty)
    assert(GameHttpWire.decodeCommand("""{"expectedNextSequence":2,"intent":{"type":"resolveWalker","playerId":"p3","decisionId":"setup.pawn-placement.p3","payload":{"kind":"choose-one","optionKind":"site","optionId":"S1"}}}""").isLeft)

  test("the trusted gateway refuses another game and a stale position"):
    val TrustedGatewayGame(_, gateway, afterBegin) = begunTrustedGame()
    val command = placeP2Pawn(afterBegin)
    assertEquals(gateway.load("other", trustedP2Seat),
      Left(TrustedSeatFailure.Forbidden))
    assertEquals(gateway.submit("other", trustedP2Seat, command),
      Left(TrustedSeatFailure.Forbidden))
    assertEquals(gateway.log("other", trustedP2Seat, 0L),
      Left(TrustedSeatFailure.Forbidden))
    val placed = accepted(gateway.submit("trusted", trustedP2Seat, command))
    assertEquals(gateway.submit("trusted", trustedP2Seat, command),
      Left(TrustedSeatFailure.Application(GameApplicationError.StaleClientPosition(
        afterBegin, placed.nextSequence))))

  test("trusted preview binds resolved actor and rejects a different game"):
    val repository = new InMemoryEventStreamRepository
    // initialSituation's first player is p2; p1 is another seat.
    val act = initialSituation().endingWake(p2)
    act.seedInto(repository, "preview")
    val service = new GameApplicationService(catalog, repository)
    val gateway = new TrustedGameGateway(service, new GameProjector(catalog))
    val sequence = act.nextSequence
    val request = MajorActionPreviewRequest(sequence, "travel")
    val previewP2Seat = TrustedSeat("preview", p2.value)
    assert(gateway.preview("preview", previewP2Seat, request).isRight)
    assertEquals(
      gateway.preview("preview", TrustedSeat("preview", p1.value), request)
        .left.toOption,
      Some(TrustedSeatFailure.Application(GameApplicationError.CommandRejected(
        oathdigital.model.OathViolation.InvalidModifierInvocation(
          "major-action preview is unavailable for this actor or phase")))))
    assertEquals(gateway.preview("other", previewP2Seat, request),
      Left(TrustedSeatFailure.Forbidden))
    assertEquals(
      present(accepted(service.load("preview")), "the preview game")
        .nextSequence,
      sequence)

  test("creation returns ordered public links"):
    withServer() { (server, _) =>
      val created = create(server, "game-a")
      assertEquals(created.gameId, "game-a")
      assertEquals(created.seats.map(_.playerId), Vector("p1", "p2"))
      assert(created.seats.forall(_.url.startsWith("http://127.0.0.1:8080/s/")))
    }

  test("a seat link exchanges for an HttpOnly cookie scoped to its game and " +
      "redirects without the code"):
    withServer() { (server, _) =>
      val code = codeOf(create(server, "game-a"), "p2")
      val entry = send(server, s"/s/$code")
      assertEquals(entry.statusCode(), 303, entry.body())
      assertEquals(entry.headers().firstValue("Location").orElse(""), "/games/game-a")
      assert(!entry.body().contains(code))
      assert(!entry.headers().firstValue("Location").orElse("").contains(code))
      val cookie = entry.headers().firstValue("Set-Cookie").orElse("")
      assertCookie(cookie, code, "/games/game-a", secure = false)
    }

  test("the seat's cookie loads the canonical page and the private API"):
    withServer() { (server, _) =>
      val code = codeOf(create(server, "game-a"), "p2")
      val canonical = send(server, "/games/game-a",
        cookie = Some(s"oath_seat=$code"))
      assertEquals(canonical.statusCode(), 200, canonical.body())
      assert(canonical.body().contains("/assets/main.js"))
      assert(!canonical.body().contains(code))
      val loaded = send(server, "/games/game-a/api",
        cookie = Some(s"oath_seat=$code"))
      assertEquals(loaded.statusCode(), 200, loaded.body())
    }

  /** p2's seat code in "game-a", p1's seat code, the sequence p2's seat
    * loaded at, and the command that answers p2's parked pawn placement with
    * the first offered site. */
  private final case class SeatPark(code: String, p1Code: String,
      currentSequence: Long, command: String)

  /** The command that answers the decision parked in `loaded`, the response
    * to a seat's API load, with its first offered site. */
  private def answerFirstOfferedSite(loaded: JavaResponse[String]): String =
    val projection = ujson.read(loaded.body())
    val decision = projection("walkerDecision")
    ujson.write(ujson.Obj(
      "expectedNextSequence" -> projection("nextSequence"),
      "intent" -> ujson.Obj("type" -> "resolveWalker",
        "decisionId" -> decision("decisionId").str,
        "payload" -> ujson.Obj("kind" -> "choose-one", "optionKind" -> "site",
          "optionId" -> decision("query")("options")(0)("id").str))))

  /** Creates "game-a" and loads p2's seat, the first player under
    * `reversing`. */
  private def p2SeatParked(server: HttpTestServer): SeatPark =
    val created = create(server, "game-a")
    val code = codeOf(created, "p2")
    val loaded = send(server, "/games/game-a/api",
      cookie = Some(s"oath_seat=$code"))
    assertEquals(loaded.statusCode(), 200, loaded.body())
    val currentSequence = ujson.read(loaded.body())("nextSequence").num.toLong
    SeatPark(code, codeOf(created, "p1"), currentSequence,
      answerFirstOfferedSite(loaded))

  test("a seat's commands refuse a spoofed actor and a stale position, then " +
      "accept the seat's answer"):
    withServer() { (server, _) =>
      val SeatPark(code, _, currentSequence, command) = p2SeatParked(server)
      val api = "/games/game-a/api"
      val spoofed = command.replace("\"type\":\"resolveWalker\"",
        "\"type\":\"resolveWalker\",\"playerId\":\"p1\"")
      assertEquals(send(server, api + "/commands", Some(spoofed),
        Some(s"oath_seat=$code")).statusCode(), 400)
      val stale = send(server, api + "/commands", Some(command.replace(
        s""""expectedNextSequence":$currentSequence,""", """"expectedNextSequence":0,""")),
        Some(s"oath_seat=$code"))
      assertEquals(stale.statusCode(), 409, stale.body())
      assertEquals(ujson.read(send(server, api,
        cookie = Some(s"oath_seat=$code")).body())("nextSequence").num,
        currentSequence.toDouble)
      val answered = send(server, api + "/commands", Some(command),
        Some(s"oath_seat=$code"))
      assertEquals(answered.statusCode(), 200, answered.body())
      assert(ujson.read(answered.body())("walkerDecision")("query")("options")
        .arr.nonEmpty)
      val reloaded = send(server, api, cookie = Some(s"oath_seat=$code"))
      assert(ujson.read(reloaded.body())("walkerDecision")("query")("options").arr.nonEmpty)
    }

  test("the other seat sees no decision, and no seat response leaks the code " +
      "or the plan"):
    withServer() { (server, _) =>
      val SeatPark(code, p1Code, _, command) = p2SeatParked(server)
      val api = "/games/game-a/api"
      val answered = send(server, api + "/commands", Some(command),
        Some(s"oath_seat=$code"))
      assertEquals(answered.statusCode(), 200, answered.body())
      val reloaded = send(server, api, cookie = Some(s"oath_seat=$code"))
      val other = send(server, api, cookie = Some(s"oath_seat=$p1Code"))
      assertEquals(other.statusCode(), 200)
      assert(ujson.read(other.body())("walkerDecision").isNull)
      Vector(answered.body(), reloaded.body(), other.body()).foreach { body =>
        assert(!body.contains(code))
        assert(!body.contains("denizenOrder"))
        assert(!body.contains("relicOrder"))
      }
    }

  test("the log route pages for the seat's cookie and checks the cursor"):
    withServer() { (server, _) =>
      val code = codeOf(create(server, "log-game"), "p2")
      val cookie = Some(s"oath_seat=$code")
      val page = send(server, "/games/log-game/api/log/0", cookie = cookie)
      assertEquals(page.statusCode(), 200, page.body())
      assertEquals(page.headers().firstValue("Cache-Control").orElse(""), "no-store")
      val decoded = accepted(oathdigital.protocol.projection.LogPageCodec
        .decode(page.body()))
      assertEquals(decoded.after, 0L)
      assertEquals(send(server, "/games/log-game/api/log/0").statusCode(), 403)
      assertEquals(send(server, "/games/log-game/api/log/-1",
        cookie = cookie).statusCode(), 400)
      assertEquals(send(server,
        s"/games/log-game/api/log/${decoded.nextSequence + 1}",
        cookie = cookie).statusCode(), 400)
      assertEquals(send(server, "/games/log-game/api/log/0?x=1",
        cookie = cookie).statusCode(), 400)
    }

  test("HTTPS public origin sets Secure even when the proxy connection uses HTTP"):
    withServer(Some("https://games.example.test")) { (server, _) =>
      val created = create(server, "tls")
      assert(created.seats.forall(_.url.startsWith("https://games.example.test/s/")))
      val code = codeOf(created, "p1")
      val exchanged = send(server, s"/s/$code")
      assertEquals(exchanged.statusCode(), 303)
      assertCookie(exchanged.headers().firstValue("Set-Cookie").orElse(""), code, "/games/tls", secure = true)
    }

  test("invalid links are generic and canonical pages recover absent, malformed, wrong-game and missing-game cookies"):
    withServer() { (server, _) =>
      val code = codeOf(create(server, "private-game"), "p1")
      val invalid = send(server, "/s/malformed-secret")
      val unknown = send(server, "/s/AAAAAAAAAAAAAAAAAAAAAA")
      assertEquals(invalid.statusCode(), 404)
      assertEquals(invalid.body(), unknown.body())
      assert(!invalid.body().contains("malformed-secret"))
      assert(!invalid.body().contains("private-game"))
      assert(invalid.headers().firstValue("Set-Cookie").isEmpty)
      val recovery = send(server, "/games/private-game")
      assertEquals(recovery.statusCode(), 403)
      assert(recovery.body().contains("assigned seat link"))
      Vector(None, Some("oath_seat=broken"), Some(s"oath_seat=$code")).foreach { cookie =>
        val response = send(server, "/games/another-game", cookie = cookie)
        assertEquals(response.statusCode(), 403)
        assertEquals(response.body(), recovery.body())
        assert(!response.body().contains(code))
        assertEquals(send(server, "/games/another-game/api",
          cookie = cookie).statusCode(), 403)
      }
    }

  /** "origin-game", created from the configured HTTPS origin, with p2's
    * seat cookie, an endWake command expecting sequence 0 and a travel
    * preview expecting 1. The game is at 2 (GameStarted and WalkerParked),
    * so both are stale. */
  private final case class OriginGame(code: String, cookie: Option[String],
      command: String, preview: String)

  private def originGame(server: HttpTestServer): OriginGame =
    val created = send(server, "/games", Some(creationBody("origin-game")),
      origin = Some("https://games.example.test"))
    assertEquals(created.statusCode(), 201, created.body())
    val code = codeOf(
      accepted(TrustedGameCreateResponseCodec.decode(created.body())), "p2")
    OriginGame(code, Some(s"oath_seat=$code"),
      """{"expectedNextSequence":0,"intent":{"type":"endWake"}}""",
      """{"expectedNextSequence":1,"action":"travel"}""")

  test("trusted endpoints reject queries and cross-origin mutations"):
    withServer(Some("https://games.example.test")) { (server, _) =>
      Vector("https://evil.example.test", "null", "https://games.example.test.evil", "http://games.example.test").foreach { origin =>
        assertEquals(send(server, "/games", Some(requestBody),
          origin = Some(origin)).statusCode(), 403)
      }
      val OriginGame(code, cookie, command, preview) = originGame(server)
      Vector("/games", s"/s/$code", "/games/origin-game", "/games/origin-game/api",
        "/games/origin-game/api/commands", "/games/origin-game/api/preview").foreach { path =>
        val payload =
          if path == "/games" then Some(requestBody)
          else if path.endsWith("commands") then Some(command)
          else if path.endsWith("preview") then Some(preview)
          else None
        val result = send(server, path + "?playerId=p1", payload, cookie)
        assertEquals(result.statusCode(), 400, result.body())
        assert(!result.body().contains(code))
      }
      Vector("commands" -> command, "preview" -> preview).foreach { case (endpoint, payload) =>
        assertEquals(send(server, s"/games/origin-game/api/$endpoint",
          Some(payload), cookie,
          Some("https://evil.example.test")).statusCode(), 403)
      }
    }

  test("stale commands and previews are refused with 409 under the " +
      "configured Origin or none"):
    withServer(Some("https://games.example.test")) { (server, _) =>
      val OriginGame(_, cookie, command, preview) = originGame(server)
      assertEquals(send(server, "/games/origin-game/api/commands",
        Some(command), cookie,
        Some("https://games.example.test")).statusCode(), 409)
      assertEquals(send(server, "/games/origin-game/api/preview",
        Some(preview.replace(":1,", ":0,")), cookie).statusCode(), 409)
    }

  test("a preview body with a playerId is refused"):
    withServer(Some("https://games.example.test")) { (server, _) =>
      val OriginGame(_, cookie, _, preview) = originGame(server)
      assertEquals(send(server, "/games/origin-game/api/preview",
        Some(preview.dropRight(1) + ",\"playerId\":\"p1\"}"), cookie).statusCode(), 400)
    }

  test("colon game IDs use the same encoded cookie redirect and API paths"):
    withServer() { (server, _) =>
      val created = create(server, "alpha:one")
      val entry = send(server, URI.create(seatLink(created, "p2")).getPath)
      assertEquals(entry.statusCode(), 303)
      assertEquals(entry.headers().firstValue("Location").orElse(""), "/games/alpha%3Aone")
      val cookie = entry.headers().firstValue("Set-Cookie").orElse("")
      assert(cookie.contains("Path=/games/alpha%3Aone;"), cookie)
      val credential = cookie.takeWhile(_ != ';')
      assertEquals(send(server, "/games/alpha%3Aone",
        cookie = Some(credential)).statusCode(), 200)
      val loaded = send(server, "/games/alpha%3Aone/api",
        cookie = Some(credential))
      assertEquals(loaded.statusCode(), 200, loaded.body())
      assertEquals(ujson.read(loaded.body())("gameId").str, "alpha:one")
      assertEquals(ujson.read(loaded.body())("viewerPlayerId").str, "p2")
    }

  test("trailing game slash redirects to the same encoded canonical page"):
    withServer() { (server, _) =>
      Vector("alpha", "alpha:one").foreach { game =>
        val created = create(server, game)
        val entry = send(server, URI.create(seatLink(created, "p1")).getPath)
        val cookie = entry.headers().firstValue("Set-Cookie").orElse("").takeWhile(_ != ';')
        val path = if game == "alpha" then "/games/alpha" else "/games/alpha%3Aone"
        val redirected = send(server, path + "/", cookie = Some(cookie))
        assertEquals(redirected.statusCode(), 303, redirected.body())
        assertEquals(redirected.headers().firstValue("Location").orElse(""), path)
        assertEquals(send(server, path, cookie = Some(cookie)).statusCode(),
          200)
      }
    }

  test("same-name cookies for two game paths coexist and restore each seat"):
    withServer() { (server, _) =>
      // A browser-like client that stores and sends the cookies it is set.
      val cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL)
      val client = HttpClient.newBuilder().cookieHandler(cookies).build()
      def browse(path: String) = client.send(
        HttpRequest.newBuilder(URI.create(server.base + path)).GET().build(),
        JavaResponse.BodyHandlers.ofString())
      Vector("one", "two").foreach { game =>
        val created = create(server, game)
        val path = URI.create(seatLink(created, "p2")).getPath
        assertEquals(browse(path).statusCode(), 303)
      }
      assertEquals(cookies.getCookieStore.getCookies.asScala.map(_.getPath).toSet,
        Set("/games/one", "/games/two"))
      Vector("one", "two").foreach { game =>
        assertEquals(browse(s"/games/$game").statusCode(), 200)
        val loaded = browse(s"/games/$game/api")
        assertEquals(loaded.statusCode(), 200, loaded.body())
        assertEquals(ujson.read(loaded.body())("gameId").str, game)
      }
    }

  test("seat links and cookies restore private seats after the runtime reopens"):
    val database = Files.createTempDirectory("trusted-seat-restart-").resolve("database")
    var current = openTrustedServer(database)
    try
      val created = create(current.server, "restart-game")
      val firstCode = codeOf(created, "p1")
      val secondCode = codeOf(created, "p2")
      val secondEntry = send(current.server, s"/s/$secondCode")
      assertEquals(secondEntry.statusCode(), 303)
      val retainedCookie = secondEntry.headers().firstValue("Set-Cookie").orElse("")
        .takeWhile(_ != ';')
      assert(retainedCookie.nonEmpty)
      val beforeRestart = send(current.server, "/games/restart-game/api",
        cookie = Some(retainedCookie))
      assertEquals(beforeRestart.statusCode(), 200, beforeRestart.body())
      assertEquals(ujson.read(beforeRestart.body())("viewerPlayerId").str, "p2")

      current.close()
      current = openTrustedServer(database)

      val originalLink = send(current.server, s"/s/$secondCode")
      assertEquals(originalLink.statusCode(), 303)
      assertEquals(originalLink.headers().firstValue("Location").orElse(""), "/games/restart-game")
      val reenteredCookie = originalLink.headers().firstValue("Set-Cookie").orElse("")
        .takeWhile(_ != ';')
      val api = "/games/restart-game/api"
      val restored = send(current.server, api, cookie = Some(retainedCookie))
      assertEquals(restored.statusCode(), 200, restored.body())
      assertEquals(ujson.read(restored.body())("viewerPlayerId").str, "p2")
      val reentered = send(current.server, api, cookie = Some(reenteredCookie))
      assertEquals(reentered.statusCode(), 200, reentered.body())
      assertEquals(ujson.read(reentered.body())("viewerPlayerId").str, "p2")

      val otherCookie = s"oath_seat=$firstCode"
      val otherSeat = send(current.server, api, cookie = Some(otherCookie))
      assertEquals(otherSeat.statusCode(), 200, otherSeat.body())
      assertEquals(ujson.read(otherSeat.body())("viewerPlayerId").str, "p1")
      assert(ujson.read(otherSeat.body())("walkerDecision").isNull)
      val currentSequence = ujson.read(restored.body())("nextSequence").num.toLong
      val command = answerFirstOfferedSite(restored)
      // 422: p1 does not own the parked pawn decision.
      assertEquals(send(current.server, api + "/commands", Some(command),
        Some(otherCookie)).statusCode(), 422)
      assertEquals(ujson.read(send(current.server, api,
        cookie = Some(retainedCookie)).body())("nextSequence").num,
        currentSequence.toDouble)
      assertEquals(send(current.server, api + "/commands", Some(command),
        Some(retainedCookie)).statusCode(), 200)
    finally current.close()

  test("storage failures never expose seat credentials in responses or log events"):
    withServer() { (server, runtime) =>
      val code = codeOf(create(server, "failure"), "p1")
      val logger = org.slf4j.LoggerFactory.getLogger("oathdigital.server.TrustedSeatRoutes")
        .asInstanceOf[ch.qos.logback.classic.Logger]
      val captured = new ch.qos.logback.core.read.ListAppender[ch.qos.logback.classic.spi.ILoggingEvent]
      captured.start(); logger.addAppender(captured)
      try
        runtime.close()
        Vector(s"/s/$code", "/games/failure", "/games/failure/api").foreach { path =>
          val failed = send(server, path, cookie = Some(s"oath_seat=$code"))
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
      finally { logger.detachAppender(captured); captured.stop() }
    }

  private def assertCookie(raw: String, code: String, path: String, secure: Boolean): Unit =
    val attributes = raw.split(";\\s*").toSet
    val expected = Set(s"oath_seat=$code", s"Path=$path", "HttpOnly",
      "SameSite=Lax",
      s"Max-Age=$oneYearSeconds")
    assertEquals(attributes, if secure then expected + "Secure" else expected)

  /** A two-seat create request, p1 Red and p2 Blue. */
  private val requestBody: String =
    TrustedGameCreateRequestCodec.encode(TrustedGameCreateRequest(Vector(BootstrapParticipantRequest("p1", PlayerColor.Red),
      BootstrapParticipantRequest("p2", PlayerColor.Blue))))

  /** `requestBody`, with `gameId` queued as the ID its creation receives. */
  private def creationBody(gameId: String): String =
    nextGameIds.add(gameId)
    requestBody

  private def create(server: HttpTestServer,
    gameId: String): TrustedGameCreateResponse =
    val response = send(server, "/games", Some(creationBody(gameId)))
    assertEquals(response.statusCode(), 201, response.body())
    accepted(TrustedGameCreateResponseCodec.decode(response.body()))

  /** `player`'s public seat link in `created`. */
  private def seatLink(created: TrustedGameCreateResponse,
    player: String): String =
    present(created.seats.find(_.playerId == player), s"$player's seat").url

  /** The raw seat code in `player`'s link. */
  private def codeOf(created: TrustedGameCreateResponse,
    player: String): String =
    URI.create(seatLink(created, player)).getPath.stripPrefix("/s/")

  /** A GET, or a POST of `body`, with an optional seat cookie and Origin; no
    * CSRF token, which the seat routes do not use. */
  private def send(server: HttpTestServer, path: String,
    body: Option[String] = None,
      cookie: Option[String] = None, origin: Option[String] = None): JavaResponse[String] =
    body.fold(server.get(path, cookie = cookie))(value =>
      server.post(path, value, origin = origin, csrf = None, cookie = cookie))

  /** A started trusted-alpha server and the runtime behind it; `close` stops
    * both. */
  private final case class TrustedServer(server: HttpTestServer,
      runtime: ServerRuntime):
    def close(): Unit =
      try server.close()
      finally runtime.close()

  /** The production routes over a runtime opened on `database`, trusted-alpha
    * and configured for 127.0.0.1:8080 with `publicOrigin` as the public base
    * URL. The server binds a free port, so 8080 shows only in origins and
    * seat links. */
  private def openTrustedServer(database: Path,
      publicOrigin: Option[String] = None): TrustedServer =
    // Generated games shuffle seating and make the first seat the first
    // player; reversing the two requested seats makes p2 first.
    val runtime = accepted(ServerRuntime.open(database, reversing, gameIds))
    val config = ServerConfig("127.0.0.1", 8080, publicOrigin.map(URI.create),
      database, ServerMode.TrustedAlpha, None, "test")
    try
      TrustedServer(HttpTestServer.start("trusted-seat-route-test")(blocking =>
        ServerRoutes.route(runtime, blocking, config,
          ServerReadiness.starting("test"))), runtime)
    catch
      case error: Throwable =>
        runtime.close()
        throw error

  private def withServer(publicOrigin: Option[String] = None)(
      body: (HttpTestServer, ServerRuntime) => Unit): Unit =
    val trusted = openTrustedServer(
      Files.createTempDirectory("trusted-seat-routes-").resolve("database"),
      publicOrigin)
    try body(trusted.server, trusted.runtime)
    finally trusted.close()
