package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.withEmptyLogPage
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
    * named by no viewer).
    */
  private def encodedGame(viewer: Option[String] = Some("blue"),
      active: String = "red", phase: String = "awaiting-pawn",
      ready: Boolean = false, nextSequence: Long = 1L): String =
    GameProjectionCodec.encode(TestProjections.actingProjection(
      gameId = "my game", nextSequence = nextSequence, phase = phase,
      activePlayer = Some(active), players = redAndBlueExiles,
      ready = ready).copy(viewerPlayerId = viewer))

  /** Blue's Act menu parked, with End Act and Rest on screen, as `viewer`
    * sees it.
    */
  private def encodedBlueAct(viewer: Option[String] = Some("blue"),
      nextSequence: Long = 1L): String =
    GameProjectionCodec.encode(TestProjections.actingProjection(
      gameId = "my game", nextSequence = nextSequence,
      phase = "act", activePlayer = Some("blue"),
      players = redAndBlueExiles, ready = true).copy(viewerPlayerId = viewer,
      walkerDecision = Some(actMenu(DecisionOptionState("button", "end-act",
        "End Act and Rest")))))

  /** The Act menu as the server parks it, over `options`. */
  private def actMenu(options: DecisionOptionState*): WalkerDecisionState =
    WalkerDecisionState("act-menu", "phase.menu", "decide", query = Some(
      DecisionQueryState.ChooseOne(options.toVector,
        heading = Some("Act: take an action, use a power or rest"))))

  test("trusted player renders its fixed seat and never bootstraps " +
      "switches or loads raw events"):
    TestBrowser.use("?gameId=wrong&playerId=red"): browser =>
      val transport = ScriptedTransport((_, _, _) =>
        Future.successful(Right(TransportResponse(200,
          encodedGame(viewer = Some("blue"), active = "red")))))
      val requests = transport.requests
      Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
        withEmptyLogPage(transport))
      browser.settle.map { _ =>
        assertEquals(requests.map(r => r.method -> r.url).toVector,
          Vector("GET" -> "/games/my%20game/api"))
        assert(browser.text.contains("Blue Exile"))
        assert(browser.text.contains("Waiting for"))
        assert(browser.byClass("seat-identity").head.textContent
          .contains("Blue Exile"))
        assertEquals(browser.byClass("debug-toolbar"), Vector.empty)
        assertEquals(browser.byClass("player-selector"), Vector.empty)
        assertEquals(browser.byClass("raw-event-log"), Vector.empty)
        assertEquals(browser.byClass("restart"), Vector.empty)
        assertEquals(browser.urls, Vector.empty)
      }

  test("trusted unauthorized player displays seat-link recovery without " +
      "bootstrap"):
    TestBrowser.use(): browser =>
      var requests = 0
      val transport = ScriptedTransport((_, _, _) =>
        requests += 1
        Future.successful(Right(TransportResponse(401,
          """{"error":"unauthorized","message":"internal detail"}"""))))
      Main.start(browser.mount, "/games/missing", trustedAlpha = true,
        withEmptyLogPage(transport))
      browser.settle.map { _ =>
        assertEquals(requests, 1)
        assert(browser.text.contains("assigned seat link"))
        assert(!browser.text.contains("internal detail"))
        assertEquals(browser.byClass("restart"), Vector.empty)
      }

  test("development root retains query loading and raw history"):
    TestBrowser.use("?gameId=existing&playerId=red"): browser =>
      val transport = ScriptedTransport((_, url, _) =>
        val json = if url.contains("/events") then """{"events":[]}"""
          else encodedGame(viewer = None)
        Future.successful(Right(TransportResponse(200, json))))
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
        assertEquals(transport.requests.map(r => r.method -> r.url).toVector,
          Vector("GET" -> "/api/dev/first-games/existing?playerId=red",
            "GET" -> "/api/dev/first-games/existing/events?limit=25",
            "GET" -> "/api/dev/first-games/existing?playerId=red"))
        assertEquals(browser.byClass("debug-toolbar").size, 1)
        assertEquals(browser.byClass("player-selector").map(_.textContent),
          Vector("Red Exile", "Blue Exile"))
        assertEquals(browser.byClass("raw-event-log").size, 1)
        assert(browser.urls.last.contains("gameId=existing&playerId=red"))
        assert(!browser.text.contains("assigned seat link"))
      }

  test("a lost connection shows one notice naming the recovery, not the " +
      "transport error"):
    TestBrowser.use("?gameId=existing&playerId=red"): browser =>
      var loads = 0
      val transport = ScriptedTransport((_, url, _) =>
        if url.contains("/events") then
          Future.successful(Right(
            TransportResponse(200, """{"events":[]}""")))
        else
          loads += 1
          if loads == 1 then Future.successful(Right(TransportResponse(200,
            encodedGame(viewer = None))))
          else Future.successful(Left(GameClientFailure.NetworkFailure(
            "GET /api/dev/first-games/existing?playerId=red failed"))))
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
        // The development toolbar offers no second Reconnect: the notice's
        // button is the one recovery in both modes.
        assertEquals(browser.byClass("reconnectSession").map(_.textContent),
          Vector("Reconnect"))
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
      val transport = ScriptedTransport((_, url, _) =>
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
            case _ => Future.never)
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
        assertEquals(loads, 3)
        // The route still shows Forge's partition heading to the new seat;
        // the zones come from a draft, and there is none.
        assertEquals(browser.byClass("partition-zones"), Vector.empty, browser.text)
        // Not vacuous: blue's frame did render, with Forge's partition panel.
        assert(browser.text.contains(
          "Development-only active-player view: blue"), browser.text)
        assertEquals(browser.byClass("partition-instruction")
          .map(_.textContent),
          Vector("Drag and drop the cards. Assign every option: " +
            "Pay Favor (2), Pay Secret (1)."))
      }

  /** An Act menu button posts its pick at once: the action asks for its
    * modifiers inside the walk, so nothing is previewed first. */
  test("an Act menu button posts its pick directly"):
    TestBrowser.use("?gameId=g&playerId=red"): browser =>
      val projection = GameProjectionCodec.encode(
        TestProjections.actingProjection(gameId = "g",
          phase = "act-menu-walker-decision",
          players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
          .copy(walkerDecision = Some(actMenu(
            DecisionOptionState("button", "recover", "Recover",
              badge = Some("1 Supply"))))))
      val transport = ScriptedTransport((_, url, _) =>
        val json = if url.contains("/events") then """{"events":[]}"""
          else projection
        Future.successful(Right(TransportResponse(200, json))))
      val requests = transport.requests
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ =>
        browser.click("menu-option")
        browser.settle
      }.map { _ =>
        val posts = requests.filter(_.method == "POST").toVector
        assertEquals(posts.map(_.url), Vector(
          "/api/dev/first-games/g/commands?playerId=red"))
        val command = present(posts.head.body, "the posted command body")
        assert(command.contains("\"decisionId\":\"phase.menu\""), command)
        assert(command.contains("\"optionId\":\"recover\""), command)
      }

  test("development new game button returns to the start page"):
    TestBrowser.use("?mode=server&gameId=existing&playerId=red"): browser =>
      val opened = ArrayBuffer.empty[String]
      val transport = ScriptedTransport((_, url, _) =>
        Future.successful(Right(TransportResponse(200,
          if url.contains("/events") then """{"events":[]}"""
          else encodedGame(viewer = None)))))
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(transport), navigate = opened += _)
      browser.settle.map { _ =>
        browser.click("restart")
        assertEquals(opened.toVector, Vector("/?mode=server"))
      }

  test("trusted UI reloads after command conflict without retrying or " +
      "changing seat"):
    TestBrowser.use(): browser =>
      val responses = scala.collection.mutable.Queue(
        TransportResponse(200, encodedBlueAct()),
        TransportResponse(409,
          """{"error":"stale-client-position","message":"position changed"}"""),
        TransportResponse(200, encodedGame(nextSequence = 2L)))
      val transport = ScriptedTransport((_, _, _) =>
        Future.successful(Right(responses.dequeue())))
      Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ =>
        browser.click("menu-ending")
        browser.settle
      }.map { _ =>
        assertEquals(transport.requests.map(r => r.method -> r.url).toVector,
          Vector("GET" -> "/games/my%20game/api",
            "POST" -> "/games/my%20game/api/commands",
            "GET" -> "/games/my%20game/api"))
        assert(browser.byClass("seat-identity").head.textContent
          .contains("Blue Exile"))
        assert(browser.text.contains("Waiting for"))
        assert(browser.text.contains("refreshed without retrying"))
        assertEquals(browser.urls, Vector.empty)
      }

  test("trusted polling stops and clears private state when cookie access " +
      "is lost"):
    TestBrowser.use(): browser =>
      var requests = 0
      val transport = ScriptedTransport((_, _, _) =>
        requests += 1
        Future.successful(Right(if requests == 1 then
          TransportResponse(200, encodedGame()) else TransportResponse(403,
            """{"error":"forbidden","message":"denied"}"""))))
      Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
        withEmptyLogPage(transport))
      browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
        assertEquals(requests, 2)
        assert(browser.text.contains("assigned seat link"))
        assertEquals(browser.byClass("wake-actions"), Vector.empty)
        browser.tick()
        assertEquals(requests, 2)
      }

  Vector("changed" -> Some("red"), "absent" -> None,
    "invalid" -> Some("unknown-seat")).foreach { case (label, viewer) =>
    test(s"trusted same-sequence poll rejects $label viewer and disables " +
        "old controls"):
      TestBrowser.use(): browser =>
        // The script reads `requests`: the transport records before it replies.
        val requests = ArrayBuffer.empty[RecordedRequest]
        val transport = ScriptedTransport(
          (_, _, _) => Future.successful(Right(TransportResponse(200,
            if requests.size == 1 then encodedBlueAct()
            else encodedBlueAct(viewer = viewer)))),
          requests)
        Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
          withEmptyLogPage(transport))
        browser.settle.flatMap { _ =>
          // The control on screen before the poll: once the viewer is
          // rejected its handler must submit nothing.
          val oldControl = browser.byClass("menu-ending").head
            .asInstanceOf[js.Dynamic]
          browser.tick()
          browser.settle.map { _ =>
            assert(browser.text.contains("assigned seat link"))
            assertEquals(browser.byClass("wake-actions"), Vector.empty)
            assertEquals(browser.byClass("menu-ending"), Vector.empty)
            oldControl.onclick(js.Dynamic.literal())
            browser.tick()
            assertEquals(requests.map(r => r.method -> r.url).toVector, Vector(
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
        val transport = ScriptedTransport((method, _, _) =>
          if method == "POST" then { commands += 1; pending.future }
          else
            loads += 1
            Future.successful(Right(
              if loads == 1 then TransportResponse(200, encodedBlueAct())
              else if status == 200 then TransportResponse(200,
                encodedBlueAct(viewer = Some("red")))
              else TransportResponse(status,
                """{"error":"forbidden","message":"denied"}"""))))
        Main.start(browser.mount, "/games/my%20game", trustedAlpha = true,
          withEmptyLogPage(transport))
        browser.settle.flatMap { _ =>
          browser.click("menu-ending")
          browser.tick()
          browser.settle
        }.flatMap { _ =>
          pending.success(Right(TransportResponse(200,
            encodedBlueAct(nextSequence = 2L))))
          browser.settle
        }.map { _ =>
          assert(browser.text.contains("assigned seat link"))
          assertEquals(browser.byClass("menu-ending"), Vector.empty)
          browser.tick()
          assertEquals(loads, 2)
          assertEquals(commands, 1)
        }
  }
