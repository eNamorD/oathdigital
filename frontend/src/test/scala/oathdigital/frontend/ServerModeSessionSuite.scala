package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.{RecordedRequest,
  withEmptyLogPage}
import oathdigital.model.PlayerColor
import oathdigital.protocol.projection.GameProjectionCodec
import oathdigital.testkit.Unwrap.present

import munit.FunSuite
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** A mounted session in the trusted and development modes: which requests it
  * makes, what it shows, and how it reacts to conflicts, lost identity and
  * lost connections.
  */
class ServerModeSessionSuite extends FunSuite:
  private val redAndBlueExiles = Vector(
    GamePlayer("red", "Red Exile", "exile", PlayerColor.Red),
    GamePlayer("blue", "Blue Exile", "exile", PlayerColor.Blue))

  /** The game "my game" as the server sends it: red and blue are exiles,
    * `active` is to act at `phase`, and `viewer` is the seat the response
    * names as the one looking (none for a development session, which is
    * named by no viewer). Nothing is legal unless `legalControls` says so.
    */
  private def encodedGame(viewer: Option[String] = Some("blue"),
      active: String = "red", phase: String = "awaiting-pawn",
      legalControls: Vector[String] = Vector.empty, ready: Boolean = false,
      nextSequence: Long = 1L): String =
    GameProjectionCodec.encode(TestProjections.actingProjection(
      gameId = "my game", nextSequence = nextSequence, phase = phase,
      activePlayer = Some(active), players = redAndBlueExiles,
      legalControls = legalControls, ready = ready)
      .copy(viewerPlayerId = viewer))

  /** Blue's Wake, with endWake legal and so a wake-action button on screen,
    * as `viewer` sees it.
    */
  private def encodedBlueWake(viewer: Option[String] = Some("blue"),
      nextSequence: Long = 1L): String =
    encodedGame(viewer, active = "blue", phase = "wake",
      legalControls = Vector("endWake"), ready = true,
      nextSequence = nextSequence)

  test("trusted player renders its fixed seat and never bootstraps " +
      "switches or loads raw events"):
    TestBrowser.use("?gameId=wrong&playerId=red"): browser =>
      val requests = ArrayBuffer.empty[RecordedRequest]
      // Spy and stub of JsonTransport: records every request; answers each
      // with the one game.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          requests += RecordedRequest(method, url, body)
          Future.successful(Right(TransportResponse(200,
            encodedGame(viewer = Some("blue"), active = "red"))))
      Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
        withEmptyLogPage(transport))
      browser.settle.map { _ =>
        assertEquals(requests.map(r => r.method -> r.url).toVector,
          Vector("GET" -> "/games/my%20game/api"))
        assert(browser.text.contains("Blue Exile"))
        assert(browser.text.contains("Waiting for"))
        assert(browser.byClass("seat-identity").head.textContent
          .contains("Blue Exile"))
        assert(browser.byClass("debug-toolbar").isEmpty)
        assert(browser.byClass("player-selector").isEmpty)
        assert(browser.byClass("raw-event-log").isEmpty)
        assert(browser.byClass("restart").isEmpty)
        assert(browser.urls.isEmpty)
      }

  test("trusted unauthorized player displays seat-link recovery without " +
      "bootstrap"):
    TestBrowser.use(): browser =>
      var requests = 0
      // Spy and stub of JsonTransport: counts requests; answers each with 401.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          requests += 1
          Future.successful(Right(TransportResponse(401,
            """{"error":"unauthorized","message":"internal detail"}""")))
      Main.start(browser.mount, "/games/missing", trustedAlpha = true,
        withEmptyLogPage(transport))
      browser.settle.map { _ =>
        assertEquals(requests, 1)
        assert(browser.text.contains("assigned seat link"))
        assert(!browser.text.contains("internal detail"))
        assert(browser.byClass("restart").isEmpty)
      }

  test("development root retains query loading and raw history"):
    TestBrowser.use("?gameId=existing&playerId=red"): browser =>
      val requests = ArrayBuffer.empty[(String, String)]
      // Spy and stub of JsonTransport: records (method, url); answers events
      // and game loads.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          requests += method -> url
          val json = if url.contains("/events") then """{"events":[]}"""
            else encodedGame(viewer = None)
          Future.successful(Right(TransportResponse(200, json)))
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
        assertEquals(requests.toVector,
          Vector("GET" -> "/api/dev/first-games/existing?playerId=red",
            "GET" -> "/api/dev/first-games/existing/events?limit=25",
            "GET" -> "/api/dev/first-games/existing?playerId=red"))
        assert(browser.byClass("debug-toolbar").nonEmpty)
        assert(browser.byClass("player-selector").nonEmpty)
        assert(browser.byClass("raw-event-log").nonEmpty)
        assert(browser.urls.last.contains("gameId=existing&playerId=red"))
        assert(!browser.text.contains("assigned seat link"))
      }

  test("a lost connection shows one notice naming the recovery, not the " +
      "transport error"):
    TestBrowser.use("?gameId=existing&playerId=red"): browser =>
      var loads = 0
      // Spy and stub of JsonTransport: counts game loads; the first lands, the
      // rest fail.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          if url.contains("/events") then
            Future.successful(Right(
              TransportResponse(200, """{"events":[]}""")))
          else
            loads += 1
            if loads == 1 then Future.successful(Right(TransportResponse(200,
              encodedGame(viewer = None))))
            else Future.successful(Left(GameClientFailure.NetworkFailure(
              "GET /api/dev/first-games/existing?playerId=red failed")))
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport))
      // The first load lands; the poll the tick fires is the one that fails.
      browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
        assertEquals(loads, 2)
        val notices = browser.byClass("status")
          .filter(_.getAttribute("class").contains("error"))
        assertEquals(notices.map(_.textContent), Vector(
          "Connection lost. Reconnect to see the current table before acting."))
        assertEquals(notices.head.getAttribute("role"), "alert")
        assert(browser.byClass("reconnectSession").nonEmpty)
        assert(!browser.text.contains("failed"), browser.text)
      }

  /** Spec, behavior change 1. A development session follows the active
    * player; the render between the seat change and the reload used to show
    * the previous seat's walker drafts, since they were cleared only by the
    * reload's reconcile. The reload here never lands, so the interim frame
    * is what the test sees.
    */
  test("a seat change clears the walker drafts before the interim render"):
    TestBrowser.use("?gameId=g&playerId=red"): browser =>
      def snapshot(sequence: Long, active: String): String =
        GameProjectionCodec.encode(TestProjections.actingProjection(
          gameId = "g", nextSequence = sequence, phase = "act",
          activePlayer = Some(active),
          players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red),
            GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)),
          ready = false).copy(walkerDecision = Some(ForgeFixture.parked)))
      var loads = 0
      // Spy and stub of JsonTransport: counts game loads; the first two land,
      // the rest never answer.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          if url.contains("/events") then
            Future.successful(Right(
              TransportResponse(200, """{"events":[]}""")))
          else
            loads += 1
            loads match
              case 1 => Future.successful(Right(
                TransportResponse(200, snapshot(1L, "red"))))
              case 2 => Future.successful(Right(
                TransportResponse(200, snapshot(2L, "blue"))))
              case _ => Future.never
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
        assertEquals(loads, 3)
        // The route still shows Forge's partition heading to the new seat;
        // the zones come from a draft, and there is none.
        assert(browser.byClass("partition-zones").isEmpty, browser.text)
        // Not vacuous: blue's frame did render, with Forge's partition panel.
        assert(browser.text.contains(
          "Development-only active-player view: blue"), browser.text)
        assertEquals(browser.byClass("partition-instruction")
          .map(_.textContent),
          Vector("Assign every option: Pay Favor (2), Pay Secret (1)."))
      }

  /** Spec, verification: the only test that proves the FlowHost wiring. A
    * Recover whose preview offers one modifier renders the ordering panel;
    * toggling and confirming previews again with the invocation, then posts
    * the StartWalker with the handler folded into its own modifiers.
    */
  test("a previewed major action orders its modifier and posts the ordered " +
      "command"):
    TestBrowser.use("?gameId=g&playerId=red"): browser =>
      val projection = GameProjectionCodec.encode(
        TestProjections.actingProjection(gameId = "g",
          phase = "act-action-selection",
          players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
          legalControls = Vector("beginRecover"))
          .copy(actionSelectionOpen = true))
      val preview = """{"nextSequence":1,"action":"recover","modifiers":""" +
        """[{"sourceKey":"adviser:p:denizen:a","handlerId":"h.a",""" +
        """"description":"Old Oak"}],"ignoredRules":[],"targets":[]}"""
      val requests = ArrayBuffer.empty[RecordedRequest]
      // Spy and stub of JsonTransport: records every request; answers events,
      // previews and game loads.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          requests += RecordedRequest(method, url, body)
          val json = if url.contains("/events") then """{"events":[]}"""
            else if url.contains("/preview") then preview
            else projection
          Future.successful(Right(TransportResponse(200, json)))
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ =>
        browser.click("recover-action")
        browser.settle
      }.flatMap { _ =>
        assert(browser.byClass("modifier-confirm").nonEmpty, browser.text)
        browser.click("modifier-toggle")
        browser.settle
      }.flatMap { _ =>
        assert(browser.byClass("modifier-ordinal").nonEmpty, browser.text)
        browser.click("modifier-confirm")
        browser.settle
      }.map { _ =>
        val posts = requests.filter(_.method == "POST").toVector
        assertEquals(posts.map(_.url), Vector(
          "/api/dev/first-games/g/preview?playerId=red",
          "/api/dev/first-games/g/preview?playerId=red",
          "/api/dev/first-games/g/commands?playerId=red"))
        assert(posts(1).body.exists(_.contains("\"handlerId\":\"h.a\"")),
          posts(1).body)
        val command = present(posts(2).body, "the posted command body")
        assert(command.contains("\"action\":\"recover\""), command)
        assert(command.contains("\"modifiers\":[\"h.a\"]"), command)
        assert(!command.contains("orderedModifiers"), command)
        assert(browser.byClass("modifier-confirm").isEmpty, browser.text)
      }

  test("development new game button returns to the start page"):
    TestBrowser.use("?mode=server&gameId=existing&playerId=red"): browser =>
      val opened = ArrayBuffer.empty[String]
      // Stub of JsonTransport: answers events and every game load, records
      // nothing.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          Future.successful(Right(TransportResponse(200,
            if url.contains("/events") then """{"events":[]}"""
            else encodedGame(viewer = None))))
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport), navigate = opened += _)
      browser.settle.map { _ =>
        browser.click("restart")
        assertEquals(opened.toVector, Vector("/?mode=server"))
      }

  test("trusted UI reloads after command conflict without retrying or " +
      "changing seat"):
    TestBrowser.use(): browser =>
      val requests = ArrayBuffer.empty[(String, String)]
      val responses = scala.collection.mutable.Queue(
        TransportResponse(200, encodedBlueWake()),
        TransportResponse(409,
          """{"error":"stale-client-position","message":"position changed"}"""),
        TransportResponse(200, encodedGame(nextSequence = 2L)))
      // Spy and stub of JsonTransport: records (method, url); answers from the
      // queued responses.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          requests += method -> url
          Future.successful(Right(responses.dequeue()))
      Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ =>
        browser.click("wake-action")
        browser.settle
      }.map { _ =>
        assertEquals(requests.toVector, Vector("GET" -> "/games/my%20game/api",
          "POST" -> "/games/my%20game/api/commands",
          "GET" -> "/games/my%20game/api"))
        assert(browser.byClass("seat-identity").head.textContent
          .contains("Blue Exile"))
        assert(browser.text.contains("Waiting for"))
        assert(browser.text.contains("refreshed without retrying"))
        assert(browser.urls.isEmpty)
      }

  test("trusted polling stops and clears private state when cookie access " +
      "is lost"):
    TestBrowser.use(): browser =>
      var requests = 0
      // Spy and stub of JsonTransport: counts requests; the first lands, the
      // rest are refused with 403.
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String])
            : Future[Either[GameClientFailure, TransportResponse]] =
          requests += 1
          Future.successful(Right(if requests == 1 then
            TransportResponse(200, encodedGame()) else TransportResponse(403,
              """{"error":"forbidden","message":"denied"}""")))
      Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
        assertEquals(requests, 2)
        assert(browser.text.contains("assigned seat link"))
        assert(browser.byClass("wake-actions").isEmpty)
        browser.tick()
        assertEquals(requests, 2)
      }

  Vector("changed" -> Some("red"), "absent" -> None,
    "invalid" -> Some("unknown-seat")).foreach { case (label, viewer) =>
    test(s"trusted same-sequence poll rejects $label viewer and disables " +
        "old controls"):
      TestBrowser.use(): browser =>
        val requests = ArrayBuffer.empty[(String, String)]
        // Spy and stub of JsonTransport: records (method, url); answers the
        // first load, then loads that name `viewer`.
        val transport = new JsonTransport:
          def request(method: String, url: String, body: Option[String])
              : Future[Either[GameClientFailure, TransportResponse]] =
            requests += method -> url
            Future.successful(Right(TransportResponse(200,
              if requests.size == 1 then encodedBlueWake()
              else encodedBlueWake(viewer = viewer))))
        Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
          withEmptyLogPage(transport))
        browser.settle.flatMap { _ =>
          // The control on screen before the poll: once the viewer is
          // rejected its handler must submit nothing.
          val oldControl = browser.byClass("wake-action").head
            .asInstanceOf[js.Dynamic]
          browser.tick()
          browser.settle.map { _ =>
            assert(browser.text.contains("assigned seat link"))
            assert(browser.byClass("wake-actions").isEmpty)
            assert(browser.byClass("wake-action").isEmpty)
            oldControl.onclick(js.Dynamic.literal())
            browser.tick()
            assertEquals(requests.toVector, Vector(
              "GET" -> "/games/my%20game/api",
              "GET" -> "/games/my%20game/api"))
          }
        }
  }

  Vector(200, 401, 403).foreach { status =>
    test(s"trusted identity loss with HTTP $status rejects a late command " +
        "response from the previous seat session"):
      TestBrowser.use(): browser =>
        val pending = Promise[Either[GameClientFailure, TransportResponse]]()
        var loads = 0
        var commands = 0
        // Spy and stub of JsonTransport: counts loads and commands; the command
        // never answers.
        val transport = new JsonTransport:
          def request(method: String, url: String, body: Option[String])
              : Future[Either[GameClientFailure, TransportResponse]] =
            if method == "POST" then { commands += 1; pending.future }
            else
              loads += 1
              Future.successful(Right(
                if loads == 1 then TransportResponse(200, encodedBlueWake())
                else if status == 200 then TransportResponse(200,
                  encodedBlueWake(viewer = Some("red")))
                else TransportResponse(status,
                  """{"error":"forbidden","message":"denied"}""")))
        Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
          withEmptyLogPage(transport))
        browser.settle.flatMap { _ =>
          browser.click("wake-action")
          browser.tick()
          browser.settle
        }.flatMap { _ =>
          pending.success(Right(TransportResponse(200,
            encodedBlueWake(nextSequence = 2L))))
          browser.settle
        }.map { _ =>
          assert(browser.text.contains("assigned seat link"))
          assert(browser.byClass("wake-action").isEmpty)
          browser.tick()
          assertEquals(loads, 2)
          assertEquals(commands, 1)
        }
  }
