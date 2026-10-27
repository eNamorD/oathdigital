package oathdigital.frontend

import Dom.{all, click, one}
import oathdigital.protocol.projection.LogKind
import org.scalajs.dom

/** On a phone the Actions and Log panes share the bottom of the table and a
  * switch in each header picks which shows. The stylesheet decides where the
  * switch appears; this drives the attribute it reads and the count the Log
  * side carries. Runs the real shell in `TestBrowser`.
  */
class PhonePaneSwitchSuite extends munit.FunSuite:
  private def entry(sequence: Long, text: String) =
    TestLog.textEntry(sequence, LogKind.Action, text)

  private val entries = Vector(entry(3, "Started Travel"),
    entry(5, "Started Search"))

  private def withShell(store: Option[LogStore] = None)
      (body: (TestBrowser, GameTableShell) => Unit): Unit =
    val browser = new TestBrowser()
    val shell = new GameTableShell(browser.mount, developmentTools = false,
      logStore = store)
    try body(browser, shell)
    finally
      shell.dispose()
      browser.close()

  private def table(browser: TestBrowser): dom.Element =
    one(browser.mount, ".game-table")

  /** The Log side of the switch in the pane `pane` heads. */
  private def logTab(browser: TestBrowser, pane: String): dom.Element =
    one(browser.mount, s""".pane-$pane .pane-switch-option[aria-controls="log-pane"]""")

  private def actionsTab(browser: TestBrowser, pane: String): dom.Element =
    one(browser.mount,
      s""".pane-$pane .pane-switch-option[aria-controls="actions-pane"]""")

  private def count(browser: TestBrowser): String =
    one(logTab(browser, "actions"), ".pane-switch-count").textContent

  test("both bottom panes head a switch, and Actions shows first"):
    withShell() { (browser, _) =>
      assertEquals(all(browser.mount, ".pane-switch").size, 2)
      assertEquals(table(browser).getAttribute("data-phone-pane"), "actions")
      assertEquals(actionsTab(browser, "log").getAttribute("aria-selected"),
        "true")
      click(logTab(browser, "actions"))
      assertEquals(table(browser).getAttribute("data-phone-pane"), "log")
      assertEquals(logTab(browser, "log").getAttribute("aria-selected"), "true")
      click(actionsTab(browser, "log"))
      assertEquals(table(browser).getAttribute("data-phone-pane"), "actions")
    }

  test("the Log side counts the events the viewer has not seen"):
    val store = new FakeLogStore()
    store.write("oath.log.seen.g.red", "3")
    withShell(Some(store)) { (browser, shell) =>
      shell.showLog("g", "red", entries, Map.empty)
      assertEquals(count(browser), "1")
      assertEquals(logTab(browser, "actions").getAttribute("aria-label"),
        "Log, 1 new")
      shell.showLog("g", "red", entries :+ entry(8, "Started Muster"),
        Map.empty)
      assertEquals(count(browser), "2")
      click(logTab(browser, "actions"))
      assertEquals(count(browser), "")
      // While the log is in front, what arrives is seen as it arrives.
      shell.showLog("g", "red", entries :+ entry(8, "Started Muster") :+
        entry(9, "Started Trade"), Map.empty)
      assertEquals(count(browser), "")
    }

  test("a seat with no stored place has missed nothing"):
    withShell(Some(new FakeLogStore())) { (browser, shell) =>
      shell.showLog("g", "red", entries, Map.empty)
      assertEquals(count(browser), "")
    }

  test("a decision parked while the log is in front marks Actions"):
    withShell() { (browser, shell) =>
      def show(decision: String): Unit = shell.update("g", decision,
        dom.document.createElement("div"), dom.document.createElement("div"),
        dom.document.createElement("div"), dom.document.createElement("div"))
      show("first")
      click(logTab(browser, "actions"))
      show("first")
      assert(!table(browser).hasAttribute("data-actions-fresh"))
      show("second")
      assert(table(browser).hasAttribute("data-actions-fresh"))
      click(actionsTab(browser, "log"))
      assert(!table(browser).hasAttribute("data-actions-fresh"))
    }

  test("the Log pane's own button opens the whole log"):
    withShell() { (browser, _) =>
      click(one(browser.mount, ".log-expand"))
      assert(!one(browser.mount, ".log-overlay").hasAttribute("hidden"))
    }
