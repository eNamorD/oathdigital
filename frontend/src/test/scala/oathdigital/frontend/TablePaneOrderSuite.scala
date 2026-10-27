package oathdigital.frontend

import Dom.all

/** The parked decision is what a viewer reads first, so the Actions pane
  * leads the table's document order, as the stylesheet sets it at the top
  * or top left, and the Log comes last. Runs the real shell in
  * `TestBrowser`.
  */
class TablePaneOrderSuite extends munit.FunSuite:
  test("Actions is the first pane and the Log the last"):
    val browser = new TestBrowser()
    val shell = new GameTableShell(browser.mount, developmentTools = false,
      logStore = None)
    try
      assertEquals(all(browser.mount, ".game-table > .game-pane")
        .map(_.getAttribute("aria-labelledby")),
        Vector("actions-heading", "players-heading", "world-heading",
          "log-heading"))
    finally
      shell.dispose()
      browser.close()
