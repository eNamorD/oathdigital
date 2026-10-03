package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.frontend.ServerModeFixtures.{forgeParked, quietLog}

import munit.FunSuite
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** A mounted session in the trusted and development modes: which requests it
  * makes, what it shows, and how it reacts to conflicts, lost identity and
  * lost connections.
  */
class ServerModeSessionSuite extends FunSuite:

  test("trusted player renders its fixed seat and never bootstraps switches or loads raw events"):
    val browser = new TestBrowser("?gameId=wrong&playerId=red")
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        requests += ((method, url, body))
        scala.concurrent.Future.successful(Right(TransportResponse(200, trustedProjection)))
    Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, quietLog(transport))
    browser.settle.map { _ =>
      assertEquals(requests.map(r => r._1 -> r._2).toVector,
        Vector("GET" -> "/games/my%20game/api"))
      assert(browser.text.contains("Blue Exile"))
      assert(browser.text.contains("Waiting for"))
      assert(browser.byClass("seat-identity").head.textContent.contains("Blue Exile"))
      assert(browser.byClass("debug-toolbar").isEmpty)
      assert(browser.byClass("player-selector").isEmpty)
      assert(browser.byClass("raw-event-log").isEmpty)
      assert(browser.byClass("restart").isEmpty)
      assert(browser.urls.isEmpty)
    }.andThen { case _ => browser.close() }(using scala.scalajs.concurrent.JSExecutionContext.queue)

  test("trusted unauthorized player displays seat-link recovery without bootstrap"):
    val browser = new TestBrowser
    var requests = 0
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        requests += 1
        scala.concurrent.Future.successful(Right(TransportResponse(401,
          """{"error":"unauthorized","message":"internal detail"}""")))
    Main.start(browser.mount, "/games/missing", trustedAlpha = true, quietLog(transport))
    browser.settle.map { _ =>
      assertEquals(requests, 1)
      assert(browser.text.contains("assigned seat link"))
      assert(!browser.text.contains("internal detail"))
      assert(browser.byClass("restart").isEmpty)
    }.andThen { case _ => browser.close() }(using scala.scalajs.concurrent.JSExecutionContext.queue)

  private def trustedProjection: String =
    """{"gameId":"my game","nextSequence":1,"phase":"awaiting-pawn","activeParticipantId":"red","viewerPlayerId":"blue","players":[{"playerId":"red","displayName":"Red Exile","role":"exile","colorToken":"red"},{"playerId":"blue","displayName":"Blue Exile","role":"exile","colorToken":"blue"}],"world":[],"pawnLocations":[],"legalControls":[],"ready":false,"completed":false}"""

  test("development root retains query loading and raw history"):
    val browser = new TestBrowser("?gameId=existing&playerId=red")
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        requests += method -> url
        val json = if url.contains("/events") then """{"events":[]}"""
          else trustedProjection.replace("\"viewerPlayerId\":\"blue\",", "")
        scala.concurrent.Future.successful(Right(TransportResponse(200, json)))
    Main.start(browser.mount, "/", trustedAlpha = false, quietLog(transport))
    browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
      assertEquals(requests.toVector, Vector("GET" -> "/api/dev/first-games/existing?playerId=red",
        "GET" -> "/api/dev/first-games/existing/events?limit=25",
        "GET" -> "/api/dev/first-games/existing?playerId=red"))
      assert(browser.byClass("debug-toolbar").nonEmpty)
      assert(browser.byClass("player-selector").nonEmpty)
      assert(browser.byClass("raw-event-log").nonEmpty)
      assert(browser.urls.last.contains("gameId=existing&playerId=red"))
      assert(!browser.text.contains("assigned seat link"))
    }.andThen { case _ => browser.close() }

  test("a lost connection shows one notice naming the recovery, not the transport error"):
    val browser = new TestBrowser("?gameId=existing&playerId=red")
    var loads = 0
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        if url.contains("/events") then
          Future.successful(Right(TransportResponse(200, """{"events":[]}""")))
        else
          loads += 1
          if loads == 1 then Future.successful(Right(TransportResponse(200,
            trustedProjection.replace("\"viewerPlayerId\":\"blue\",", ""))))
          else Future.successful(Left(GameClientFailure.NetworkFailure(
            "GET /api/dev/first-games/existing?playerId=red failed")))
    Main.start(browser.mount, "/", trustedAlpha = false, quietLog(transport))
    // The first load lands; the poll the tick fires is the one that fails.
    browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
      assertEquals(loads, 2)
      val notices = browser.byClass("status")
        .filter(_.getAttribute("class").contains("error"))
      assertEquals(notices.map(_.textContent),
        Vector("Connection lost. Reconnect to see the current table before acting."))
      assertEquals(notices.head.getAttribute("role"), "alert")
      assert(browser.byClass("reconnectSession").nonEmpty)
      assert(!browser.text.contains("failed"), browser.text)
    }.andThen { case _ => browser.close() }

  /** Spec, behavior change 1. A development session follows the active
    * player; the render between the seat change and the reload used to show
    * the previous seat's walker drafts, since they were cleared only by the
    * reload's reconcile. The reload here never lands, so the interim frame
    * is what the test sees.
    */
  test("a seat change clears the walker drafts before the interim render"):
    val browser = new TestBrowser("?gameId=g&playerId=red")
    def snapshot(sequence: Long, active: String): String =
      oathdigital.protocol.projection.GameProjectionCodec.encode(
        GameProjection("g", sequence, "act", Some(active),
          Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red),
            GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)),
          Vector.empty, Vector.empty, Vector.empty, ready = false,
          completed = false, walkerDecision = Some(forgeParked)))
    var loads = 0
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        if url.contains("/events") then
          Future.successful(Right(TransportResponse(200, """{"events":[]}""")))
        else
          loads += 1
          loads match
            case 1 => Future.successful(Right(TransportResponse(200, snapshot(1L, "red"))))
            case 2 => Future.successful(Right(TransportResponse(200, snapshot(2L, "blue"))))
            case _ => Future.never
    Main.start(browser.mount, "/", trustedAlpha = false, quietLog(transport))
    browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
      assertEquals(loads, 3)
      // The route still shows Forge's partition heading to the new seat;
      // the zones come from a draft, and there is none.
      assert(browser.byClass("partition-zones").isEmpty, browser.text)
      // Not vacuous: blue's frame did render, with Forge's partition panel.
      assert(browser.text.contains("Development-only active-player view: blue"),
        browser.text)
      assertEquals(browser.byClass("partition-instruction")
        .map(_.textContent), Vector("Assign every option: Pay Favor (2), Pay Secret (1)."))
    }.andThen { case _ => browser.close() }

  /** Spec, verification: the only test that proves the FlowHost wiring. A
    * Recover whose preview offers one modifier renders the ordering panel;
    * toggling and confirming previews again with the invocation, then posts
    * the StartWalker with the handler folded into its own modifiers.
    */
  test("a previewed major action orders its modifier and posts the ordered command"):
    val browser = new TestBrowser("?gameId=g&playerId=red")
    val projection = oathdigital.protocol.projection.GameProjectionCodec.encode(
      GameProjection("g", 1L, "act-action-selection", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
        Vector.empty, Vector.empty, Vector("beginRecover"), ready = true,
        completed = false, actionSelectionOpen = true))
    val preview = """{"nextSequence":1,"action":"recover","modifiers":[{"sourceKey":"adviser:p:denizen:a","handlerId":"h.a","description":"Old Oak"}],"ignoredRules":[],"targets":[]}"""
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        requests += ((method, url, body))
        val json = if url.contains("/events") then """{"events":[]}"""
          else if url.contains("/preview") then preview
          else projection
        Future.successful(Right(TransportResponse(200, json)))
    Main.start(browser.mount, "/", trustedAlpha = false, quietLog(transport))
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
      val posts = requests.filter(_._1 == "POST").toVector
      assertEquals(posts.map(_._2), Vector(
        "/api/dev/first-games/g/preview?playerId=red",
        "/api/dev/first-games/g/preview?playerId=red",
        "/api/dev/first-games/g/commands?playerId=red"))
      assert(posts(1)._3.exists(_.contains("\"handlerId\":\"h.a\"")), posts(1)._3)
      val command = posts(2)._3.get
      assert(command.contains("\"action\":\"recover\""), command)
      assert(command.contains("\"modifiers\":[\"h.a\"]"), command)
      assert(!command.contains("orderedModifiers"), command)
      assert(browser.byClass("modifier-confirm").isEmpty, browser.text)
    }.andThen { case _ => browser.close() }

  test("development new game button returns to the start page"):
    val browser = new TestBrowser("?mode=server&gameId=existing&playerId=red")
    val opened = scala.collection.mutable.ArrayBuffer.empty[String]
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        scala.concurrent.Future.successful(Right(TransportResponse(200,
          if url.contains("/events") then """{"events":[]}"""
          else trustedProjection.replace("\"viewerPlayerId\":\"blue\",", ""))))
    Main.start(browser.mount, "/", trustedAlpha = false, quietLog(transport), navigate = opened += _)
    browser.settle.map { _ =>
      browser.click("restart")
      assertEquals(opened.toVector, Vector("/?mode=server"))
    }.andThen { case _ => browser.close() }

  test("trusted UI reloads after command conflict without retrying or changing seat"):
    val browser = new TestBrowser
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
    val active = trustedProjection.replace("\"activeParticipantId\":\"red\"", "\"activeParticipantId\":\"blue\"")
      .replace("\"awaiting-pawn\"", "\"wake\"").replace("\"legalControls\":[]", "\"legalControls\":[\"endWake\"]")
      .replace("\"ready\":false", "\"ready\":true")
    val responses = scala.collection.mutable.Queue(
      TransportResponse(200, active),
      TransportResponse(409, """{"error":"stale-client-position","message":"position changed"}"""),
      TransportResponse(200, trustedProjection.replace("\"nextSequence\":1", "\"nextSequence\":2")))
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        requests += method -> url
        scala.concurrent.Future.successful(Right(responses.dequeue()))
    Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, quietLog(transport))
    browser.settle.flatMap { _ =>
      browser.click("wake-action")
      browser.settle
    }.map { _ =>
      assertEquals(requests.toVector, Vector("GET" -> "/games/my%20game/api",
        "POST" -> "/games/my%20game/api/commands", "GET" -> "/games/my%20game/api"))
      assert(browser.byClass("seat-identity").head.textContent.contains("Blue Exile"))
      assert(browser.text.contains("Waiting for"))
      assert(browser.text.contains("refreshed without retrying"))
      assert(browser.urls.isEmpty)
    }.andThen { case _ => browser.close() }

  test("trusted polling stops and clears private state when cookie access is lost"):
    val browser = new TestBrowser
    var requests = 0
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        requests += 1
        scala.concurrent.Future.successful(Right(if requests == 1 then
          TransportResponse(200, trustedProjection) else TransportResponse(403,
            """{"error":"forbidden","message":"denied"}""")))
    Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, quietLog(transport))
    browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
      assertEquals(requests, 2)
      assert(browser.text.contains("assigned seat link"))
      assert(browser.byClass("wake-actions").isEmpty)
      browser.tick()
      assertEquals(requests, 2)
    }.andThen { case _ => browser.close() }

  Vector("changed" -> Some("red"), "absent" -> None,
    "invalid" -> Some("unknown-seat")).foreach { case (label, viewer) =>
    test(s"trusted same-sequence poll rejects $label viewer and disables old controls"):
      val browser = new TestBrowser
      val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
      val active = trustedProjection.replace("\"activeParticipantId\":\"red\"", "\"activeParticipantId\":\"blue\"")
        .replace("\"awaiting-pawn\"", "\"wake\"")
        .replace("\"legalControls\":[]", "\"legalControls\":[\"endWake\"]")
        .replace("\"ready\":false", "\"ready\":true")
      val replacement = viewer.fold(active.replace("\"viewerPlayerId\":\"blue\",", "")) { id =>
        active.replace("\"viewerPlayerId\":\"blue\"", s"\"viewerPlayerId\":\"$id\"")
      }
      val transport = new JsonTransport:
        def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
          requests += method -> url
          scala.concurrent.Future.successful(Right(TransportResponse(200,
            if requests.size == 1 then active else replacement)))
      Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, quietLog(transport))
      browser.settle.flatMap { _ =>
        val oldControl = browser.byClass("wake-action").head.asInstanceOf[scala.scalajs.js.Dynamic]
        browser.tick()
        browser.settle.map { _ =>
          assert(browser.text.contains("assigned seat link"))
          assert(browser.byClass("wake-actions").isEmpty)
          assert(browser.byClass("wake-action").isEmpty)
          oldControl.onclick(scala.scalajs.js.Dynamic.literal())
          browser.tick()
          assertEquals(requests.toVector, Vector(
            "GET" -> "/games/my%20game/api", "GET" -> "/games/my%20game/api"))
        }
      }.andThen { case _ => browser.close() }
  }

  Vector(200, 401, 403).foreach { status =>
  test(s"trusted identity loss with HTTP $status rejects a late command response from the previous seat session"):
    val browser = new TestBrowser
    val pending = scala.concurrent.Promise[Either[GameClientFailure, TransportResponse]]()
    val active = trustedProjection.replace("\"activeParticipantId\":\"red\"", "\"activeParticipantId\":\"blue\"")
      .replace("\"awaiting-pawn\"", "\"wake\"")
      .replace("\"legalControls\":[]", "\"legalControls\":[\"endWake\"]")
      .replace("\"ready\":false", "\"ready\":true")
    var loads = 0
    var commands = 0
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]) =
        if method == "POST" then { commands += 1; pending.future }
        else
          loads += 1
          scala.concurrent.Future.successful(Right(if loads == 1 then TransportResponse(200, active)
            else if status == 200 then TransportResponse(200, active.replace("\"viewerPlayerId\":\"blue\"",
              "\"viewerPlayerId\":\"red\""))
            else TransportResponse(status, """{"error":"forbidden","message":"denied"}""")))
    Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, quietLog(transport))
    browser.settle.flatMap { _ =>
      browser.click("wake-action")
      browser.tick()
      browser.settle
    }.flatMap { _ =>
      pending.success(Right(TransportResponse(200,
        active.replace("\"nextSequence\":1", "\"nextSequence\":2"))))
      browser.settle
    }.map { _ =>
      assert(browser.text.contains("assigned seat link"))
      assert(browser.byClass("wake-action").isEmpty)
      browser.tick()
      assertEquals(loads, 2)
      assertEquals(commands, 1)
    }.andThen { case _ => browser.close() }
  }
