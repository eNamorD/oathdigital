package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js
import oathdigital.protocol.projection.{LogEntryWire, LogSpanWire}

class GameLogPaneSuite extends munit.FunSuite:
  private def entry(sequence: Long, kind: String, depth: Int,
      spans: LogSpanWire*) = LogEntryWire(sequence, 0, kind, depth, spans.toVector)

  private val setup = entry(0, "round", 0, LogSpanWire("text", "Setup"))
  private val turn = entry(3, "turn", 0,
    LogSpanWire("player", "Red", id = Some("red")), LogSpanWire("text", "'s turn"))
  private val travel = entry(5, "action", 1, LogSpanWire("text", "Started Travel"),
    LogSpanWire("cost", "−2 Supply", value = Some(2), unit = Some("Supply")))

  /** A content box jsdom gives a size: `height` tall, `scrolled` from the top,
    * holding `total` pixels. */
  private def box(total: Int, height: Int, scrolled: Double): dom.html.Element =
    val content = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    def define(name: String, value: js.Any, writable: Boolean) =
      js.Object.defineProperty(content, name, js.Dynamic.literal(value = value,
        writable = writable, configurable = true)
        .asInstanceOf[js.PropertyDescriptor])
    define("scrollHeight", total, writable = false)
    define("clientHeight", height, writable = false)
    define("scrollTop", scrolled, writable = true)
    content

  private def items(content: dom.Element): Vector[dom.Element] =
    content.querySelectorAll("li").toVector.map(_.asInstanceOf[dom.Element])

  test("an empty log shows the Setup headline"):
    val content = box(0, 0, 0)
    new GameLogPane(content).show("g|red", Vector.empty, Map.empty)
    assertEquals(items(content).map(_.textContent), Vector("Setup"))

  test("a phase headline carries its kind and is not a heading in the pane"):
    val content = box(0, 0, 0)
    new GameLogPane(content).show("g|red", Vector(setup, turn,
      entry(4, "phase", 0, LogSpanWire("text", "Wake"))), Map.empty)
    val shown = items(content)
    assertEquals(shown(2).getAttribute("class"),
      "log-entry log-headline log-phase")
    assertEquals(shown(2).querySelector("h5"), null)

  test("headlines and lines carry their depth and kind; a player takes the seat colour"):
    val content = box(0, 0, 0)
    new GameLogPane(content).show("g|red", Vector(setup, turn, travel),
      Map("red" -> "player-red"))
    val shown = items(content)
    assertEquals(shown.map(_.getAttribute("class")), Vector(
      "log-entry log-headline log-round", "log-entry log-headline log-turn",
      "log-entry log-line log-action"))
    assertEquals(shown(1).querySelector(".log-player").getAttribute("class"),
      "log-player player-ref player-red")
    assertEquals(shown(2).querySelector(".log-cost").textContent,
      "−2 Supply")
    assertEquals(shown(2).getAttribute("title"), "5.0")
    val list = content.querySelector("ol")
    assertEquals(list.getAttribute("role"), "log")
    assertEquals(list.getAttribute("aria-live"), "polite")

  test("a dice span draws the die-face chips, one per face"):
    val content = box(0, 0, 0)
    val roll = entry(7, "roll", 1, LogSpanWire("text", "Rolled "),
      LogSpanWire("dice", "sword, blank", id = Some("one-sword blank"),
        unit = Some("attack")), LogSpanWire("text", " for the attack"))
    new GameLogPane(content).show("g|red", Vector(roll), Map.empty)
    val faces = content.querySelectorAll(".die-faces .die-face")
    assertEquals(faces.length, 2)
    assertEquals(faces(0).getAttribute("aria-label"), "one sword")
    assertEquals(content.querySelector(".die-faces").getAttribute("class"),
      "die-faces log-dice log-dice-attack")

  test("new entries append; a reader at the end stays at the end"):
    val content = box(500, 100, 400)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    val first = items(content).head
    content.scrollTop = 400
    pane.show("g|red", Vector(setup, turn, travel), Map.empty)
    assert(items(content).head eq first)
    assertEquals(items(content).size, 3)
    assertEquals(content.scrollTop, 500.0)
    // A reader who is followed is never offered the New chip.
    assert(content.querySelector(".log-new").hasAttribute("hidden"))

  test("a different session redraws the whole list"):
    val content = box(0, 0, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    pane.show("g|blue", Vector(setup), Map.empty)
    assertEquals(items(content).map(_.textContent), Vector("Setup"))

  private val victory = entry(9, "victory", 0,
    LogSpanWire("player", "Red", id = Some("red")), LogSpanWire("text", " won"))

  /** Gives `node` a layout box: jsdom lays nothing out. */
  private def place(node: dom.Element, top: Double, bottom: Double): Unit =
    js.Object.defineProperty(node, "getBoundingClientRect",
      js.Dynamic.literal(configurable = true, value = (() =>
        js.Dynamic.literal(top = top, bottom = bottom, height = bottom - top))
        : js.Function0[js.Dynamic]).asInstanceOf[js.PropertyDescriptor])

  private def scroll(content: dom.Element): Unit =
    content.dispatchEvent(new dom.Event("scroll"))

  test("a marker below the last entry puts the divider before the first newer one, at the top"):
    val content = box(500, 100, 0)
    new GameLogPane(content).show("g|red", Vector(setup, turn, travel), Map.empty,
      since = Some(3))
    assertEquals(items(content).map(_.getAttribute("class")), Vector(
      "log-entry log-headline log-round", "log-entry log-headline log-turn",
      "log-divider", "log-entry log-line log-action"))
    assertEquals(content.querySelector(".log-divider").textContent,
      "Since you last looked")
    // Scrolled to the divider, which jsdom places at 0, not to the end.
    assertEquals(content.scrollTop, 0.0)

  test("a marker at the last entry, or none, opens at the end with no divider"):
    Vector(Some(5L), None).foreach { since =>
      val content = box(500, 100, 0)
      new GameLogPane(content).show("g|red", Vector(setup, turn, travel), Map.empty, since)
      assertEquals(content.querySelectorAll(".log-divider").length, 0)
      assertEquals(content.scrollTop, 500.0)
    }

  test("appends never add a divider"):
    val content = box(0, 0, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup), Map.empty, since = Some(0))
    pane.show("g|red", Vector(setup, turn, travel), Map.empty, since = Some(0))
    assertEquals(content.querySelectorAll(".log-divider").length, 0)

  test("a reader scrolled up is offered the New chip, which returns them to the end"):
    val content = box(500, 100, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    val chip = content.querySelector(".log-new").asInstanceOf[dom.html.Button]
    assertEquals(chip.textContent, "New")
    assert(chip.hasAttribute("hidden"))
    content.scrollTop = 40
    pane.show("g|red", Vector(setup, turn, travel), Map.empty)
    assert(!chip.hasAttribute("hidden"))
    assertEquals(content.scrollTop, 40.0)
    chip.click()
    assertEquals(content.scrollTop, 500.0)
    assert(chip.hasAttribute("hidden"))

  test("scrolling to the end hides the chip and tells the owner"):
    val content = box(500, 100, 0)
    var told = 0
    val pane = new GameLogPane(content, scrolled = () => told += 1)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    content.scrollTop = 40
    pane.show("g|red", Vector(setup, turn, travel), Map.empty)
    content.scrollTop = 400
    scroll(content)
    assert(content.querySelector(".log-new").hasAttribute("hidden"))
    assertEquals(told, 1)

  test("in headings mode rounds and victories are h3, turns h4, lines neither"):
    val content = box(0, 0, 0)
    new GameLogPane(content, headings = true)
      .show("g|red", Vector(setup, turn, travel, victory), Map.empty)
    val shown = items(content)
    assertEquals(shown.map(item => Option(item.querySelector(".log-heading"))
      .map(_.tagName)), Vector(Some("H3"), Some("H4"), None, Some("H3")))
    assertEquals(shown(1).textContent, "Red's turn")
    assertEquals(shown(1).querySelector("h4 .log-player").textContent, "Red")

  test("the empty overlay's Setup is a heading too"):
    val content = box(0, 0, 0)
    new GameLogPane(content, headings = true).show("g|red", Vector.empty, Map.empty)
    assertEquals(content.querySelector("li h3.log-heading").textContent, "Setup")

  test("last is the newest entry's sequence"):
    val pane = new GameLogPane(box(0, 0, 0))
    assertEquals(pane.last, None)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    assertEquals(pane.last, Some(3L))

  test("the reading position is the first line seen below the stuck headline"):
    val content = box(500, 100, 200)
    val pane = new GameLogPane(content)
    val search = entry(6, "action", 1, LogSpanWire("text", "Started Search"))
    pane.show("g|red", Vector(setup, turn, travel, search), Map.empty)
    content.scrollTop = 200
    place(content, 0, 100)
    val shown = items(content)
    place(shown(0), -40, -20)
    place(shown(1), 0, 15) // the stuck turn headline
    place(shown(2), -2, 13) // hidden under it
    place(shown(3), 13, 28)
    assertEquals(pane.position, Some(3))

  test("a list that shows no line reports no position"):
    val content = box(500, 100, 200)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    content.scrollTop = 200
    place(content, 0, 100)
    val shown = items(content)
    place(shown(0), -40, -20)
    place(shown(1), 0, 15)
    assertEquals(pane.position, None)

  test("a list at the end has no position, and restoring none goes to the end"):
    val content = box(500, 100, 400)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    content.scrollTop = 400
    assertEquals(pane.position, None)
    val other = box(900, 300, 0)
    val overlay = new GameLogPane(other, headings = true)
    overlay.show("g|red", Vector(setup, turn), Map.empty)
    other.scrollTop = 0
    overlay.restore(None)
    assertEquals(other.scrollTop, 900.0)

  test("restoring a position puts that child just under the list's stuck headline"):
    val content = box(900, 300, 0)
    val overlay = new GameLogPane(content, headings = true)
    overlay.show("g|red", Vector(setup, turn, travel), Map.empty)
    content.scrollTop = 0
    place(content, 50, 350)
    val shown = items(content)
    place(shown(0), 50, 68) // the round headline that sticks in the overlay
    place(shown(2), 350, 365)
    overlay.restore(Some(2))
    assertEquals(content.scrollTop, 350.0 - 50.0 - 18.0)

  test("a card list is a control that opens it; few cards or a count are words"):
    val cards = (1 to 6).toVector.map(n =>
      CardDetails(s"d$n", "denizen", s"Card $n"))
    val listed = entry(7, "action", 1, LogSpanWire("text", "Scryer: "),
      LogSpanWire("cards", "6 cards", cards = cards))
    val plain = entry(8, "action", 1, LogSpanWire("text", "Oracle: 6 cards"))
    val few = entry(9, "action", 1, LogSpanWire("text", "Pig: "),
      LogSpanWire("card", "Old Oak", id = Some("d1")))
    var opened = Vector.empty[CardInspection.Request]
    CardInspection.onOpen(request => opened :+= request)
    try
      val content = box(0, 0, 0)
      new GameLogPane(content).show("g|red", Vector(setup, listed, plain, few),
        Map.empty)
      val shown = items(content)
      val short = shown.find(_.textContent.startsWith("Pig")).get
      assertEquals(short.querySelector(".log-card").textContent, "Old Oak")
      assertEquals(short.querySelector(".log-cards"), null)
      val link = shown.find(_.textContent.startsWith("Scryer")).get
        .querySelector("button.log-cards").asInstanceOf[dom.html.Button]
      assertEquals(link.textContent, "6 cards")
      link.click()
      assertEquals(opened.collect {
        case CardInspection.Request.Cards(title, sent, _) =>
          title -> sent.map(_.cardId)
      }, Vector("6 cards" -> cards.map(_.cardId)))
      assertEquals(shown.find(_.textContent.startsWith("Oracle")).get
        .querySelector(".log-cards"), null)
    finally CardInspection.clear()
