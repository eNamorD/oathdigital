package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js
import oathdigital.protocol.projection.{LogEntryWire, LogSectionWire,
  LogSpanWire, LogTakenBackWire}

/** Taken-back moves in the log (undo design, "Game Log"): each contiguous
  * run is one collapsed group under its header, its lines keep their bands,
  * and a redraw keeps both the reader's place and the groups they opened. */
class GameLogTakenBackPaneSuite extends munit.FunSuite:
  private val takenBackByRed = LogTakenBackWire(5L, Vector(
    LogSpanWire("player", "Red", id = Some("red")),
    LogSpanWire("text", " took back the following moves:")))

  private def travelLine(sequence: Long, words: String): LogEntryWire =
    LogEntryWire(sequence, 0, "action", 1, Vector(LogSpanWire("text", words)),
      Some(LogSectionWire(5L, "travel")))

  private def takenBack(entry: LogEntryWire): LogEntryWire =
    entry.copy(takenBack = Some(takenBackByRed))

  private val setup = LogEntryWire(0, 0, "round", 0,
    Vector(LogSpanWire("text", "Setup")))
  private val started = travelLine(5, "Started Travel")
  private val moved = travelLine(6, "Travelled to Buried Giant")
  private val movedAgain = travelLine(8, "Travelled to Great Spire")

  /** A content box jsdom gives a size: `height` tall, `scrolled` from the
    * top, holding `total` pixels. jsdom does not clamp `scrollTop`. */
  private def sizedLogContent(total: Int, height: Int, scrolled: Double)
      : dom.html.Element =
    val content = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    def define(name: String, value: js.Any, writable: Boolean) =
      js.Object.defineProperty(content, name, js.Dynamic.literal(value = value,
        writable = writable, configurable = true)
        .asInstanceOf[js.PropertyDescriptor])
    define("scrollHeight", total, writable = false)
    define("clientHeight", height, writable = false)
    define("scrollTop", scrolled, writable = true)
    content

  private def topLevel(content: dom.Element): Vector[dom.Element] =
    val list = content.querySelector("ol.game-log")
    (0 until list.children.length).map(list.children(_)).toVector

  private def toggle(content: dom.Element): dom.html.Button =
    content.querySelector(".log-taken-back-toggle").asInstanceOf[dom.html.Button]

  private def groupLines(content: dom.Element): dom.Element =
    content.querySelector(".log-taken-back-lines")

  test("a taken-back run is one collapsed group under its header"):
    val content = sizedLogContent(0, 0, 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started), takenBack(moved)),
      Map("red" -> "player-red"))
    val shown = topLevel(content)
    assertEquals(shown.map(_.getAttribute("class")).last, "log-taken-back")
    assertEquals(shown.size, 2)
    assertEquals(toggle(content).textContent,
      "Red took back the following moves:")
    assertEquals(toggle(content).querySelector(".log-player")
      .getAttribute("class"), "log-player player-ref player-red")
    assertEquals(toggle(content).getAttribute("aria-expanded"), "false")
    assert(groupLines(content).hasAttribute("hidden"))
    assertEquals(groupLines(content).children.length, 2)

  test("inside a group the lines keep their band"):
    val content = sizedLogContent(0, 0, 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started), takenBack(moved)), Map.empty)
    val lines = groupLines(content)
    assertEquals(lines.children(0).getAttribute("class"),
      "log-entry log-line log-action log-band log-band-travel log-band-start")
    assertEquals(lines.children(1).getAttribute("class"),
      "log-entry log-line log-action log-band log-band-travel log-band-end")

  test("the toggle opens and closes the group"):
    val content = sizedLogContent(0, 0, 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started)), Map.empty)
    toggle(content).click()
    assertEquals(toggle(content).getAttribute("aria-expanded"), "true")
    assert(!groupLines(content).hasAttribute("hidden"))
    toggle(content).click()
    assertEquals(toggle(content).getAttribute("aria-expanded"), "false")
    assert(groupLines(content).hasAttribute("hidden"))

  test("a group the reader opened stays open through a redraw"):
    val content = sizedLogContent(0, 0, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started)), Map.empty)
    toggle(content).click()
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    assertEquals(groupLines(content).children.length, 2)
    assertEquals(toggle(content).getAttribute("aria-expanded"), "true")

  test("a live line after a group starts its own band, even in the same section"):
    val content = sizedLogContent(0, 0, 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started), takenBack(moved), movedAgain), Map.empty)
    assertEquals(topLevel(content).last.getAttribute("class"),
      "log-entry log-line log-action log-band log-band-travel " +
        "log-band-start log-band-end")

  test("an appended live line after a group starts its own band"):
    val content = sizedLogContent(0, 0, 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started)), Map.empty)
    pane.show("g|red", Vector(setup, takenBack(started), movedAgain), Map.empty)
    assertEquals(topLevel(content).last.getAttribute("class"),
      "log-entry log-line log-action log-band log-band-travel " +
        "log-band-start log-band-end")

  test("lines that gain a group are redrawn, and a reader scrolled up stays put"):
    val content = sizedLogContent(total = 1000, height = 100, scrolled = 200)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, started, moved), Map.empty)
    content.scrollTop = 200
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    assertEquals(topLevel(content).map(_.getAttribute("class")).last,
      "log-taken-back")
    assertEquals(content.scrollTop, 200.0)

  test("a reader at the end stays at the end through a redraw"):
    val content = sizedLogContent(total = 1000, height = 100, scrolled = 1000)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, started, moved), Map.empty)
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    assertEquals(content.scrollTop, 1000.0)

  test("a divider that would fall inside a group stands before it"):
    val content = sizedLogContent(0, 0, 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started), takenBack(moved)), Map.empty,
      since = Some(5L))
    assertEquals(topLevel(content).map(_.getAttribute("class")),
      Vector("log-entry log-headline log-round", "log-divider",
        "log-taken-back"))
