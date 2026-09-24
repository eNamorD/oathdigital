package oathdigital.frontend

import oathdigital.model.PlayerColor

import munit.FunSuite
import oathdigital.presentation._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import oathdigital.protocol.{DecisionAnswerWire, DecisionPlacementWire}

class ServerModeUiSuite extends FunSuite {
  test("canonical path decodes only a single game segment") {
    assertEquals(ServerUiSupport.canonicalGameId("/games/my%20game"), Some("my game"))
    assertEquals(ServerUiSupport.canonicalGameId("/"), None)
    assertEquals(ServerUiSupport.canonicalGameId("/games/"), None)
    assertEquals(ServerUiSupport.canonicalGameId("/games/a/api"), None)
    assertEquals(ServerUiSupport.canonicalGameId("/games/%broken"), None)
  }

  test("trusted player renders its fixed seat and never bootstraps switches or loads raw events") {
    val browser = new TestBrowser("?gameId=wrong&playerId=red")
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    val transport = new JsonTransport {
      def request(method: String, url: String, body: Option[String]) = {
        requests += ((method, url, body))
        scala.concurrent.Future.successful(Right(TransportResponse(200, trustedProjection)))
      }
    }
    Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, transport)
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
    }.andThen { case _ => browser.close() }(scala.scalajs.concurrent.JSExecutionContext.queue)
  }

  test("trusted unauthorized player displays seat-link recovery without bootstrap") {
    val browser = new TestBrowser
    var requests = 0
    val transport = new JsonTransport {
      def request(method: String, url: String, body: Option[String]) = {
        requests += 1
        scala.concurrent.Future.successful(Right(TransportResponse(401,
          """{"error":"unauthorized","message":"internal detail"}""")))
      }
    }
    Main.start(browser.mount, "/games/missing", trustedAlpha = true, transport)
    browser.settle.map { _ =>
      assertEquals(requests, 1)
      assert(browser.text.contains("assigned seat link"))
      assert(!browser.text.contains("internal detail"))
      assert(browser.byClass("restart").isEmpty)
    }.andThen { case _ => browser.close() }(scala.scalajs.concurrent.JSExecutionContext.queue)
  }

  private def hostTransport(
      requests: scala.collection.mutable.ArrayBuffer[(String, String, Option[String])],
      status: Int = 201,
      body: String = """{"gameId":"host-game","seats":[{"playerId":"Red","url":"https://oath.test/s/red-code"},{"playerId":"Blue","url":"https://oath.test/s/blue-code"}]}"""
  ): JsonTransport = new JsonTransport {
    def request(method: String, url: String, body0: Option[String]) = {
      requests += ((method, url, body0))
      scala.concurrent.Future.successful(Right(TransportResponse(status, body)))
    }
  }

  private def hostRequests(requests: collection.Seq[(String, String, Option[String])]) =
    requests.map(r => oathdigital.protocol.TrustedGameCreateRequestCodec.decode(r._3.get).toOption.get)

  private def hostColors(browser: TestBrowser): Vector[String] =
    browser.byClass("host-player").map(_.getAttribute("class").split(" ")
      .find(_.startsWith("host-player-")).get.stripPrefix("host-player-"))

  private def menuColors(browser: TestBrowser): Vector[String] =
    browser.byClass("add-player-option").map(_.textContent)

  private def toggle(browser: TestBrowser): org.scalajs.dom.html.Button =
    browser.byClass("add-player-toggle").head.asInstanceOf[org.scalajs.dom.html.Button]

  test("trusted root posts host form and displays ordered copyable seat links") {
    val browser = new TestBrowser("?gameId=ignored&playerId=ignored")
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    Main.start(browser.mount, "/", trustedAlpha = true, hostTransport(requests))
    assertEquals(requests.size, 0)
    assert(!browser.nodes.exists(_.getAttribute("aria-label") == "Game ID"))
    assert(!browser.nodes.exists(_.getAttribute("aria-label") == "First player ID"))
    assertEquals(hostColors(browser), Vector("red", "blue"))
    assertEquals(browser.input("Red player ID").value, "Red")
    assertEquals(browser.input("Blue player ID").value, "Blue")
    browser.click("create-trusted-game")
    browser.settle.map { _ =>
      assertEquals(requests.map(r => r._1 -> r._2).toVector, Vector("POST" -> "/games"))
      val request = hostRequests(requests).head
      assert(request.gameId.startsWith("manual-"), request.gameId)
      assertEquals(request.participants, Vector(
        oathdigital.protocol.BootstrapParticipantRequest("Red", "red-lineage", PlayerColor.Red),
        oathdigital.protocol.BootstrapParticipantRequest("Blue", "blue-lineage", PlayerColor.Blue)))
      assert(browser.text.contains("Game ID: host-game"))
      val links = browser.byClass("seat-link").map(_.asInstanceOf[org.scalajs.dom.html.Input])
      assertEquals(links.map(_.value), Vector("https://oath.test/s/red-code", "https://oath.test/s/blue-code"))
      assert(links.forall(_.readOnly))
      assertEquals(browser.byClass("copy-seat-link").size, 2)
      assert(browser.urls.isEmpty)
      browser.click("copy-seat-link")
      assertEquals(browser.copied, Vector("https://oath.test/s/red-code"))
    }.andThen { case _ => browser.close() }(scala.scalajs.concurrent.JSExecutionContext.queue)
  }

  test("host add-player menu offers untaken colors in order and stops at six players") {
    val browser = new TestBrowser
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    Main.start(browser.mount, "/", trustedAlpha = true, hostTransport(requests))
    assert(browser.byClass("add-player-menu").head.hasAttribute("hidden"))
    browser.click("add-player-toggle")
    assertEquals(toggle(browser).getAttribute("aria-expanded"), "true")
    assertEquals(menuColors(browser), Vector("Yellow", "White", "Black", "Pink", "Brown"))
    browser.click("add-player-pink")
    assert(browser.byClass("add-player-menu").head.hasAttribute("hidden"))
    assertEquals(hostColors(browser), Vector("red", "blue", "pink"))
    Vector("add-player-white", "add-player-brown", "add-player-black").foreach { option =>
      browser.click("add-player-toggle"); browser.click(option)
    }
    assertEquals(hostColors(browser), Vector("red", "blue", "pink", "white", "brown", "black"))
    assert(toggle(browser).disabled)
    assert(browser.text.contains("Maximum 6 players"))
    browser.click("remove-player-white")
    assertEquals(hostColors(browser), Vector("red", "blue", "pink", "brown", "black"))
    assert(!toggle(browser).disabled)
    assert(!browser.text.contains("Maximum 6 players"))
    browser.click("add-player-toggle")
    assertEquals(menuColors(browser), Vector("Yellow", "White"))
    browser.click("create-trusted-game")
    browser.settle.map { _ =>
      assertEquals(hostRequests(requests).head.participants.map(p => p.playerId -> p.lineageId),
        Vector("Red" -> "red-lineage", "Blue" -> "blue-lineage", "Pink" -> "pink-lineage",
          "Brown" -> "brown-lineage", "Black" -> "black-lineage"))
    }.andThen { case _ => browser.close() }
  }

  test("host form blocks fewer than two players, invalid IDs and duplicate IDs before posting") {
    val browser = new TestBrowser
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    Main.start(browser.mount, "/", trustedAlpha = true, hostTransport(requests))
    browser.click("remove-player-red")
    browser.click("create-trusted-game")
    assert(browser.text.contains("Need at least 2 players."))
    browser.click("remove-player-blue")
    assertEquals(hostColors(browser), Vector.empty)
    browser.click("add-player-toggle"); browser.click("add-player-yellow")
    browser.click("add-player-toggle"); browser.click("add-player-red")
    assertEquals(hostColors(browser), Vector("yellow", "red"))
    browser.input("Yellow player ID").value = "bad id"
    browser.click("create-trusted-game")
    assert(browser.text.contains("Yellow player ID must start with a letter or digit"))
    browser.input("Yellow player ID").value = "Alex"
    browser.input("Red player ID").value = " Alex "
    browser.click("create-trusted-game")
    assert(browser.text.contains("Player ID \"Alex\" is used twice."))
    browser.input("Red player ID").value = "Sam"
    browser.click("create-trusted-game")
    browser.settle.map { _ =>
      assertEquals(requests.size, 1)
      assertEquals(hostRequests(requests).head.participants.map(p => p.playerId -> p.color),
        Vector("Alex" -> PlayerColor.Yellow, "Sam" -> PlayerColor.Red))
    }.andThen { case _ => browser.close() }
  }

  test("host colors map to their own player badge tokens") {
    assertEquals(TrustedHostUi.LineageColors.map(PlayerColorCss.of),
      Vector("player-red", "player-blue", "player-yellow", "player-white", "player-black",
        "player-pink", "player-brown"))
    assertEquals(PlayerColorCss.of(None), "player-neutral")
  }

  private def trustedProjection: String =
    """{"gameId":"my game","nextSequence":1,"phase":"awaiting-pawn","activeParticipantId":"red","viewerPlayerId":"blue","players":[{"playerId":"red","displayName":"Red Exile","role":"exile","colorToken":"red"},{"playerId":"blue","displayName":"Blue Exile","role":"exile","colorToken":"blue"}],"world":[],"pawnLocations":[],"legalControls":[],"ready":false,"completed":false}"""

  test("development root retains query loading and raw history") {
    val browser = new TestBrowser("?gameId=existing&playerId=red")
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
    val transport = new JsonTransport {
      def request(method: String, url: String, body: Option[String]) = {
        requests += method -> url
        val json = if (url.contains("/events")) """{"events":[]}"""
          else trustedProjection.replace("\"viewerPlayerId\":\"blue\",", "")
        scala.concurrent.Future.successful(Right(TransportResponse(200, json)))
      }
    }
    Main.start(browser.mount, "/", trustedAlpha = false, transport)
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
  }

  test("host duplicate game response keeps form editable and retries with a new game ID") {
    val browser = new TestBrowser
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    Main.start(browser.mount, "/", trustedAlpha = true, hostTransport(requests, 409,
      """{"error":"game-already-exists","message":"Game already exists"}"""))
    browser.click("create-trusted-game")
    browser.settle.flatMap { _ =>
      assert(browser.text.contains("A new one was generated"))
      browser.click("create-trusted-game")
      browser.settle
    }.map { _ =>
      val ids = hostRequests(requests).map(_.gameId)
      assertEquals(ids.size, 2)
      assertNotEquals(ids(0), ids(1))
      assert(!browser.text.contains("refreshed"))
      assert(!browser.byClass("create-trusted-game").head.asInstanceOf[org.scalajs.dom.html.Button].disabled)
      assert(browser.byClass("seat-link").isEmpty)
    }.andThen { case _ => browser.close() }
  }

  test("development root without a game shows the start page and opens the created game") {
    val browser = new TestBrowser("?mode=server")
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    val opened = scala.collection.mutable.ArrayBuffer.empty[String]
    Main.start(browser.mount, "/", trustedAlpha = false, hostTransport(requests),
      navigate = opened += _)
    assertEquals(requests.size, 0)
    assertEquals(hostColors(browser), Vector("red", "blue"))
    assert(!browser.text.contains("assigned link"))
    browser.click("create-trusted-game")
    browser.settle.map { _ =>
      assertEquals(requests.map(r => r._1 -> r._2).toVector, Vector("POST" -> "/games"))
      assertEquals(hostRequests(requests).head.participants.map(_.color),
        Vector(PlayerColor.Red, PlayerColor.Blue))
      assertEquals(opened.toVector, Vector("/?mode=server&gameId=host-game&playerId=Red"))
      assert(browser.byClass("seat-link").isEmpty)
    }.andThen { case _ => browser.close() }
  }

  test("development new game button returns to the start page") {
    val browser = new TestBrowser("?mode=server&gameId=existing&playerId=red")
    val opened = scala.collection.mutable.ArrayBuffer.empty[String]
    val transport = new JsonTransport {
      def request(method: String, url: String, body: Option[String]) =
        scala.concurrent.Future.successful(Right(TransportResponse(200,
          if (url.contains("/events")) """{"events":[]}"""
          else trustedProjection.replace("\"viewerPlayerId\":\"blue\",", ""))))
    }
    Main.start(browser.mount, "/", trustedAlpha = false, transport, navigate = opened += _)
    browser.settle.map { _ =>
      browser.click("restart")
      assertEquals(opened.toVector, Vector("/?mode=server"))
    }.andThen { case _ => browser.close() }
  }

  test("trusted UI reloads after command conflict without retrying or changing seat") {
    val browser = new TestBrowser
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
    val active = trustedProjection.replace("\"activeParticipantId\":\"red\"", "\"activeParticipantId\":\"blue\"")
      .replace("\"awaiting-pawn\"", "\"wake\"").replace("\"legalControls\":[]", "\"legalControls\":[\"endWake\"]")
      .replace("\"ready\":false", "\"ready\":true")
    val responses = scala.collection.mutable.Queue(
      TransportResponse(200, active),
      TransportResponse(409, """{"error":"stale-client-position","message":"position changed"}"""),
      TransportResponse(200, trustedProjection.replace("\"nextSequence\":1", "\"nextSequence\":2")))
    val transport = new JsonTransport {
      def request(method: String, url: String, body: Option[String]) = {
        requests += method -> url
        scala.concurrent.Future.successful(Right(responses.dequeue()))
      }
    }
    Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, transport)
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
  }

  test("trusted polling stops and clears private state when cookie access is lost") {
    val browser = new TestBrowser
    var requests = 0
    val transport = new JsonTransport {
      def request(method: String, url: String, body: Option[String]) = {
        requests += 1
        scala.concurrent.Future.successful(Right(if (requests == 1)
          TransportResponse(200, trustedProjection) else TransportResponse(403,
            """{"error":"forbidden","message":"denied"}""")))
      }
    }
    Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, transport)
    browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
      assertEquals(requests, 2)
      assert(browser.text.contains("assigned seat link"))
      assert(browser.byClass("wake-actions").isEmpty)
      browser.tick()
      assertEquals(requests, 2)
    }.andThen { case _ => browser.close() }
  }

  Vector("changed" -> Some("red"), "absent" -> None,
    "invalid" -> Some("unknown-seat")).foreach { case (label, viewer) =>
    test(s"trusted same-sequence poll rejects $label viewer and disables old controls") {
      val browser = new TestBrowser
      val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
      val active = trustedProjection.replace("\"activeParticipantId\":\"red\"", "\"activeParticipantId\":\"blue\"")
        .replace("\"awaiting-pawn\"", "\"wake\"")
        .replace("\"legalControls\":[]", "\"legalControls\":[\"endWake\"]")
        .replace("\"ready\":false", "\"ready\":true")
      val replacement = viewer.fold(active.replace("\"viewerPlayerId\":\"blue\",", "")) { id =>
        active.replace("\"viewerPlayerId\":\"blue\"", s"\"viewerPlayerId\":\"$id\"")
      }
      val transport = new JsonTransport {
        def request(method: String, url: String, body: Option[String]) = {
          requests += method -> url
          scala.concurrent.Future.successful(Right(TransportResponse(200,
            if (requests.size == 1) active else replacement)))
        }
      }
      Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, transport)
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
  }

  Vector(200, 401, 403).foreach { status =>
  test(s"trusted identity loss with HTTP $status rejects a late command response from the previous seat session") {
    val browser = new TestBrowser
    val pending = scala.concurrent.Promise[Either[GameClientFailure, TransportResponse]]()
    val active = trustedProjection.replace("\"activeParticipantId\":\"red\"", "\"activeParticipantId\":\"blue\"")
      .replace("\"awaiting-pawn\"", "\"wake\"")
      .replace("\"legalControls\":[]", "\"legalControls\":[\"endWake\"]")
      .replace("\"ready\":false", "\"ready\":true")
    var loads = 0
    var commands = 0
    val transport = new JsonTransport {
      def request(method: String, url: String, body: Option[String]) =
        if (method == "POST") { commands += 1; pending.future }
        else {
          loads += 1
          scala.concurrent.Future.successful(Right(if (loads == 1) TransportResponse(200, active)
            else if (status == 200) TransportResponse(200, active.replace("\"viewerPlayerId\":\"blue\"",
              "\"viewerPlayerId\":\"red\""))
            else TransportResponse(status, """{"error":"forbidden","message":"denied"}""")))
        }
    }
    Main.start(browser.mount, "/games/my%20game", trustedAlpha = true, transport)
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
  }

  test("secret summaries lead with available over total and explain unavailable tokens") {
    assertEquals(ServerUiSupport.secretSummaryLabel(1, 1, 0, 0),
      "1 available of 1 owned; 0 facedown and 0 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(0, 1, 0, 1),
      "0 available of 1 owned; 0 facedown and 1 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(0, 1, 1, 0),
      "0 available of 1 owned; 1 facedown and 0 committed")
    assertEquals(ServerUiSupport.secretSummaryLabel(1, 2, 0, 1),
      "1 available of 2 owned; 0 facedown and 1 committed")
  }
  test("the roll outcome summary reads the accumulated dice, the score and " +
      "what it is measured against") {
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState("recover", Vector("blank", "blank"), 0, Some(4))),
      "Rolled blank, blank -- 0 shields so far (need 4).")
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState("recover", Vector("two-shields", "doubler"), 4,
        Some(4))),
      "Rolled two-shields, doubler -- 4 shields so far (need 4).")
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState("campaign.attack",
        Vector("two-swords-skull", "one-sword"), 3, None,
        Vector("1 skull loss"))),
      "Rolled two-swords-skull, one-sword -- Attack 3, 1 skull loss.")
  }

  /** Task 5: Forge is driven end to end through the shared two-zone
    * interaction. The sections carrying their own labels and minima, the
    * denizen options carrying their own references, and the answer is
    * assembled by generic code -- nothing below states Forge's printed
    * cost, and nothing names a resource.
    */
  private val forgeQuery = DecisionQueryState("partition",
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")),
    Vector(DecisionSectionState("pay-favor", "Pay Favor", 2),
      DecisionSectionState("pay-secret", "Pay Secret", 1)))

  private val forgeParked = WalkerDecisionState("forge", "forge-9", "decide",
    query = Some(forgeQuery))

  private def forgeItem(index: Int): String =
    WalkerPartitionDraft.itemId(forgeQuery.options(index))

  test("Forge is answered by moving projected options between projected " +
      "sections") {
    val context = BoardSelectionContext("game", "red", 9)
    val initial = WalkerPartitionDraft.reconcile(None, context,
      Some(forgeParked)).get
    // The opening draft fills each section to its projected minimum, in
    // declared order.
    assertEquals(initial.optionsIn("pay-favor").map(_.label),
      Vector("Denizen 1", "Denizen 2"))
    assertEquals(initial.optionsIn("pay-secret").map(_.label),
      Vector("Denizen 3"))
    assert(initial.canConfirm)
    // A confirmed draft answers the decision as one placement per offered
    // option, naming the option's own kind and id.
    assertEquals(initial.command("red"), Some(GameCommand.ResolveWalker(
      "red", "forge-9", DecisionAnswerWire.PartitionWire(
        Vector("pay-favor", "pay-favor", "pay-secret").zipWithIndex.map {
          case (sectionKey, index) =>
            val option = forgeQuery.options(index)
            DecisionPlacementWire(option.kind, option.id, sectionKey) }))))
    // Dragging the third option into the favor zone leaves the secret zone
    // below its projected minimum, so confirmation is refused.
    val invalid = initial.move(forgeItem(2), "pay-favor")
    assert(!invalid.canConfirm)
    assertEquals(invalid.command("red"), None)
    val repaired = invalid.move(forgeItem(0), "pay-secret")
    assert(repaired.canConfirm)
    assertEquals(repaired.optionsIn("pay-favor").map(_.label),
      Vector("Denizen 2", "Denizen 3"))
    assertEquals(repaired.optionsIn("pay-secret").map(_.label),
      Vector("Denizen 1"))
    // A section the query never declared is ignored rather than recorded.
    assertEquals(repaired.move(forgeItem(0), "pay-nothing"), repaired)
  }

  test("a Forge draft is dropped whenever the question changes") {
    val context = BoardSelectionContext("game", "red", 9)
    val initial = WalkerPartitionDraft.reconcile(None, context,
      Some(forgeParked)).get
    val moved = initial.move(forgeItem(2), "pay-favor")
      .move(forgeItem(0), "pay-secret")
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked)), Some(moved))
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved),
      context.copy(sequence = 10), Some(forgeParked)).get.partition,
      initial.partition)
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked.copy(decisionId = "forge-new"))).get.partition,
      initial.partition)
    // A power that changes the option set asks a different question, so the
    // draft assembled against the old one is dropped.
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked.copy(query = Some(forgeQuery.copy(
        options = forgeQuery.options.drop(1)))))).get
        .optionsIn("pay-favor").map(_.label),
      Vector("Denizen 2", "Denizen 3"))
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context, None),
      None)
  }

  test("a parked walker decision that is not a partition drives no draft") {
    val context = BoardSelectionContext("game", "red", 9)
    // A choose-one park, and a park whose query was suppressed because an
    // option could not be presented: neither is an answerable partition.
    assertEquals(WalkerPartitionDraft.reconcile(None, context, Some(
      WalkerDecisionState("recover", "recover.choice", "decide",
        query = Some(DecisionQueryState("choose-one", Vector(
          DecisionOptionState("button", "stop", "Stop"))))))), None)
    assertEquals(WalkerPartitionDraft.reconcile(None, context,
      Some(forgeParked.copy(query = None))), None)
  }

  test("banner and Challenge action labels are presentable") {
    assertEquals(ServerUiSupport.actionLabel("challenge"), "Challenge")
    assertEquals(ServerUiSupport.actionLabel("peoples-favor"), "People's Favor")
  }

  test("facedown adviser draft supports zero one and multiple choices and only faceup outcomes") {
    val context = BoardSelectionContext("game", "red", 7)
    val card = CardDetails("D1", "denizen", "The Adviser")
    val other = CardDetails("D2", "denizen", "The Other Adviser")
    val replacement = CardDetails("D3", "denizen", "Old Denizen")
    val placements = Vector(MinorAdviserPlacement("discard"),
      MinorAdviserPlacement("play-adviser"),
      MinorAdviserPlacement("play-site", Some(replacement)))
    def minor(advisers: Vector[MinorAdviser]) = MinorActionsState(advisers,
      canPeekSiteRelics = false, Vector.empty, Some("site:a"), 0, 0)
    assertEquals(FacedownAdviserDraft.initial(context, minor(Vector.empty)), None)
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(minor(Vector.empty)), 0)
    val one = FacedownAdviserDraft.initial(context,
      minor(Vector(MinorAdviser(card, placements)))).get
    assertEquals(one.selectedCardId, Some("D1"))
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      minor(Vector(MinorAdviser(card, placements)))), 1)
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      minor(Vector(MinorAdviser(card, Vector.empty)))), 1)
    assert(FacedownAdviserDraft.initial(context,
      minor(Vector(MinorAdviser(card, Vector.empty)))).nonEmpty)
    assertEquals(one.command, Some(oathdigital.protocol.GameIntent.StartWalker(
      "play-facedown-adviser", Vector.empty,
      Vector(oathdigital.protocol.WalkerStartArgWire("denizen", "D1")))))
    val many = FacedownAdviserDraft.initial(context, minor(Vector(
      MinorAdviser(card, placements), MinorAdviser(other, placements)))).get
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(minor(Vector(
      MinorAdviser(card, placements), MinorAdviser(other, placements)))), 1)
    assertEquals(many.selected, None)
    assertEquals(many.choose("D2").selected.map(_.card.cardId), Some("D2"))
    assertEquals(FacedownAdviserDraft.reconcile(Some(many.choose("D2")),
      context, Some(minor(Vector(MinorAdviser(card, placements),
        MinorAdviser(other, placements))))).flatMap(_.selected).map(_.card.cardId),
      Some("D2"))
    assertEquals(FacedownAdviserDraft.reconcile(Some(many.choose("D2")),
      context.copy(sequence = 8), Some(minor(Vector(
        MinorAdviser(card, placements), MinorAdviser(other, placements))))), None)
  }

  test("selection copy exposes details and non-color cardinality instructions") {
    val single = BoardTargetAction("travel", "Travel", 1, 1, false,
      Vector(BoardTargetCandidate(BoardTargetRef.Site("a"), "A", Vector.empty)))
    val confirmed = single.copy(explicitConfirm = true)
    assert(ServerUiSupport.cardinalityInstruction(single).contains("immediately"))
    assertEquals(ServerUiSupport.cardinalityInstruction(confirmed),
      "Choose one target, then confirm.")
    assertEquals(ServerUiSupport.cardinalityInstruction(single.copy(
      actionKind = "travel", minimum = 0, maximum = 0)),
      "No target is available; confirm to play this action.")
  }

  test("a parked walker roll classifies as a Roll control carrying the projected pool") {
    val roll = WalkerDecisionState("recover", "walker.recover.roll", "roll",
      pool = Some("recover"), count = Some(2))
    assertEquals(WalkerPanelSupport.recoverWalkerStep(roll),
      Some(WalkerPanelSupport.RecoverWalkerStep.Roll("recover")))
    // No die faces ride the command -- only the projected pool key does.
    assertEquals(GameCommand.RollWalker("red", "recover"),
      oathdigital.protocol.GameIntent.RollWalker("recover"))
  }

  test("a roll park with no projected pool renders no control rather than guessing one") {
    assertEquals(WalkerPanelSupport.recoverWalkerStep(
      WalkerDecisionState("recover", "walker.recover.roll", "roll")), None)
  }

  /** Task 4: both decide parks take their option set from the projected
    * query, and one generic command builder serves both -- a projected
    * option already carries the `kind`/`id` pair a `ChooseOneWire` needs,
    * so the client never has to know which variant it is holding.
    */
  test("the parked Recover choice decision resolves its projected button " +
      "options against its own decision id, distinct from the relic park " +
      "sharing its \"decide\" kind") {
    val continueOption = DecisionOptionState("button", "continue", "Continue")
    val stopOption = DecisionOptionState("button", "stop", "Stop")
    val choiceQuery = DecisionQueryState("choose-one",
      Vector(continueOption, stopOption), heading = Some("Recover"))
    val choice = WalkerDecisionState("recover", "recover.choice", "decide",
      query = Some(choiceQuery))
    // Task 5b: the step carries the whole query, not just its options, so
    // the panel reads the heading the action declared from the same place
    // it reads what to offer.
    assertEquals(WalkerPanelSupport.recoverWalkerStep(choice),
      Some(WalkerPanelSupport.RecoverWalkerStep.Choice(choiceQuery)))
    assertEquals(
      WalkerPanelSupport.resolveChooseOneCommand(choice, continueOption),
      GameCommand.ResolveWalker("red", "recover.choice",
        DecisionAnswerWire.ChooseOneWire("button", "continue")))
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(choice, stopOption),
      GameCommand.ResolveWalker("red", "recover.choice",
        DecisionAnswerWire.ChooseOneWire("button", "stop")))
    // A power that drops an option drops the control with it: the step
    // carries whatever the projection offered, never a fixed pair.
    val stopOnly = DecisionQueryState("choose-one", Vector(stopOption),
      heading = Some("Recover"))
    assertEquals(WalkerPanelSupport.recoverWalkerStep(
      choice.copy(query = Some(stopOnly))),
      Some(WalkerPanelSupport.RecoverWalkerStep.Choice(stopOnly)))
  }

  test("the parked Recover relic decision offers one control per projected " +
      "option, never a preselected relic") {
    val bronze = DecisionOptionState("relic", "relic-1", "Bronze Idol",
      Some(CardDetails("relic-1", "relic", "Bronze Idol")))
    val silver = DecisionOptionState("relic", "relic-2", "Silver Idol",
      Some(CardDetails("relic-2", "relic", "Silver Idol")))
    val relicQuery = DecisionQueryState("choose-one", Vector(bronze, silver),
      heading = Some("Take a relic"))
    val relic = WalkerDecisionState("recover", "recover.relic", "decide",
      query = Some(relicQuery))
    assertEquals(WalkerPanelSupport.recoverWalkerStep(relic),
      Some(WalkerPanelSupport.RecoverWalkerStep.Relic(relicQuery)))
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(relic, bronze),
      GameCommand.ResolveWalker("red", "recover.relic",
        DecisionAnswerWire.ChooseOneWire("relic", "relic-1")))
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(relic, silver),
      GameCommand.ResolveWalker("red", "recover.relic",
        DecisionAnswerWire.ChooseOneWire("relic", "relic-2")))
  }

  test("a decide park whose query was suppressed renders no control, since " +
      "there is no answer the client could safely build") {
    Vector("recover.choice", "recover.relic").foreach(decisionId =>
      assertEquals(WalkerPanelSupport.recoverWalkerStep(
        WalkerDecisionState("recover", decisionId, "decide")), None,
        s"$decisionId must render nothing without a projected query"))
  }

  test("an unrecognized parked walker decision renders no Recover control") {
    assertEquals(WalkerPanelSupport.recoverWalkerStep(
      WalkerDecisionState("recover", "some.other.decision", "decide")), None)
  }

  test("a parked decision for a walker procedure other than Recover renders no " +
      "Recover control, even if it happens to reuse a Recover-shaped kind") {
    assertEquals(WalkerPanelSupport.recoverWalkerStep(
      WalkerDecisionState("teleport", "walker.recover.roll", "roll",
        pool = Some("recover"))), None)
  }

  test("site forces retain accessible labels counts and stable color classes") {
    val cases = Vector(
      SiteForces.Exile(2, "red-exile", PlayerColor.Red,
        "Red Warbands") -> ("Red Warbands x2", "force-red"),
      SiteForces.Exile(1, "blue-exile", PlayerColor.Blue,
        "Blue Warbands") -> ("Blue Warbands x1", "force-blue"),
      SiteForces.Imperial(1, "Imperial Warbands") -> ("Imperial Warbands x1", "force-empire"),
      SiteForces.Bandit(3, "Bandit Warbands") -> ("Bandit Warbands x3", "force-bandit")
    )
    cases.foreach { case (forces, (label, cssClass)) =>
      assertEquals(ServerUiSupport.forceText(forces), label)
      assertEquals(ServerUiSupport.forceCssClass(forces), cssClass)
    }
    assertEquals(GameSite("empty", "Empty", 0, 0, 0, 0, Vector.empty,
      GameSiteRelics(0)).forces, None)
  }

  test("Take Wealth actions use the active-player labels and commands") {
    val actions = ServerUiSupport.takeWealthActions(
      projection(Set("takeFavor", "takeSecret", "endWake")),
      "red-exile"
    )

    assertEquals(
      actions.map(_.label),
      Vector("Take Wealth: 1 favor", "Take Wealth: 1 secret")
    )
    assertEquals(
      actions.map(_.command),
      Vector(
        GameCommand.TakeWealth("red-exile", "favor"),
        GameCommand.TakeWealth("red-exile", "secret")
      )
    )
  }

  test("unavailable Take Wealth actions are absent") {
    assertEquals(
      ServerUiSupport.takeWealthActions(
        projection(Set("endWake")),
        "red-exile"
      ),
      Vector.empty
    )
  }

  test("blocked Take Wealth resource is absent while the legal one remains") {
    val actions = ServerUiSupport.takeWealthActions(
      projection(Set("takeSecret", "endWake")),
      "red-exile"
    )

    assertEquals(actions.map(_.label), Vector("Take Wealth: 1 secret"))
  }

  test("already-used Take Wealth actions are absent from a later phase") {
    assertEquals(
      ServerUiSupport.takeWealthActions(
        projection(Set("takeFavor", "takeSecret"), phase = "act-action-selection"),
        "red-exile"
      ),
      Vector.empty
    )
  }

  test("inactive Wake viewer waits and receives no gameplay controls") {
    val value = projection(
      Set("takeFavor", "takeSecret", "endWake"),
      activeParticipantId = "red-exile"
    )
    val presentation = ServerUiSupport.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForPlayerId, Some("red-exile"))
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
    assertEquals(
      ServerUiSupport.takeWealthActions(value, "blue-exile"),
      Vector.empty
    )
  }

  /** Fix round 1: a parked walker `Decide`'s owner is not always the active
    * participant (Task 5). `walkerDecision` is projected to the owner alone
    * regardless of whose turn it is, so `viewerPresentation` must grant
    * controls off that field directly rather than off `activeParticipantId`
    * -- the bug this guards against left the owner and the active player
    * each waiting on the other.
    */
  test("an off-turn owner of a parked walker decision keeps gameplay controls") {
    val value = projection(Set.empty, activeParticipantId = "red-exile")
      .copy(walkerDecision = Some(forgeParked))
    val owner = ServerUiSupport.viewerPresentation(value, "blue-exile")
    assert(owner.showGameplayControls)
    assertEquals(owner.waitingForPlayerId, None)
    assertEquals(owner.waitingForDisplayName, None)
  }

  test("the active participant waits for the walker decision's off-turn owner") {
    val value = projection(Set.empty, activeParticipantId = "red-exile")
      .copy(walkerWaiting = Some(WalkerWaitingState("blue-exile",
        Some("Choose the Oathkeeper"))))
    val active = ServerUiSupport.viewerPresentation(value, "red-exile")
    assert(!active.showGameplayControls)
    assertEquals(active.waitingForPlayerId, Some("blue-exile"))
    assertEquals(active.waitingForDisplayName, Some("Blue Exile"))
  }

  test("inactive Act viewer sees no action-selection controls") {
    val value = projection(Set("beginRest"), phase = "act-action-selection")
      .copy(actionSelectionOpen = true,
        legalSearchSources = Vector(LegalSearchSource("world", None, 2)),
        legalTravelDestinations = Vector(LegalTravelDestination("site:1", 2)))
    val inactive = ServerUiSupport.viewerPresentation(value, "blue-exile")
    val active = ServerUiSupport.viewerPresentation(value, "red-exile")

    assertEquals(inactive.waitingForPlayerId, Some("red-exile"))
    assert(!ServerUiSupport.showActActionControls(value, inactive))
    assert(ServerUiSupport.showActActionControls(value, active))
  }

  test("inactive setup viewer waits without pawn or private adviser controls") {
    val value = projection(
      Set.empty,
      phase = "setup-walker-decision",
      activeParticipantId = "red-exile",
      ready = false
    )

    val presentation = ServerUiSupport.viewerPresentation(value, "blue-exile")

    assertEquals(presentation.showGameplayControls, false)
    assertEquals(presentation.waitingForDisplayName, Some("Red Exile"))
  }

  test("active viewer retains Wake and setup gameplay controls") {
    val wake = projection(Set("takeFavor", "endWake"))
    assert(ServerUiSupport.viewerPresentation(
      wake,
      "red-exile"
    ).showGameplayControls)
    assertEquals(
      ServerUiSupport.takeWealthActions(wake, "red-exile").map(_.label),
      Vector("Take Wealth: 1 favor")
    )

    val setup = projection(
      Set.empty,
      phase = "setup-walker-decision",
      ready = false
    )
    assert(ServerUiSupport.viewerPresentation(
      setup,
      "red-exile"
    ).showGameplayControls)
  }

  /** Regression: the owner of a parked walker decision has
    * `waitingForPlayerId = None` (Task 5, tested above), but the status line
    * matched on that field alone, so this viewer fell through every named
    * phase case straight to the `activeParticipantId` debug fallback --
    * showing "setup-walker-decision; active participant: <someone else>"
    * to the very player who needs to act. Assert the decision's own heading
    * renders instead, for both the owner and an off-turn viewer.
    */
  test("a parked walker decision's owner sees its heading, not the debug fallback") {
    val site = DecisionOptionState("site", "site:ancient-city", "Ancient City")
    val pawnQuery = DecisionQueryState("choose-one", Vector(site),
      heading = Some("Choose your starting site"))
    val pawnDecision = WalkerDecisionState("setup", "setup.pawn-placement.blue-exile",
      "decide", query = Some(pawnQuery))
    val value = projection(
      Set.empty,
      phase = "setup-walker-decision",
      activeParticipantId = "red-exile",
      ready = false
    ).copy(walkerDecision = Some(pawnDecision))

    val ownerStatus = ActionDecisionRenderer.status(value, new RecordingView("game-1", "blue-exile"))
    assertEquals(ownerStatus.textContent, "Choose your starting site")
    assert(!ownerStatus.textContent.contains("active participant"))
  }

  test("a parked walker decision without a heading still avoids the debug fallback") {
    val vote = DecisionOptionState("button", "yes", "Yes")
    val query = DecisionQueryState("choose-one", Vector(vote))
    val decision = WalkerDecisionState("setup", "setup.some-decision.blue-exile",
      "decide", query = Some(query))
    val value = projection(
      Set.empty,
      phase = "setup-walker-decision",
      activeParticipantId = "red-exile",
      ready = false
    ).copy(walkerDecision = Some(decision))

    val ownerStatus = ActionDecisionRenderer.status(value, new RecordingView("game-1", "blue-exile"))
    assertEquals(ownerStatus.textContent, "Your decision.")
  }

  test("board target classes distinguish candidate selected and read-only state") {
    assertEquals(ServerUiSupport.siteTargetClasses(false, false),
      "site site-readonly")
    assertEquals(ServerUiSupport.siteTargetClasses(true, false),
      "site board-target")
    assertEquals(ServerUiSupport.siteTargetClasses(true, true),
      "site board-target board-target-selected")
  }

  test("round tracker geometry is eight circular ring wedges") {
    val segments = (1 to 8).map(WorldBoardRenderer.roundSegment)
    assertEquals(segments.map(_.path).distinct.size, 8)
    segments.foreach { segment =>
      assert(segment.path.startsWith("M "))
      assertEquals(" A ".r.findAllIn(segment.path).length, 2)
      assert(segment.path.contains(" L "))
      assert(segment.path.endsWith(" Z"))
      assert(segment.labelX >= 30.0 && segment.labelX <= 170.0)
      assert(segment.labelY >= 30.0 && segment.labelY <= 170.0)
    }
    val limiter = segments(3)
    val markerRadius = Math.hypot(limiter.markerX - 100.0, limiter.markerY - 100.0)
    val labelRadius = Math.hypot(limiter.labelX - 100.0, limiter.labelY - 100.0)
    assert(markerRadius > labelRadius)
  }

  test("winner banner class resolves the winner's stable color token") {
    assertEquals(ServerUiSupport.winnerColorClass(
      projection(Set.empty), "red-exile"), "player-red")
    assertEquals(ServerUiSupport.winnerColorClass(
      projection(Set.empty), "missing"), "player-neutral")
  }

  test("visible target detail badges contain details without duplicating names") {
    val candidate = BoardTargetCandidate(BoardTargetRef.Site("site:a"),
      "Ancient City", Vector("2 Supply", "+1 warband"))
    val badge = ServerUiSupport.candidateDetailText(candidate)
    assertEquals(badge, Some("2 Supply · +1 warband"))
    assert(!badge.get.contains(candidate.label))
    assertEquals(ServerUiSupport.candidateDetailText(candidate.copy(details = Vector.empty)), None)
  }

  test("populated site details render properties, stable IDs, and hidden relics") {
    val site = GameSite(
      "site:woods",
      "Woods",
      looseFavor = 2,
      looseSecrets = 1,
      denizenCapacity = 3,
      relicCapacity = 2,
      denizens = Vector(
        GameSiteCard("denizen:fox", "Fox"),
        GameSiteCard("denizen:owl", "Owl")
      ),
      relics = GameSiteRelics(2)
    )
    val details = SiteCardPresentation.from(site)

    assertEquals((details.looseFavor, details.looseSecrets, details.defense),
      (2, 1, 0))
    assertEquals(site.denizens.map(_.label), Vector("Fox", "Owl"))
    assertEquals(site.denizens.map(_.denizenId),
      Vector("denizen:fox", "denizen:owl"))
    assertEquals(details.unknownRelicCount, 2)
  }

  test("peeked site relics replace opaque slots only for the scoped viewer") {
    val known = CardDetails("R1", "relic", "Ancient Crown", rulesText = Some("Rule"))
    val owner = SiteCardPresentation.from(GameSite("site", "Site", 0, 0, 0, 2,
      Vector.empty, GameSiteRelics(2, Vector(known))))
    val other = SiteCardPresentation.from(GameSite("site", "Site", 0, 0, 0, 2,
      Vector.empty, GameSiteRelics(2)))
    assertEquals(owner.unknownRelicCount, 1)
    assertEquals(other.unknownRelicCount, 2)
    assertEquals(owner.peekedRelics.map(_.card.name), Vector("Ancient Crown"))
    assertEquals(other.peekedRelics, Vector.empty)
  }

  test("empty site details have image-independent empty states") {
    val details = SiteCardPresentation.from(GameSite(
      "site:empty",
      "Empty",
      0,
      0,
      0,
      0,
      Vector.empty,
      GameSiteRelics(0)
    ))

    assertEquals((details.looseFavor, details.looseSecrets, details.defense),
      (0, 0, 0))
    assertEquals(details.requirement, None)
    assertEquals(details.unknownRelicCount, 0)
  }

  test("a forgeable site shows its forge cost instead of its recover difficulty") {
    val forged = SiteCardPresentation.from(GameSite("forge", "Forge", 0, 0,
      3, 0, Vector.empty, GameSiteRelics(0), recoverDifficulty = Some(4),
      forgeCost = Some(ForgeCost(2, 1))))
    assertEquals(forged.requirement, Some(SiteRequirement.Forge(2, 1)))

    val recover = SiteCardPresentation.from(GameSite("recover", "Recover", 0, 0,
      2, 0, Vector.empty, GameSiteRelics(0), recoverDifficulty = Some(3)))
    assertEquals(recover.requirement, Some(SiteRequirement.Recover(3)))
  }

  test("pile symbols and shape classes distinguish public tops and empty piles") {
    assertEquals(ServerUiSupport.pileSymbol(2, Some("denizen")), "D")
    assertEquals(ServerUiSupport.pileSymbol(1, Some("vision")), "V")
    assertEquals(ServerUiSupport.pileSymbol(0, None), "")
    assertEquals(ServerUiSupport.pileCardClasses(2), "pile-card pile-back")
    assertEquals(ServerUiSupport.pileCardClasses(0), "pile-card pile-empty")
  }

  test("a legal phase power becomes one usePower command") {
    val power = PhasePowerState("denizen.silver-tongue",
      DecisionOptionState("denizen", "92", "Silver Tongue"),
      "Silver Tongue", "Take a favor.")
    val legal = projection(Set("usePower:denizen.silver-tongue:92", "finishRest"),
      phase = "rest", phasePowers = Vector(power))
    assertEquals(PhasePowerButtons.actions(legal), Vector(power ->
      oathdigital.protocol.GameIntent.UsePower("denizen.silver-tongue",
        oathdigital.protocol.WalkerStartArgWire("denizen", "92"))))
    assertEquals(PhasePowerButtons.actions(projection(Set("finishRest"),
      phase = "rest", phasePowers = Vector(power))), Vector.empty)
  }

  test("Finish Rest is offered only when finishRest is legal") {
    assert(PhasePowerButtons.showsFinishRest(projection(Set("finishRest"),
      phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(projection(Set.empty,
      phase = "rest")))
    assert(!PhasePowerButtons.showsFinishRest(projection(Set("finishRest"),
      phase = "wake")))
  }

  private def projection(
      legalControls: Set[String],
      phase: String = "wake",
      activeParticipantId: String = "red-exile",
      ready: Boolean = true,
      phasePowers: Vector[PhasePowerState] = Vector.empty
  ): GameProjection =
    GameProjection(
      gameId = "game-1",
      nextSequence = 8L,
      phase = phase,
      activeParticipantId = Some(activeParticipantId),
      players = Vector(
        GamePlayer(
          "red-exile",
          "Red Exile",
          "exile",
          PlayerColor.Red
        ),
        GamePlayer(
          "blue-exile",
          "Blue Exile",
          "exile",
          PlayerColor.Blue
        )
      ),
      world = Vector.empty,
      pawnLocations = Vector.empty,
      legalControls = legalControls.toVector.sorted,
      ready = ready,
      completed = false,
      phasePowers = phasePowers
    )

  /** Task 5: a `GameProjection` parked on the Forge decision above, for the
    * waiting-notice test below. Reuses `forgeParked` -- the same
    * `WalkerDecisionState` the "Forge is answered by..." test builds and
    * asserts against -- rather than authoring a second, possibly diverging
    * walker decision.
    */
  private def forgeProjection: GameProjection =
    projection(Set.empty).copy(walkerDecision = Some(forgeParked))

  test("a parked walker waiting on another player names them and the question") {
    val waitingOn = forgeProjection.copy(walkerDecision = None,
      walkerWaiting = Some(WalkerWaitingState(
        forgeProjection.players.head.playerId, Some("Choose the Oathkeeper"))))
    assertEquals(WalkerPanelSupport.waitingNotice(waitingOn),
      Some(s"Waiting for ${forgeProjection.players.head.displayName}: " +
        "Choose the Oathkeeper"))
    assertEquals(WalkerPanelSupport.waitingNotice(
      waitingOn.copy(walkerWaiting = None)), None)
  }
  test("a choose-one decision outside Recover is answered from its projected options") {
    val decision = WalkerDecisionState("oathkeeper", "oathkeeper.recipient",
      "decide", query = Some(DecisionQueryState("choose-one", Vector(
        DecisionOptionState("player", "blue", "blue"),
        DecisionOptionState("player", "yellow", "yellow")),
        heading = Some("Choose the Oathkeeper"))))
    assertEquals(WalkerPanelSupport.chooseOneStep(decision).map(_.options.map(_.id)),
      Some(Vector("blue", "yellow")))
    assertEquals(WalkerPanelSupport.chooseOneStep(decision.copy(action = "recover")),
      None)
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(decision,
      decision.query.get.options(1)),
      GameCommand.ResolveWalker("red", "oathkeeper.recipient",
        DecisionAnswerWire.ChooseOneWire("player", "yellow")))
  }
  test("selection actions map only authorized single target shapes to commands") {
    val placeholderCandidates = Vector("a", "b", "c", "d").map(id =>
      BoardTargetCandidate(BoardTargetRef.Site(id), id, Vector.empty))
    def action(kind: String) = BoardTargetAction(kind, "Choose", 1, 1,
      false, placeholderCandidates)
    assertEquals(ServerUiSupport.commandForSelection(action("travel"),
      Vector(BoardTargetRef.Site("site:b")), "red"),
      Some(oathdigital.protocol.GameIntent.StartWalker("travel", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("site", "site:b")))))
    assertEquals(ServerUiSupport.commandForSelection(action("travel"), Vector(
      BoardTargetRef.Site("site:b"), BoardTargetRef.Site("site:c")), "red"), None)
    // Campaign is no longer a board-target selection: it starts from its own
    // control and asks its questions as walker decisions.
    assertEquals(ServerUiSupport.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:b")), "red"), None)
  }
  test("available controls use durable ordered presentation categories") {
    assertEquals(ServerUiSupport.actionCategoryOrder.map(_._2),
      Vector("Major actions", "Minor actions"))
    assertEquals(ServerUiSupport.actionCategory("travel"), "major")
    assertEquals(ServerUiSupport.actionCategory("challenge"), "major")
    assertEquals(ServerUiSupport.actionCategory("campaign"), "major")
    // Using a power's "Action:" is itself a minor action, so an unrecognised
    // kind joins them rather than opening a section of its own.
    assertEquals(ServerUiSupport.actionCategory("unrecognized-power"), "minor")
    assertEquals(ServerUiSupport.majorFamilyOrder, Vector("search", "travel", "campaign",
      "muster", "trade", "forge", "recover", "challenge"))
    assertEquals(Vector("trade-favor", "trade-secret").map(
      ServerUiSupport.actionFamily), Vector("trade", "trade"))
  }
}
