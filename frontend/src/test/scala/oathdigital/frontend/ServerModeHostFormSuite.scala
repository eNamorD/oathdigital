package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.frontend.ServerModeFixtures.quietLog

import munit.FunSuite
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The trusted and development start page: the host form that posts a new
  * game, its add-player menu and validation, and the seat links it shows.
  */
class ServerModeHostFormSuite extends FunSuite:

  private def hostTransport(
      requests: scala.collection.mutable.ArrayBuffer[(String, String, Option[String])],
      status: Int = 201,
      body: String = """{"gameId":"host-game","seats":[{"playerId":"Red","url":"https://oath.test/s/red-code"},{"playerId":"Blue","url":"https://oath.test/s/blue-code"}]}"""
  ): JsonTransport = new JsonTransport:
    def request(method: String, url: String, body0: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
      requests += ((method, url, body0))
      scala.concurrent.Future.successful(Right(TransportResponse(status, body)))

  private def hostRequests(requests: collection.Seq[(String, String, Option[String])]) =
    requests.map(r => oathdigital.protocol.TrustedGameCreateRequestCodec.decode(r._3.get).toOption.get)

  private def hostColors(browser: TestBrowser): Vector[String] =
    browser.byClass("host-player").map(_.getAttribute("class").split(" ")
      .find(_.startsWith("host-player-")).get.stripPrefix("host-player-"))

  private def menuColors(browser: TestBrowser): Vector[String] =
    browser.byClass("add-player-option").map(_.textContent)

  private def toggle(browser: TestBrowser): org.scalajs.dom.html.Button =
    browser.byClass("add-player-toggle").head.asInstanceOf[org.scalajs.dom.html.Button]

  test("trusted root posts host form and displays ordered copyable seat links"):
    val browser = new TestBrowser("?gameId=ignored&playerId=ignored")
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    Main.start(browser.mount, "/", trustedAlpha = true, quietLog(hostTransport(requests)))
    assertEquals(requests.size, 0)
    assert(!browser.nodes.exists(_.getAttribute("aria-label") == "Game ID"))
    assert(!browser.nodes.exists(_.getAttribute("aria-label") == "First player ID"))
    assertEquals(hostColors(browser), Vector("red", "blue"))
    assertEquals(browser.input("Red player ID").value, "Red")
    assertEquals(browser.input("Blue player ID").value, "Blue")
    browser.click("create-trusted-game")
    browser.settle.map { _ =>
      assertEquals(requests.map(r => r._1 -> r._2).toVector, Vector("POST" -> "/games"))
      // The server generates the game ID and derives each lineage.
      assertEquals(ujson.read(requests.head._3.get).obj.keySet, Set("participants"))
      val request = hostRequests(requests).head
      assertEquals(request.participants, Vector(
        oathdigital.protocol.BootstrapParticipantRequest("Red", PlayerColor.Red),
        oathdigital.protocol.BootstrapParticipantRequest("Blue", PlayerColor.Blue)))
      assert(browser.text.contains("Game ID: host-game"))
      val links = browser.byClass("seat-link").map(_.asInstanceOf[org.scalajs.dom.html.Input])
      assertEquals(links.map(_.value), Vector("https://oath.test/s/red-code", "https://oath.test/s/blue-code"))
      assert(links.forall(_.readOnly))
      assertEquals(browser.byClass("copy-seat-link").size, 2)
      assert(browser.urls.isEmpty)
      browser.click("copy-seat-link")
      assertEquals(browser.copied, Vector("https://oath.test/s/red-code"))
    }.andThen { case _ => browser.close() }(using scala.scalajs.concurrent.JSExecutionContext.queue)

  test("host player ID fields keep what is typed: no auto-capitalize or auto-correct"):
    val browser = new TestBrowser
    Main.start(browser.mount, "/", trustedAlpha = true,
      quietLog(hostTransport(scala.collection.mutable.ArrayBuffer.empty)))
    val input = browser.input("Red player ID")
    assertEquals(input.getAttribute("autocapitalize"), "none")
    assertEquals(input.getAttribute("autocorrect"), "off")
    assertEquals(input.getAttribute("spellcheck"), "false")
    browser.close()

  test("host add-player menu offers untaken colors in order and stops at six players"):
    val browser = new TestBrowser
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    Main.start(browser.mount, "/", trustedAlpha = true, quietLog(hostTransport(requests)))
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
      assertEquals(hostRequests(requests).head.participants.map(p => p.playerId -> p.color),
        Vector("Red" -> PlayerColor.Red, "Blue" -> PlayerColor.Blue, "Pink" -> PlayerColor.Pink,
          "Brown" -> PlayerColor.Brown, "Black" -> PlayerColor.Black))
    }.andThen { case _ => browser.close() }

  test("host form blocks fewer than two players, invalid IDs and duplicate IDs before posting"):
    val browser = new TestBrowser
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    Main.start(browser.mount, "/", trustedAlpha = true, quietLog(hostTransport(requests)))
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

  test("host duplicate game response keeps the form editable and names the cause"):
    val browser = new TestBrowser
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    Main.start(browser.mount, "/", trustedAlpha = true, quietLog(hostTransport(requests, 409,
      """{"error":"game-already-exists","message":"Game already exists"}""")))
    browser.click("create-trusted-game")
    browser.settle.flatMap { _ =>
      assert(browser.text.contains("could not assign a new game ID"), browser.text)
      browser.click("create-trusted-game")
      browser.settle
    }.map { _ =>
      assertEquals(hostRequests(requests).size, 2)
      assert(!browser.text.contains("refreshed"))
      assert(!browser.byClass("create-trusted-game").head.asInstanceOf[org.scalajs.dom.html.Button].disabled)
      assert(browser.byClass("seat-link").isEmpty)
    }.andThen { case _ => browser.close() }

  test("development root without a game shows the start page and opens the created game"):
    val browser = new TestBrowser("?mode=server")
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    val opened = scala.collection.mutable.ArrayBuffer.empty[String]
    Main.start(browser.mount, "/", trustedAlpha = false, quietLog(hostTransport(requests)),
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
