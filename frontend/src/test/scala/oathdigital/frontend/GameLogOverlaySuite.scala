package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js
import oathdigital.protocol.projection.{LogEntryWire, LogSpanWire}

/** The Log pane's heading opens the whole log over the table (spec,
  * "Overlay"), and the shell keeps each seat's place (spec, "Divider").
  * Runs the real shell in `TestBrowser`, which stubs what the map needs.
  */
class GameLogOverlaySuite extends munit.FunSuite:
  private final class FakeStore(initial: Map[String, String]) extends LogStore:
    var values = initial
    def read(key: String): Option[String] = values.get(key)
    def write(key: String, value: String): Unit = values += key -> value

  private def entry(sequence: Long, kind: String, depth: Int, text: String) =
    LogEntryWire(sequence, 0, kind, depth, Vector(LogSpanWire("text", text)))

  private val entries = Vector(entry(0, "round", 0, "Setup"),
    entry(3, "turn", 0, "Red's turn"), entry(5, "action", 1, "Started Travel"))

  private def press(node: dom.Element, key: String): Unit =
    node.dispatchEvent(new dom.KeyboardEvent("keydown",
      js.Dynamic.literal(key = key, bubbles = true)
        .asInstanceOf[dom.KeyboardEventInit]))

  private def click(node: dom.Element): Unit =
    node.dispatchEvent(new dom.MouseEvent("click",
      new dom.MouseEventInit { bubbles = true }))

  private def withShell(store: Option[LogStore] = None)
      (body: (TestBrowser, GameTableShell) => Unit): Unit =
    val browser = new TestBrowser()
    val shell = new GameTableShell(browser.mount, developmentTools = false,
      logStore = store)
    try body(browser, shell)
    finally
      shell.dispose()
      browser.close()

  private def heading(browser: TestBrowser): dom.html.Element =
    browser.mount.querySelector("#log-heading").asInstanceOf[dom.html.Element]

  private def overlay(browser: TestBrowser): dom.Element =
    browser.mount.querySelector(".log-overlay")

  test("the pane heading is a button that controls the overlay"):
    withShell() { (browser, _) =>
      val node = heading(browser)
      assertEquals(node.getAttribute("role"), "button")
      assertEquals(node.getAttribute("tabindex"), "0")
      assertEquals(node.getAttribute("aria-expanded"), "false")
      assertEquals(node.getAttribute("aria-controls"), "log-overlay")
      assert(overlay(browser).hasAttribute("hidden"))
      assertEquals(overlay(browser).getAttribute("role"), "dialog")
      assertEquals(overlay(browser).getAttribute("aria-labelledby"),
        "log-overlay-heading")
    }

  test("a click opens the whole log, with headlines as headings"):
    withShell() { (browser, shell) =>
      shell.showLog("g", "red", entries, Map.empty)
      click(heading(browser))
      assert(!overlay(browser).hasAttribute("hidden"))
      assertEquals(heading(browser).getAttribute("aria-expanded"), "true")
      val shown = overlay(browser).querySelectorAll("li")
      assertEquals(shown.length, 3)
      assertEquals(overlay(browser).querySelector("h3").textContent, "Setup")
      assertEquals(overlay(browser).querySelector("h4").textContent, "Red's turn")
      assertEquals(dom.document.activeElement,
        overlay(browser).querySelector(".log-overlay-content"))
    }

  test("Enter and Space on the heading open it"):
    Vector("Enter", " ").foreach { key =>
      withShell() { (browser, _) =>
        press(heading(browser), key)
        assert(!overlay(browser).hasAttribute("hidden"), key)
      }
    }

  test("Escape, Close and the scrim close it and return focus to the heading"):
    Vector[TestBrowser => Unit](
      browser => press(overlay(browser).querySelector(".log-overlay-content"), "Escape"),
      browser => click(overlay(browser).querySelector(".log-overlay-close")),
      browser => click(overlay(browser))).foreach { close =>
      withShell() { (browser, _) =>
        click(heading(browser))
        close(browser)
        assert(overlay(browser).hasAttribute("hidden"))
        assertEquals(heading(browser).getAttribute("aria-expanded"), "false")
        assertEquals(dom.document.activeElement, heading(browser))
      }
    }

  test("while it is open the table behind is inert, and an open card overlay closes first"):
    withShell() { (browser, _) =>
      val table = browser.mount.querySelector(".game-table")
      CardInspection.openText("Rules", Vector("A line"), heading(browser))
      assert(!browser.mount.querySelector(".card-overlay").hasAttribute("hidden"))
      click(heading(browser))
      assert(browser.mount.querySelector(".card-overlay").hasAttribute("hidden"))
      assert(table.hasAttribute("inert"))
      press(overlay(browser).querySelector(".log-overlay-content"), "Escape")
      assert(!table.hasAttribute("inert"))
    }

  test("a click inside the panel does not close it"):
    withShell() { (browser, shell) =>
      shell.showLog("g", "red", entries, Map.empty)
      click(heading(browser))
      click(overlay(browser).querySelector("li"))
      assert(!overlay(browser).hasAttribute("hidden"))
    }

  test("new entries reach the overlay while it is closed"):
    withShell() { (browser, shell) =>
      shell.showLog("g", "red", entries.take(2), Map.empty)
      shell.showLog("g", "red", entries, Map.empty)
      assertEquals(overlay(browser).querySelectorAll("li").length, 3)
    }

  test("the stored marker places the divider in the pane and the overlay alike"):
    val store = FakeStore(Map("oath.log.seen.g.red" -> "3"))
    withShell(Some(store)) { (browser, shell) =>
      shell.showLog("g", "red", Vector.empty, Map.empty)
      shell.showLog("g", "red", entries, Map.empty)
      val pane = browser.mount.querySelector(".pane-log")
      Vector(pane, overlay(browser)).foreach { list =>
        assertEquals(list.querySelectorAll("li").toVector.map(_.textContent),
          Vector("Setup", "Red's turn", "Since you last looked", "Started Travel"))
      }
    }

  test("a second at the end writes the marker; leaving the page flushes a pending one"):
    val store = FakeStore(Map.empty)
    withShell(Some(store)) { (browser, shell) =>
      shell.showLog("g", "red", entries, Map.empty)
      // jsdom lays nothing out, so every list is at its end.
      browser.tick()
      assertEquals(store.values, Map("oath.log.seen.g.red" -> "5"))
      shell.showLog("g", "red", entries :+ entry(8, "action", 1, "Started Search"),
        Map.empty)
      dom.window.dispatchEvent(new dom.Event("pagehide"))
      assertEquals(store.values, Map("oath.log.seen.g.red" -> "8"))
    }

  test("an observer seat keeps no marker and sees no divider"):
    val store = FakeStore(Map("oath.log.seen.g." -> "3"))
    withShell(Some(store)) { (browser, shell) =>
      shell.showLog("g", "", Vector.empty, Map.empty)
      shell.showLog("g", "", entries, Map.empty)
      assertEquals(browser.mount.querySelectorAll(".log-divider").length, 0)
      browser.tick()
      assertEquals(store.values, Map("oath.log.seen.g." -> "3"))
    }
