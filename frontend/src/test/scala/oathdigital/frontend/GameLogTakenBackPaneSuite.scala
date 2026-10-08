package oathdigital.frontend

import org.scalajs.dom
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
    TestLog.textEntry(sequence, "action", depth = 1, words,
      section = Some(LogSectionWire(5L, "travel")))

  private def takenBack(entry: LogEntryWire): LogEntryWire =
    entry.copy(takenBack = Some(takenBackByRed))

  private val setup = TestLog.textEntry(sequence = 0, kind = "round",
    depth = 0, text = "Setup")
  private val started = travelLine(5, "Started Travel")
  private val moved = travelLine(6, "Travelled to Buried Giant")
  private val movedAgain = travelLine(8, "Travelled to Great Spire")

  private def topLevel(content: dom.Element): Vector[dom.Element] =
    val list = content.querySelector("ol.game-log")
    (0 until list.children.length).map(list.children(_)).toVector

  private def toggle(content: dom.Element): dom.html.Button =
    content.querySelector(".log-taken-back-toggle").asInstanceOf[dom.html.Button]

  private def groupLines(content: dom.Element): dom.Element =
    content.querySelector(".log-taken-back-lines")

  test("a taken-back run is one collapsed group under its header"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
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
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started), takenBack(moved)), Map.empty)
    val lines = groupLines(content)
    assertEquals(lines.children(0).getAttribute("class"),
      "log-entry log-line log-action log-band log-band-travel log-band-start")
    assertEquals(lines.children(1).getAttribute("class"),
      "log-entry log-line log-action log-band log-band-travel log-band-end")

  test("the toggle opens and closes the group"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started)), Map.empty)
    toggle(content).click()
    assertEquals(toggle(content).getAttribute("aria-expanded"), "true")
    assert(!groupLines(content).hasAttribute("hidden"))
    toggle(content).click()
    assertEquals(toggle(content).getAttribute("aria-expanded"), "false")
    assert(groupLines(content).hasAttribute("hidden"))

  test("a group the reader opened stays open through a redraw"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started)), Map.empty)
    toggle(content).click()
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    assertEquals(groupLines(content).children.length, 2)
    assertEquals(toggle(content).getAttribute("aria-expanded"), "true")

  test("a live line after a group starts its own band, even in the same section"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started), takenBack(moved), movedAgain), Map.empty)
    assertEquals(topLevel(content).last.getAttribute("class"),
      "log-entry log-line log-action log-band log-band-travel " +
        "log-band-start log-band-end")

  test("an appended live line after a group starts its own band"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started)), Map.empty)
    pane.show("g|red", Vector(setup, takenBack(started), movedAgain), Map.empty)
    assertEquals(topLevel(content).last.getAttribute("class"),
      "log-entry log-line log-action log-band log-band-travel " +
        "log-band-start log-band-end")

  test("lines that gain a group are redrawn, and a reader scrolled up stays put"):
    val content = TestLog.sizedBox(total = 1000, height = 100, scrolled = 200)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, started, moved), Map.empty)
    content.scrollTop = 200
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    assertEquals(topLevel(content).map(_.getAttribute("class")).last,
      "log-taken-back")
    assertEquals(content.scrollTop, 200.0)

  test("a reader at the end stays at the end through a redraw"):
    // 2px short of the end is inside the slack, so the reader counts as at
    // the end, and the redraw must carry them to the new end.
    val content = TestLog.sizedBox(total = 1000, height = 100, scrolled = 898)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, started, moved), Map.empty)
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    assertEquals(content.scrollTop, 1000.0)

  test("a divider that would fall inside a group stands before it"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started), takenBack(moved)), Map.empty,
      since = Some(5L))
    assertEquals(topLevel(content).map(_.getAttribute("class")),
      Vector("log-entry log-headline log-round", "log-divider",
        "log-taken-back"))

  test("a group the reader opened closes again in another session"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started)), Map.empty)
    toggle(content).click()
    pane.show("h|red", Vector(setup, takenBack(started)), Map.empty)
    assertEquals(toggle(content).getAttribute("aria-expanded"), "false")

  test("a new line that arrives already taken back redraws into a group"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, started), Map.empty)
    pane.show("g|red", Vector(setup, started, takenBack(moved)), Map.empty)
    assertEquals(topLevel(content).map(_.getAttribute("class")).last,
      "log-taken-back")

  test("two groups side by side stay two groups"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val later = LogTakenBackWire(8L, takenBackByRed.header)
    new GameLogPane(content).show("g|red",
      Vector(setup, takenBack(started),
        movedAgain.copy(takenBack = Some(later))), Map.empty)
    assertEquals(content.querySelectorAll(".log-taken-back").length, 2)
    assertEquals(groupLines(content).children.length, 1)

  test("live lines that arrive with an undo offer the New chip to a reader scrolled up"):
    val content = TestLog.sizedBox(total = 1000, height = 100, scrolled = 200)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, started, moved), Map.empty)
    content.scrollTop = 200
    pane.show("g|red",
      Vector(setup, takenBack(started), takenBack(moved), movedAgain),
      Map.empty)
    assert(!content.querySelector(".log-new").hasAttribute("hidden"))

  test("restored lines leave their group and read as ordinary lines"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    pane.show("g|red", Vector(setup, started, moved), Map.empty)
    assert(content.querySelector(".log-taken-back") == null, "no group expected")
    assertEquals(topLevel(content).size, 3)

  /** `moved` as it reads in the group that remains after a Redo restored
    * `started`: the group is named by its new first line. */
  private def takenBackFrom(first: Long)(entry: LogEntryWire): LogEntryWire =
    entry.copy(takenBack = Some(LogTakenBackWire(first,
      takenBackByRed.header)))

  test("a group the reader opened stays open when a Redo restores its first line"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    toggle(content).click()
    pane.show("g|red",
      Vector(setup, started, takenBackFrom(6L)(moved)), Map.empty)
    assertEquals(toggle(content).getAttribute("aria-expanded"), "true")
    assertEquals(groupLines(content).children.length, 1)

  test("a group the reader left closed stays closed when a Redo restores its first line"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    pane.show("g|red",
      Vector(setup, started, takenBackFrom(6L)(moved)), Map.empty)
    assertEquals(toggle(content).getAttribute("aria-expanded"), "false")

  test("a toggle still works after a Redo changed the group's id"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    toggle(content).click()
    pane.show("g|red",
      Vector(setup, started, takenBackFrom(6L)(moved)), Map.empty)
    toggle(content).click()
    assertEquals(toggle(content).getAttribute("aria-expanded"), "false")
    toggle(content).click()
    assertEquals(toggle(content).getAttribute("aria-expanded"), "true")

  test("a group formed by taking back again opens when a line of it was open"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    toggle(content).click()
    pane.show("g|red",
      Vector(setup, started, takenBackFrom(6L)(moved)), Map.empty)
    // An undo again: the group's first line is `started`'s once more, so
    // the group's id differs from the one the reader last saw.
    pane.show("g|red", Vector(setup, takenBack(started), takenBack(moved)),
      Map.empty)
    assertEquals(toggle(content).getAttribute("aria-expanded"), "true")

  test("only the group the reader opened stays open through a redraw"):
    val content = TestLog.sizedBox(total = 0, height = 0, scrolled = 0)
    val pane = new GameLogPane(content)
    val later = takenBackFrom(8L)(_)
    pane.show("g|red", Vector(setup, takenBack(started), moved, later(movedAgain)),
      Map.empty)
    content.querySelectorAll(".log-taken-back-toggle").toVector
      .head.asInstanceOf[dom.html.Button].click()
    pane.show("g|red",
      Vector(setup, takenBack(started), moved, later(movedAgain),
        later(travelLine(9, "Travelled to Moonstone"))), Map.empty)
    val states = content.querySelectorAll(".log-taken-back-toggle").toVector
      .map(_.getAttribute("aria-expanded"))
    assertEquals(states, Vector("true", "false"))
