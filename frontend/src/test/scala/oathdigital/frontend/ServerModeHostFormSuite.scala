package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.withEmptyLogPage
import oathdigital.model.PlayerColor
import oathdigital.protocol.{BootstrapParticipantRequest,
  TrustedGameCreateRequestCodec}
import oathdigital.testkit.Unwrap.{accepted, as, present}

import munit.FunSuite
import org.scalajs.dom
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The trusted and development start page: the host form that posts a new
  * game, its add-player menu and validation, and the seat links it shows.
  */
class ServerModeHostFormSuite extends FunSuite:
  private val seatLinksResponse = """{"gameId":"host-game","seats":[""" +
    """{"playerId":"Red","url":"https://oath.test/s/red-code"},""" +
    """{"playerId":"Blue","url":"https://oath.test/s/blue-code"}]}"""

  /** A `ScriptedTransport` that records into `requests` and answers every
    * request with `status` and `body`.
    */
  private def hostTransport(
      requests: ArrayBuffer[RecordedRequest],
      status: Int = 201,
      body: String = seatLinksResponse
  ): ScriptedTransport = ScriptedTransport(
    (_, _, _) => Future.successful(Right(TransportResponse(status, body))),
    requests)

  private def decodedHostRequests(requests: collection.Seq[RecordedRequest]) =
    requests.map(request => accepted(TrustedGameCreateRequestCodec.decode(
      present(request.body, "a request body"))))

  private def hostColors(browser: TestBrowser): Vector[String] =
    browser.byClass("host-player").map(player => present(
      player.getAttribute("class").split(" ")
        .find(_.startsWith("host-player-")),
      "a host-player-<color> class").stripPrefix("host-player-"))

  private def menuColors(browser: TestBrowser): Vector[String] =
    browser.byClass("add-player-option").map(_.textContent)

  private def toggle(browser: TestBrowser): dom.html.Button =
    as[dom.html.Button](present(browser.byClass("add-player-toggle")
      .headOption, "the add-player toggle"))

  /** The players the host form posts, as the player ID and color of each. */
  private def postedPlayers(requests: collection.Seq[RecordedRequest])
      : Vector[(String, PlayerColor)] =
    decodedHostRequests(requests).head.participants
      .map(player => player.playerId -> player.color)

  /** Opens the add-player menu and picks `options` from it, one at a time. */
  private def addPlayers(browser: TestBrowser, options: String*): Unit =
    options.foreach { option =>
      browser.click("add-player-toggle")
      browser.click(option)
    }

  private def startHostForm(browser: TestBrowser,
      requests: ArrayBuffer[RecordedRequest] = ArrayBuffer.empty): Unit =
    Main.start(browser.mount, "/", trustedAlpha = true,
      withEmptyLogPage(hostTransport(requests)))

  test("trusted root posts host form and displays ordered copyable " +
      "seat links"):
    TestBrowser.use("?gameId=ignored&playerId=ignored"): browser =>
      val requests = ArrayBuffer.empty[RecordedRequest]
      startHostForm(browser, requests)
      assertEquals(requests.size, 0)
      assert(!browser.nodes.exists(
        _.getAttribute("aria-label") == "Game ID"))
      assert(!browser.nodes.exists(
        _.getAttribute("aria-label") == "First player ID"))
      assertEquals(hostColors(browser), Vector("red", "blue"))
      assertEquals(browser.input("Red player ID").value, "Red")
      assertEquals(browser.input("Blue player ID").value, "Blue")
      browser.click("create-trusted-game")
      browser.settle.map { _ =>
        assertEquals(requests.map(r => r.method -> r.url).toVector,
          Vector("POST" -> "/games"))
        // The server generates the game ID and derives each lineage.
        val body = present(requests.head.body, "a request body")
        assertEquals(ujson.read(body).obj.keySet, Set("participants"))
        val request = decodedHostRequests(requests).head
        assertEquals(request.participants, Vector(
          BootstrapParticipantRequest("Red", PlayerColor.Red),
          BootstrapParticipantRequest("Blue", PlayerColor.Blue)))
        assert(browser.text.contains("Game ID: host-game"))
        val links = browser.byClass("seat-link").map(as[dom.html.Input](_))
        assertEquals(links.map(_.value), Vector(
          "https://oath.test/s/red-code", "https://oath.test/s/blue-code"))
        assert(links.forall(_.readOnly))
        assertEquals(browser.byClass("copy-seat-link").size, 2)
        assert(browser.urls.isEmpty)
        browser.click("copy-seat-link")
        assertEquals(browser.copied, Vector("https://oath.test/s/red-code"))
      }

  test("host player ID fields keep what is typed: no auto-capitalize or " +
      "auto-correct"):
    TestBrowser.useSynchronously(): browser =>
      startHostForm(browser)
      val input = browser.input("Red player ID")
      assertEquals(input.getAttribute("autocapitalize"), "none")
      assertEquals(input.getAttribute("autocorrect"), "off")
      assertEquals(input.getAttribute("spellcheck"), "false")

  test("the host add-player menu offers the untaken colors in order"):
    TestBrowser.useSynchronously(): browser =>
      startHostForm(browser)
      assert(browser.byClass("add-player-menu").head.hasAttribute("hidden"))
      browser.click("add-player-toggle")
      assertEquals(toggle(browser).getAttribute("aria-expanded"), "true")
      assertEquals(menuColors(browser),
        Vector("Yellow", "White", "Black", "Pink", "Brown"))
      browser.click("add-player-pink")
      assert(browser.byClass("add-player-menu").head.hasAttribute("hidden"))
      assertEquals(hostColors(browser), Vector("red", "blue", "pink"))

  test("the sixth player disables the add-player toggle"):
    TestBrowser.useSynchronously(): browser =>
      startHostForm(browser)
      addPlayers(browser, "add-player-pink", "add-player-white",
        "add-player-brown", "add-player-black")
      assertEquals(hostColors(browser),
        Vector("red", "blue", "pink", "white", "brown", "black"))
      assert(toggle(browser).disabled)
      assert(browser.text.contains("Maximum 6 players"))

  test("removing a player from a full table re-enables the add-player " +
      "toggle"):
    TestBrowser.useSynchronously(): browser =>
      startHostForm(browser)
      addPlayers(browser, "add-player-pink", "add-player-white",
        "add-player-brown", "add-player-black")
      browser.click("remove-player-white")
      assertEquals(hostColors(browser),
        Vector("red", "blue", "pink", "brown", "black"))
      assert(!toggle(browser).disabled)
      assert(!browser.text.contains("Maximum 6 players"))
      browser.click("add-player-toggle")
      assertEquals(menuColors(browser), Vector("Yellow", "White"))

  test("the posted participants follow the chosen colors"):
    TestBrowser.use(): browser =>
      val requests = ArrayBuffer.empty[RecordedRequest]
      startHostForm(browser, requests)
      addPlayers(browser, "add-player-pink", "add-player-white",
        "add-player-brown", "add-player-black")
      browser.click("remove-player-white")
      browser.click("create-trusted-game")
      browser.settle.map { _ =>
        assertEquals(postedPlayers(requests), Vector(
          "Red" -> PlayerColor.Red, "Blue" -> PlayerColor.Blue,
          "Pink" -> PlayerColor.Pink, "Brown" -> PlayerColor.Brown,
          "Black" -> PlayerColor.Black))
      }

  test("host form blocks fewer than two players, invalid IDs and duplicate " +
      "IDs before posting"):
    TestBrowser.use(): browser =>
      val requests = ArrayBuffer.empty[RecordedRequest]
      startHostForm(browser, requests)
      browser.click("remove-player-red")
      browser.click("create-trusted-game")
      assert(browser.text.contains("Need at least 2 players."))
      browser.click("remove-player-blue")
      assertEquals(hostColors(browser), Vector.empty)
      addPlayers(browser, "add-player-yellow", "add-player-red")
      assertEquals(hostColors(browser), Vector("yellow", "red"))
      browser.input("Yellow player ID").value = "bad id"
      browser.click("create-trusted-game")
      assert(browser.text.contains(
        "Yellow player ID must start with a letter or digit"))
      browser.input("Yellow player ID").value = "Alex"
      browser.input("Red player ID").value = " Alex "
      browser.click("create-trusted-game")
      assert(browser.text.contains("Player ID \"Alex\" is used twice."))
      browser.input("Red player ID").value = "Sam"
      browser.click("create-trusted-game")
      browser.settle.map { _ =>
        assertEquals(requests.size, 1)
        assertEquals(postedPlayers(requests), Vector(
          "Alex" -> PlayerColor.Yellow, "Sam" -> PlayerColor.Red))
      }

  test("host duplicate game response keeps the form editable and names the " +
      "cause"):
    TestBrowser.use(): browser =>
      val requests = ArrayBuffer.empty[RecordedRequest]
      Main.start(browser.mount, "/", trustedAlpha = true,
        withEmptyLogPage(hostTransport(requests, 409,
          """{"error":"game-already-exists",""" +
            """"message":"Game already exists"}""")))
      browser.click("create-trusted-game")
      browser.settle.flatMap { _ =>
        assert(browser.text.contains("could not assign a new game ID"),
          browser.text)
        browser.click("create-trusted-game")
        browser.settle
      }.map { _ =>
        assertEquals(decodedHostRequests(requests).size, 2)
        assert(!browser.text.contains("refreshed"))
        assert(!as[dom.html.Button](
          browser.byClass("create-trusted-game").head).disabled)
        assert(browser.byClass("seat-link").isEmpty)
      }

  test("development root without a game shows the start page and opens the " +
      "created game"):
    TestBrowser.use("?mode=server"): browser =>
      val requests = ArrayBuffer.empty[RecordedRequest]
      val opened = ArrayBuffer.empty[String]
      Main.start(browser.mount, "/", trustedAlpha = false,
        withEmptyLogPage(hostTransport(requests)), navigate = opened += _)
      assertEquals(requests.size, 0)
      assertEquals(hostColors(browser), Vector("red", "blue"))
      assert(!browser.text.contains("assigned link"))
      browser.click("create-trusted-game")
      browser.settle.map { _ =>
        assertEquals(requests.map(r => r.method -> r.url).toVector,
          Vector("POST" -> "/games"))
        assertEquals(decodedHostRequests(requests).head.participants
          .map(_.color), Vector(PlayerColor.Red, PlayerColor.Blue))
        assertEquals(opened.toVector,
          Vector("/?mode=server&gameId=host-game&playerId=Red"))
        assert(browser.byClass("seat-link").isEmpty)
      }
