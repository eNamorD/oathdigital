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

  test("a reader scrolled up is not moved by new entries"):
    val content = box(500, 100, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    content.scrollTop = 40
    pane.show("g|red", Vector(setup, turn, travel), Map.empty)
    assertEquals(content.scrollTop, 40.0)

  test("a different session redraws the whole list"):
    val content = box(0, 0, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    pane.show("g|blue", Vector(setup), Map.empty)
    assertEquals(items(content).map(_.textContent), Vector("Setup"))
