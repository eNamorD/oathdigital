# Game Log, Slice 3 (Reading Aids) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the log easy to return to and easy to read at length. A reader who comes back finds a "Since you last looked" line where they left off. A reader who has scrolled up is offered a "New" chip instead of being pulled down. The current turn's headline stays at the top of the pane. Clicking the pane heading opens the whole log in a full-height overlay.

**Architecture:** This slice is frontend-only; no server, wire or shared code changes.

- `GameLogPane` stays the one list component. It gains the divider, the chip, sticky-ready headline classes, a headings mode for the overlay, and a reading position that another list can restore.
- The overlay (`GameLogOverlay`) holds a second `GameLogPane` in headings mode. The shell feeds it the same entries as the pane, so both lists always hold the same children.
- A small `LogMarker` owns the `localStorage` marker behind a `LogStore` seam. A blocked store is `None`, so the jsdom credential-storage trap in `TestBrowser` stays untouched.
- `GameTableShell` wires all of this together: the heading becomes a button, and the shell reads the marker once per seat, watches both lists for "at the end", and flushes the marker on `pagehide`.

**Tech Stack:** Scala.js 3.9.0, scalajs-dom, munit on jsdom. Build through `./sbtw`. Impeccable (`~/.claude/skills/impeccable`) for the styling and the final polish and accessibility audit.

**Spec:** `docs/superpowers/specs/2026-09-25-game-log-design.md`. Before starting, read these sections: Decisions (rows 1, 3 and 4), Vocabulary ("Divider"), Client (Pane, Divider, Overlay, Accessibility, Fetching) and Testing (Frontend). Slice 2's plan (`docs/superpowers/plans/2026-09-26-game-log-slice2.md`) shows how the pane was built and polished.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects. An unused import or private member fails the build.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- `shared/src/main` may import only `oathdigital.protocol`. `application` must not import `persistence`, `serialization` or `server`. This slice touches none of them.
- Never touch the live database `var/oathdigital`. Browser checks use a scratch copy only.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`.
- Work in a git worktree. `EnterWorktree` branches from `origin`, which lags local `main`, so fast-forward the new branch to local `main` first. Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- The marker key is exactly `oath.log.seen.{gameId}.{seatId}`, and its value is a journal sequence written as decimal digits. The divider's text is exactly "Since you last looked". The chip's text is exactly "New".
- `TestBrowser` keeps throwing on `localStorage` and `sessionStorage`. Suites that need a store supply a fake `LogStore`; nothing lifts the trap.
- Entries are never re-rendered or reordered by this slice. The divider and the chip are client-only nodes and are never sent anywhere.
- DESIGN.md forbids `transition`, `animation` and resting shadows, and keeps saturated color for pieces only. Functional text holds the 11px floor.
- UI work follows Impeccable. Run `~/.claude/skills/impeccable/scripts/impeccable context` once per session with cwd at the worktree root. Read `reference/craft-floor.md` immediately before any CSS edit. Verify in one batched pass plus at most one confirming pass.
- Baselines at `3f9448fe`: 1809 server tests and 464 frontend tests. Record the counts from your first full run and use those if they differ.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

### Decisions this plan makes that the spec left open

These were settled while planning (2026-09-26). Task 5 records each one in the spec.

1. **What the marker holds.** The marker holds the sequence of the last entry the client holds, written once a list has stayed at the end for one second. On `pagehide`, the client writes only a mark that is already pending: the reader is at the end, but the second has not passed. Unload never moves the marker past what the reader reached.
2. **Who is an observer.** The frontend has no observer seat. A trusted table has an empty seat id until the server names its seat, so an empty seat id counts as the observer: it reads no marker, writes none and shows no divider.
3. **Where the divider goes.** The divider is placed only when the list is drawn in full: when a session loads, or when the log resets. Appends never add, move or remove it. The pane and the overlay place it at the same point, from the same marker read once per seat.
4. **A chip in both lists.** The overlay's list is a `GameLogPane` too, so it gets the same "New" chip at its own bottom edge.
5. **How the headline sticks.** The list stays flat, and every turn headline in the pane is `position: sticky`. The latest headline that has passed the top covers the earlier ones, because later siblings paint above them. That headline belongs to the lines the reader sees. The overlay does the same with round headlines. No grouping markup is added, so an append stays a single `appendChild`.
6. **Where the overlay opens.** Both lists hold the same children, so the pane's reading position is an index into its children. That index is the first child, not a sticky headline, whose bottom is below the list's top edge. The overlay scrolls that child to just under its own stuck round headline. A pane at the end opens the overlay at the end.
7. **Overlay type size.** The overlay is for reading the whole log, so its list reads at 13px instead of the pane's 11px floor. The die chips scale with it, since they are sized in `em`.
8. **Focus in the overlay.** On open, focus goes to the overlay's scrolling list region, so that arrow keys and Page Down scroll the log at once. Close is one Shift+Tab away, in the header. Escape, a click on the scrim and Close all shut the overlay and return focus to the pane heading. This differs from the card overlay, which focuses its Close button, because the log overlay is for scrolling.
9. **How the heading opens it.** The pane heading gets `role="button"`, `tabindex="0"`, `aria-expanded` and `aria-controls="log-overlay"`. Enter and Space open the overlay.

### Facts this plan relies on (verified against the code on 2026-09-26, at `3f9448fe`)

- **The pane.** `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala` (67 lines) is `final class GameLogPane(content: dom.html.Element)`.
  - `show(sessionKey, entries, colors)` appends when the new entries extend the ones shown, and otherwise redraws the list in full.
  - It follows the end only when the reader was at the end, where `GameLogPane.atEnd` allows 4px of slack.
  - `GameLogPane.item(entry, colors)` builds an `li` with the classes `log-entry log-headline|log-line log-{kind}` and a `title` of `"{sequence}.{ordinal}"`.
  - `GameLogPane.placeholder` is the "Setup" round headline.
- **The shell.** `GameTableShell` (`GameTableShell.scala`, 157 lines) builds the four panes through its private `Pane(id, title)` class. `Pane` exposes `node`, `header`, `heading: dom.html.Element` (with `tabIndex = -1`) and `content: dom.html.Div`.
  - The log pane is `new Pane("log", "Game Log")`, so its heading id is `log-heading`.
  - `showLog(sessionKey, entries, colors)` is called only from `TableScreen.scala:114`, with `s"${view.viewedGameId}|${view.viewedPlayerId}"`.
  - `dispose()` tears the shell down. The development panel's dialog pattern (hidden attribute, Escape, focus return) lives in the same file.
- **The card overlay.** `CardInspectionOverlay(root)` is an `aside.card-overlay` with `role="dialog"` and `aria-modal="true"`, appended to the mount. It uses the hidden attribute, and a click on the scrim or on Close dismisses it. Escape closes it, and focus returns to the opener when the opener is still in the document. Its suite dispatches `KeyboardEvent("keydown", {key, bubbles: true})` and `MouseEvent("click", {bubbles: true})`.
- **The seat.** `ServerModeUi.start` builds `new GameTableShell(mount, !trusted)`. On a trusted table the first player id is `""`, and `TableSession` fills it in when the server names the seat. The development table always has a seat, and a seat switch or session change resets the log (`TableSession.resetLog`).
- **The test browser.** `TestBrowser` (`frontend/src/test/scala/oathdigital/frontend/TestBrowser.scala`) makes `window.localStorage` and `window.sessionStorage` throw "Unexpected credential storage". It queues `window.setTimeout` callbacks, which `tick()` runs, and stubs `ResizeObserver`, which `MapViewport` and therefore `GameTableShell` need. `ServerModeUiSuite` drives the whole table through it and calls `tick()` for the poll timer. A new timer in that queue would change which callback `tick()` runs, and that is why a blocked store must schedule nothing.
- **Helpers.** `ServerUiSupport` has `element(tag, className)`, `text(tag, className, value)` and `button(label, className)`.
- **Styles.** `frontend/styles.css`:
  - The log rules are at lines 845–858. `.pane-content` has 12px padding, 8px in the narrowest layout, and `overflow: auto`. The pane fill is `#211f1b`.
  - The Control face is the selector list `.map-control, .dev-toggle, .dev-close` (line 687), with its hover list at lines 692–693.
  - The card overlay's scrim is `background: #0b0a08e8` at `z-index: 300` (line 892).
  - Brass Label is `#d5bd8f`. The Brass Line border is `#7c694b`, and Bright Cream is `#fff2d6`.
- **DESIGN.md** names two things that may read as above the table: the developer panel's lift and the card overlay's scrim ("The Flat Board Rule"). The log overlay uses the scrim, so Task 5 updates that rule and the "Don't" list to name both overlays.

## File Structure

- Create `frontend/src/main/scala/oathdigital/frontend/LogMarker.scala`: `LogStore`, the seam over `localStorage`, and its `browser` constructor. Also `LogMarker`, which reads the marker per seat and writes it after one second at the end or on flush.
- Modify `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`. It gains the divider, the chip, headings mode, the reading position, a scroll callback, `last` and `dispose`.
- Create `frontend/src/main/scala/oathdigital/frontend/GameLogOverlay.scala`, the full-height dialog around a headings-mode `GameLogPane`.
- Modify `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala`. The heading becomes a button, and the shell gains the overlay, the marker, the `pagehide` flush and the new `showLog(gameId, seatId, entries, colors)`.
- Modify `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala`, the one `showLog` call.
- Modify `frontend/styles.css`: sticky headlines, divider, chip, overlay and the heading's affordance.
- Tests:
  - Create `LogMarkerSuite`.
  - Extend `GameLogPaneSuite`.
  - Create `GameLogOverlaySuite`, which drives the shell in `TestBrowser`.
- Docs:
  - `DESIGN.md`: Panes, Shadow Vocabulary and Named Rules, and the Don't list.
  - The spec: status note and Resolved decisions.
  - `docs/ROADMAP.md`: Slice 3 done.

---

### Task 1: The marker and its store

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/LogMarker.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/LogMarkerSuite.scala`

**Interfaces:**
- Produces:
  - `private[frontend] trait LogStore { def read(key: String): Option[String]; def write(key: String, value: String): Unit }`
  - `LogStore.browser: Option[LogStore]`
  - `private[frontend] final class LogMarker(store: Option[LogStore], schedule: (() => Unit) => (() => Unit) = LogMarker.afterASecond)`, with these methods:
    - `open(gameId: String, seatId: String): Option[Long]`
    - `observe(atEnd: Boolean, last: Option[Long]): Unit`
    - `flush(): Unit`
  - `LogMarker.key(gameId: String, seatId: String): String`

- [ ] **Step 1: Write the failing tests**

```scala
package oathdigital.frontend

class LogMarkerSuite extends munit.FunSuite:
  private final class FakeStore(initial: Map[String, String] = Map.empty) extends LogStore:
    var values = initial
    def read(key: String): Option[String] = values.get(key)
    def write(key: String, value: String): Unit = values += key -> value

  /** Stands in for `setTimeout`: runs nothing until `fire`. */
  private final class FakeTimer:
    var pending = Vector.empty[Option[() => Unit]]
    def schedule(run: () => Unit): () => Unit =
      pending :+= Some(run)
      val index = pending.size - 1
      () => pending = pending.updated(index, None)
    def live: Int = pending.count(_.nonEmpty)
    def fire(): Unit =
      val due = pending.flatten
      pending = Vector.empty
      due.foreach(_())

  test("the key names the game and the seat"):
    assertEquals(LogMarker.key("g1", "red-exile"), "oath.log.seen.g1.red-exile")

  test("opening a seat reads its stored sequence"):
    val store = FakeStore(Map("oath.log.seen.g1.red" -> "42"))
    assertEquals(new LogMarker(Some(store)).open("g1", "red"), Some(42L))
    assertEquals(new LogMarker(Some(store)).open("g1", "blue"), None)

  test("a stored value that is not a sequence reads as no marker"):
    val store = FakeStore(Map("oath.log.seen.g1.red" -> "soon"))
    assertEquals(new LogMarker(Some(store)).open("g1", "red"), None)

  test("a second at the end writes the last sequence; leaving first writes nothing"):
    val store = FakeStore()
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    marker.open("g1", "red")
    marker.observe(atEnd = true, last = Some(7))
    marker.observe(atEnd = true, last = Some(9))
    assertEquals(timer.live, 1)
    marker.observe(atEnd = false, last = Some(9))
    assertEquals(timer.live, 0)
    timer.fire()
    assertEquals(store.values, Map.empty[String, String])
    marker.observe(atEnd = true, last = Some(9))
    timer.fire()
    assertEquals(store.values, Map("oath.log.seen.g1.red" -> "9"))

  test("the write takes the newest sequence seen while waiting"):
    val store = FakeStore()
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    marker.open("g1", "red")
    marker.observe(atEnd = true, last = Some(7))
    marker.observe(atEnd = true, last = Some(12))
    timer.fire()
    assertEquals(store.values, Map("oath.log.seen.g1.red" -> "12"))

  test("flush writes a pending mark at once, and nothing when none is pending"):
    val store = FakeStore()
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    marker.open("g1", "red")
    marker.flush()
    assertEquals(store.values, Map.empty[String, String])
    marker.observe(atEnd = true, last = Some(5))
    marker.flush()
    assertEquals(store.values, Map("oath.log.seen.g1.red" -> "5"))
    assertEquals(timer.live, 0)

  test("opening another seat drops the first seat's pending mark"):
    val store = FakeStore()
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    marker.open("g1", "red")
    marker.observe(atEnd = true, last = Some(5))
    marker.open("g1", "blue")
    timer.fire()
    assertEquals(store.values, Map.empty[String, String])

  test("an observer, with no seat, reads nothing and schedules nothing"):
    val store = FakeStore(Map("oath.log.seen.g1." -> "3"))
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    assertEquals(marker.open("g1", ""), None)
    marker.observe(atEnd = true, last = Some(5))
    assertEquals(timer.live, 0)

  test("with no store there is no marker and no timer"):
    val timer = FakeTimer()
    val marker = new LogMarker(None, timer.schedule)
    assertEquals(marker.open("g1", "red"), None)
    marker.observe(atEnd = true, last = Some(5))
    assertEquals(timer.live, 0)

  test("a browser that refuses storage gives no store"):
    val browser = new TestBrowser()
    try assertEquals(LogStore.browser, None)
    finally browser.close()
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.LogMarkerSuite"`
Expected: FAIL. The compiler reports `Not found: type LogStore` and `Not found: LogMarker`.

- [ ] **Step 3: Write the implementation**

```scala
package oathdigital.frontend

import org.scalajs.dom
import scala.util.Try

/** Where the log's last-looked marker lives (spec, "Divider"). A seam so the
  * suites can supply a store: the jsdom harness makes the real one throw.
  */
private[frontend] trait LogStore:
  def read(key: String): Option[String]
  def write(key: String, value: String): Unit

private[frontend] object LogStore:
  /** The browser's `localStorage`, or nothing when the browser refuses it.
    * A refused store means no divider, never a failure.
    */
  def browser: Option[LogStore] =
    Try(dom.window.localStorage).toOption.filter(_ != null).map(storage =>
      new LogStore:
        def read(key: String): Option[String] =
          Try(Option(storage.getItem(key))).toOption.flatten
        def write(key: String, value: String): Unit =
          Try(storage.setItem(key, value)).getOrElse(()))

/** The reader's place in one seat's log: the sequence of the last entry held
  * once a list has stayed at the end for a second. A seat with no id is an
  * observer and keeps no place.
  */
private[frontend] final class LogMarker(store: Option[LogStore],
    schedule: (() => Unit) => (() => Unit) = LogMarker.afterASecond):
  private var key = Option.empty[String]
  private var latest = Option.empty[Long]
  private var cancel = Option.empty[() => Unit]

  /** Starts on a seat's log and returns the sequence stored for it. */
  def open(gameId: String, seatId: String): Option[Long] =
    stop()
    latest = None
    key = Option.when(seatId.nonEmpty && store.nonEmpty)(LogMarker.key(gameId, seatId))
    for
      name <- key
      value <- store.flatMap(_.read(name))
      sequence <- value.toLongOption
    yield sequence

  /** Called whenever a list moves or grows. The second starts when a list
    * reaches the end and is not restarted by later entries; leaving the end
    * cancels it.
    */
  def observe(atEnd: Boolean, last: Option[Long]): Unit =
    latest = last
    if !atEnd || last.isEmpty then stop()
    else if cancel.isEmpty && key.nonEmpty then
      cancel = Some(schedule(() => { cancel = None; write() }))

  /** On unload: a mark still waiting for its second is written now. */
  def flush(): Unit =
    if cancel.nonEmpty then
      stop()
      write()

  private def stop(): Unit =
    cancel.foreach(_())
    cancel = None

  private def write(): Unit =
    for name <- key; target <- store; sequence <- latest do
      target.write(name, sequence.toString)

private[frontend] object LogMarker:
  def key(gameId: String, seatId: String): String = s"oath.log.seen.$gameId.$seatId"

  def afterASecond(run: () => Unit): () => Unit =
    val handle = dom.window.setTimeout(() => run(), 1000)
    () => dom.window.clearTimeout(handle)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.LogMarkerSuite"`
Expected: PASS, 10 tests.

If `Try(dom.window.localStorage)` does not catch the harness's throw, that is because the getter throws a JavaScript `Error`. Scala.js wraps it as `js.JavaScriptException`, which is `NonFatal`, so `Try` does catch it. Check that the harness is the one in use before changing the code.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/LogMarker.scala frontend/src/test/scala/oathdigital/frontend/LogMarkerSuite.scala
git commit -m "feat(log): keep each seat's last-looked place in the browser"
```

---

### Task 2: The pane's divider, chip, headings and reading position

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala` (the whole file)
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`

**Interfaces:**
- Consumes: nothing from Task 1. The shell connects the two in Task 3.
- Produces:
  - `private[frontend] final class GameLogPane(content: dom.html.Element, headings: Boolean = false, scrolled: () => Unit = () => ())`, with these members:
    - `show(sessionKey: String, entries: Vector[LogEntryWire], colors: Map[String, String], since: Option[Long] = None): Unit`
    - `atEnd: Boolean`
    - `last: Option[Long]`
    - `position: Option[Int]`
    - `restore(at: Option[Int]): Unit`
    - `dispose(): Unit`
  - CSS class names later tasks style:
    - `log-divider` on the divider `li`.
    - `log-new` on the chip `button`.
    - `log-heading` on the `h3`/`h4` inside an overlay headline.

- [ ] **Step 1: Write the failing tests**

Add these to `GameLogPaneSuite`, after the existing tests. Also add `victory` to the fixtures at the top, and the `place` and `press` helpers:

```scala
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

  test("a reader at the end is followed and never shown the chip"):
    val content = box(500, 100, 400)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn), Map.empty)
    content.scrollTop = 400
    pane.show("g|red", Vector(setup, turn, travel), Map.empty)
    assert(content.querySelector(".log-new").hasAttribute("hidden"))

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

  test("the reading position is the first line below the top edge, skipping stuck headlines"):
    val content = box(500, 100, 200)
    val pane = new GameLogPane(content)
    pane.show("g|red", Vector(setup, turn, travel), Map.empty)
    content.scrollTop = 200
    place(content, 0, 100)
    val shown = items(content)
    place(shown(0), -40, -20)
    place(shown(1), 0, 15) // the stuck turn headline
    place(shown(2), -10, 5)
    assertEquals(pane.position, Some(2))

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
```

The `items` helper returns only `li` elements, and the chip is a `button`, so the existing tests still count entries correctly. The divider is an `li`, so the tests above count it on purpose.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogPaneSuite"`
Expected: FAIL to compile, because `show` does not take `since`, and `last`, `position` and `restore` do not exist.

- [ ] **Step 3: Rewrite `GameLogPane.scala`**

```scala
package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.{LogEntryWire, LogSpanWire}
import ServerUiSupport._

/** One reading of the game log (spec, "Pane" and "Overlay"). The pane and the
  * overlay each hold one, fed the same entries, so both hold the same
  * children. Entries only ever arrive at the end, so a render appends what is
  * new rather than replacing the list. A reader at the end stays there; a
  * reader who has scrolled up stays put and is offered the "New" chip.
  *
  * `headings` makes headlines into headings, for the overlay: a screen reader
  * jumps between them there, while the pane keeps the table's outline short.
  */
private[frontend] final class GameLogPane(content: dom.html.Element,
    headings: Boolean = false, scrolled: () => Unit = () => ()):
  private val list = element("ol", "game-log")
  list.setAttribute("role", "log")
  list.setAttribute("aria-live", "polite")
  content.appendChild(list)
  private val chip = button("New", "log-new")
  chip.setAttribute("hidden", "")
  chip.onclick = _ => toEnd()
  content.appendChild(chip)
  /** The headline that sticks in this list: the turn in the pane, the round
    * in the overlay (spec, "Pane" and "Overlay"). */
  private val sticky = if headings then "log-round" else "log-turn"
  private var key = Option.empty[String]
  private var shown = Vector.empty[LogEntryWire]
  list.appendChild(headline(GameLogPane.placeholder, "round"))

  private val onScroll: dom.Event => Unit = _ =>
    if atEnd then chip.setAttribute("hidden", "")
    scrolled()
  content.addEventListener("scroll", onScroll)

  /** `since` is the stored marker. It places the divider only when the list
    * is drawn in full, so appends never move it. */
  def show(sessionKey: String, entries: Vector[LogEntryWire],
      colors: Map[String, String], since: Option[Long] = None): Unit =
    val appending = key.contains(sessionKey) && shown.nonEmpty &&
      entries.size >= shown.size && entries(shown.size - 1) == shown.last
    if appending then
      if entries.size > shown.size then
        val following = atEnd
        entries.drop(shown.size).foreach(entry => list.appendChild(item(entry, colors)))
        if following then toEnd() else chip.removeAttribute("hidden")
    else redraw(entries, colors, since)
    key = Some(sessionKey)
    shown = entries

  private def redraw(entries: Vector[LogEntryWire], colors: Map[String, String],
      since: Option[Long]): Unit =
    while list.firstChild != null do list.removeChild(list.firstChild)
    chip.setAttribute("hidden", "")
    if entries.isEmpty then list.appendChild(headline(GameLogPane.placeholder, "round"))
    val divideAt = since.map(marker => entries.indexWhere(_.sequence > marker))
      .filter(_ >= 0)
    entries.zipWithIndex.foreach { (entry, index) =>
      if divideAt.contains(index) then list.appendChild(GameLogPane.divider)
      list.appendChild(item(entry, colors))
    }
    divideAt.fold(toEnd())(_ => reveal(list.querySelector(".log-divider")))

  def atEnd: Boolean = GameLogPane.atEnd(content)

  def last: Option[Long] = shown.lastOption.map(_.sequence)

  /** Where the reader is: nothing at the end, otherwise the index among the
    * list's children of the first one below the top edge. A stuck headline
    * sits at the top edge whatever the scroll, so it is skipped. */
  def position: Option[Int] =
    if atEnd then None else
      val top = content.getBoundingClientRect().top
      Some(children.indexWhere(child => !child.classList.contains(sticky) &&
        child.getBoundingClientRect().bottom > top).max(0))

  /** Scrolls to a position another list reported (see `position`). */
  def restore(at: Option[Int]): Unit =
    at.flatMap(children.lift).fold(toEnd())(reveal)

  def dispose(): Unit = content.removeEventListener("scroll", onScroll)

  private def children: Vector[dom.Element] =
    (0 until list.children.length).map(list.children(_)).toVector

  private def toEnd(): Unit =
    chip.setAttribute("hidden", "")
    content.scrollTop = content.scrollHeight.toDouble

  /** Scrolls `node` to the top, just under the headline stuck above it. */
  private def reveal(node: dom.Element): Unit =
    val before = children.takeWhile(_ != node)
    val cover = before.findLast(_.classList.contains(sticky))
      .fold(0.0)(_.getBoundingClientRect().height)
    content.scrollTop = content.scrollTop + node.getBoundingClientRect().top -
      content.getBoundingClientRect().top - cover

  private def item(entry: LogEntryWire, colors: Map[String, String]): dom.Element =
    val node = GameLogPane.item(entry, colors)
    if entry.depth == 0 then headline(node, entry.kind) else node

  /** In headings mode the headline's spans move into an `h3`, or an `h4` for
    * a turn. */
  private def headline(node: dom.Element, kind: String): dom.Element =
    if headings then
      val heading = element(if kind == "turn" then "h4" else "h3", "log-heading")
      while node.firstChild != null do heading.appendChild(node.firstChild)
      node.appendChild(heading)
    node

private[frontend] object GameLogPane:
  /** Within a few pixels of the end counts as at the end. */
  private val Slack = 4.0

  def atEnd(content: dom.html.Element): Boolean =
    content.scrollHeight - content.scrollTop - content.clientHeight <= Slack

  /** Before the first entry arrives the log is the game's first headline. */
  def placeholder: dom.Element =
    text("li", "log-entry log-headline log-round", "Setup")

  /** Client-only: where the reader left off (spec, "Divider"). */
  def divider: dom.Element = text("li", "log-divider", "Since you last looked")

  def item(entry: LogEntryWire, colors: Map[String, String]): dom.Element =
    val depth = if entry.depth == 0 then "log-headline" else "log-line"
    val node = element("li", s"log-entry $depth log-${entry.kind}")
    node.setAttribute("title", s"${entry.sequence}.${entry.ordinal}")
    entry.spans.foreach(span => node.appendChild(spanNode(span, colors)))
    node

  private def spanNode(span: LogSpanWire, colors: Map[String, String])
      : dom.Node = span.kind match
    case "player" => text("span", "log-player player-ref " +
      span.id.flatMap(colors.get).getOrElse(PlayerColorCss.neutral), span.text)
    case "card" | "site" | "amount" | "cost" =>
      text("span", s"log-${span.kind}", span.text)
    // The same die chips the table draws; the faces ride in `id`.
    case "dice" => span.id.fold[dom.Node](dom.document.createTextNode(span.text)) {
      faces =>
        val dice = DieFace.roll(faces.split(" ").toVector.filter(_.nonEmpty))
        dice.setAttribute("class",
          s"die-faces log-dice log-dice-${span.unit.getOrElse("unknown")}")
        dice
    }
    case _ => dom.document.createTextNode(span.text)
```

Keep the existing redraw rule: the list is drawn in full on a new session key, on the first entries after an empty log, and when the new entries do not extend the shown ones. A show that repeats the same entries changes nothing, as before.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogPaneSuite"`
Expected: PASS. That is the 6 existing tests and the 12 new ones.

If the "reading position" test finds index 1, the sticky skip is not working: check that `sticky` is `log-turn` when `headings` is false. If the restore test is off by 18, the cover lookup did not find the round headline: `reveal` must search the children before `node`, not the ones after it.

- [ ] **Step 5: Run the whole frontend suite**

Run: `./sbtw "frontend/test"`
Expected: all pass. `GameTableShell` still calls `show(sessionKey, entries, colors)` with no `since`, so it compiles unchanged.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala
git commit -m "feat(log): the pane marks where the reader left off and offers new entries"
```

**Checkpoint A.** Dispatch a review subagent on Tasks 1 and 2. Give it the spec's Client section, this plan's Decisions 1 to 6, and `git diff 3f9448fe..HEAD -- frontend`. It checks that the divider is placed only on a full redraw, that a blocked store can never schedule a timer, and that `position` and `restore` round-trip between two lists that hold the same children.

---

### Task 3: The overlay, and the shell that wires the reading aids

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/GameLogOverlay.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala`: the constructor, the log pane lines 30–31, `showLog`, and `dispose`
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala:114-117`
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogOverlaySuite.scala`

**Interfaces:**
- Consumes:
  - `LogStore`, `LogStore.browser` and `LogMarker(store)` with `open`, `observe` and `flush`, from Task 1.
  - `GameLogPane(content, headings, scrolled)` with `show(…, since)`, `atEnd`, `last`, `position`, `restore` and `dispose`, from Task 2.
- Produces:
  - `private[frontend] final class GameLogOverlay(root: dom.Element, scrolled: () => Unit, closed: () => Unit)`, with these members:
    - `show(sessionKey, entries, colors, since)`
    - `open(position: Option[Int], origin: dom.html.Element): Unit`
    - `hide(): Unit`
    - `isOpen: Boolean`
    - `atEnd: Boolean`
    - `dispose(): Unit`
  - `GameTableShell(mount, developmentTools = true, logStore: Option[LogStore] = LogStore.browser)`.
  - `GameTableShell.showLog(gameId: String, seatId: String, entries: Vector[LogEntryWire], colors: Map[String, String]): Unit`.
  - These classes and ids:
    - `aside.log-overlay#log-overlay` holds `div.log-overlay-panel`.
    - Inside the panel sit a `div.pane-header` with `h2#log-overlay-heading` and `button.log-overlay-close`, then `div.pane-content.log-overlay-content`.

- [ ] **Step 1: Write the failing tests**

```scala
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogOverlaySuite"`
Expected: FAIL to compile, because `GameTableShell` takes no `logStore` and `showLog` takes a session key.

- [ ] **Step 3: Write `GameLogOverlay.scala`**

```scala
package oathdigital.frontend

import org.scalajs.dom
import oathdigital.protocol.projection.LogEntryWire
import ServerUiSupport._

/** The whole log, full height over the table (spec, "Overlay"). The card
  * overlay's idiom: a scrim, a pane-fill panel, and Escape, Close or a click
  * on the scrim returning focus to the control that opened it. It is fed
  * every entry the pane is, so it is current whenever it opens. Focus lands
  * on the list, not on Close: the overlay exists to be scrolled.
  */
private[frontend] final class GameLogOverlay(root: dom.Element,
    scrolled: () => Unit, closed: () => Unit):
  private val node = element("aside", "log-overlay").asInstanceOf[dom.html.Element]
  node.id = "log-overlay"
  node.setAttribute("hidden", "")
  node.setAttribute("role", "dialog")
  node.setAttribute("aria-modal", "true")
  node.setAttribute("aria-labelledby", "log-overlay-heading")
  private val panel = element("div", "log-overlay-panel")
  private val header = element("div", "pane-header")
  private val heading = text("h2", "pane-heading", "Game Log")
  heading.id = "log-overlay-heading"
  private val close = button("Close", "log-overlay-close")
  private val content = element("div", "pane-content log-overlay-content")
    .asInstanceOf[dom.html.Element]
  content.tabIndex = 0
  content.setAttribute("aria-label", "Game Log contents")
  header.appendChild(heading)
  header.appendChild(close)
  panel.appendChild(header)
  panel.appendChild(content)
  node.appendChild(panel)
  root.appendChild(node)
  private val log = new GameLogPane(content, headings = true, scrolled)
  private var opener = Option.empty[dom.html.Element]

  def isOpen: Boolean = !node.hasAttribute("hidden")

  /** A closed overlay is never at the end: only what the reader sees counts. */
  def atEnd: Boolean = isOpen && log.atEnd

  def show(sessionKey: String, entries: Vector[LogEntryWire],
      colors: Map[String, String], since: Option[Long]): Unit =
    log.show(sessionKey, entries, colors, since)

  /** Opens where the pane is (see `GameLogPane.position`). */
  def open(position: Option[Int], origin: dom.html.Element): Unit =
    opener = Some(origin)
    node.removeAttribute("hidden")
    log.restore(position)
    content.focus()

  def hide(): Unit =
    if isOpen then
      node.setAttribute("hidden", "")
      // The opener is the pane heading, which the shell never rebuilds; the
      // guard matches the card overlay's in case that ever changes.
      opener.filter(dom.document.contains).foreach(_.focus())
      opener = None
      closed()

  private val dismiss: dom.MouseEvent => Unit = event =>
    if event.target == node || event.target == close then hide()

  private val escape: dom.KeyboardEvent => Unit = event =>
    if event.key == "Escape" && isOpen then { event.preventDefault(); hide() }

  node.addEventListener("click", dismiss)
  node.addEventListener("keydown", escape)

  def dispose(): Unit =
    node.removeEventListener("click", dismiss)
    node.removeEventListener("keydown", escape)
    log.dispose()
    node.remove()
```

- [ ] **Step 4: Wire the shell**

In `GameTableShell.scala`, change the constructor to:

```scala
private[frontend] final class GameTableShell(mount: dom.Element,
    developmentTools: Boolean = true,
    logStore: Option[LogStore] = LogStore.browser):
```

Replace `private val logPane = new GameLogPane(log.content)` (line 31) with:

```scala
  private val marker = new LogMarker(logStore)
  private val logPane = new GameLogPane(log.content, scrolled = () => watchLog())
  private val logOverlay = new GameLogOverlay(mount, () => watchLog(), () => {
    log.heading.setAttribute("aria-expanded", "false")
    watchLog()
  })
  // The heading opens the whole log (spec, "Overlay").
  log.heading.tabIndex = 0
  log.heading.setAttribute("role", "button")
  log.heading.setAttribute("aria-expanded", "false")
  log.heading.setAttribute("aria-controls", "log-overlay")
  private def openLog(): Unit =
    log.heading.setAttribute("aria-expanded", "true")
    logOverlay.open(logPane.position, log.heading)
    watchLog()
  log.heading.onclick = _ => openLog()
  log.heading.onkeydown = event =>
    if event.key == "Enter" || event.key == " " then
      event.preventDefault()
      openLog()
  private var logSeat = Option.empty[String]
  private var logSince = Option.empty[Long]
  /** Either list at its end for a second marks the log seen (spec,
    * "Divider"). */
  private def watchLog(): Unit =
    marker.observe(logPane.atEnd || logOverlay.atEnd, logPane.last)
  private val leaving: dom.Event => Unit = _ => marker.flush()
  dom.window.addEventListener("pagehide", leaving)
```

Replace `showLog` with:

```scala
  /** The log is not rebuilt with the other panes: it only grows. The marker
    * is read once per seat, so the pane and the overlay divide at the same
    * entry. */
  def showLog(gameId: String, seatId: String,
      entries: Vector[oathdigital.protocol.projection.LogEntryWire],
      colors: Map[String, String]): Unit =
    val sessionKey = s"$gameId|$seatId"
    if !logSeat.contains(sessionKey) then
      logSince = marker.open(gameId, seatId)
      logSeat = Some(sessionKey)
    logPane.show(sessionKey, entries, colors, logSince)
    logOverlay.show(sessionKey, entries, colors, logSince)
    watchLog()
```

In `dispose()`, before `table.remove()`, add:

```scala
    dom.window.removeEventListener("pagehide", leaving)
    logPane.dispose()
    logOverlay.dispose()
```

In `TableScreen.scala`, change the `shell.showLog(...)` call to:

```scala
    shell.showLog(view.viewedGameId, view.viewedPlayerId,
      view.viewedLog, projection.fold(Map.empty[String, String])(value =>
        value.players.map(player =>
          player.playerId -> PlayerColorCss.of(player.color)).toMap))
```

The Pane class's `heading` is already a `dom.html.Element`. Setting the log heading's `tabIndex` to 0 does not affect `PanelContent.replace`, because the log pane is never passed to it.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogOverlaySuite"`
Expected: PASS, 9 tests.

If "a second at the end" fails because `tick()` ran nothing, the marker's timer was not queued. Check that the store passed in is `Some` and the seat is not empty. If `ServerModeUiSuite` fails in the next step on a `tick()`, a timer has leaked into its queue. That suite's shell uses `LogStore.browser`, which must be `None` under `TestBrowser`, and a `LogMarker` without a store never schedules.

- [ ] **Step 6: Run the whole frontend suite and link**

Run: `./sbtw "frontend/test" "frontend/fastLinkJS"`
Expected: all pass. The count is the baseline plus 10 from Task 1, 12 from Task 2 and 9 from this task.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/GameLogOverlay.scala frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala frontend/src/main/scala/oathdigital/frontend/TableScreen.scala frontend/src/test/scala/oathdigital/frontend/GameLogOverlaySuite.scala
git commit -m "feat(log): the pane heading opens the whole log, and each seat keeps its place"
```

**Checkpoint B.** Dispatch a review subagent on Task 3. Give it the spec's Overlay and Accessibility sections, this plan's Decisions 7 to 9, and `git diff` of Task 3's commit. It checks the following:
- Focus returns to the heading on every close path.
- `aria-expanded` follows the overlay.
- The `pagehide` listener is removed in `dispose`.
- A closed overlay never counts as "at the end".

---

### Task 4: Style the reading aids

**Files:**
- Modify: `frontend/styles.css`: the log block after line 858, the Control face lists at lines 687 and 692–693, and a new overlay block after the card overlay's rules
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`, only if this task adds a class

This task follows Impeccable's `craft` discipline for a bounded styling change.

- [ ] **Step 1: Load the Impeccable playbook**

From the worktree root, run `~/.claude/skills/impeccable/scripts/impeccable context --target frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`, unless this session has already run it. Read DESIGN.md's Panes, Buttons (Control), Shadow Vocabulary and Named Rules sections. Immediately before the first CSS edit, read `reference/craft-floor.md`.

- [ ] **Step 2: Add the rules**

After the `.game-log .log-dice` rule, add:

```css
/* Reading aids. In the pane the current turn's headline stays at the top
   while its lines scroll under it; in the overlay the round's does. The
   list is flat, so the latest headline past the top covers the earlier
   ones. A stuck headline is painted on the pane fill so nothing shows
   through it. */
.pane-log .game-log .log-turn, .log-overlay .game-log .log-round {
  position: sticky; top: 0; z-index: 1; background: #211f1b; }
/* Where the reader left off: a Brass Label rule across the list. */
.game-log .log-divider { display: flex; align-items: center; gap: 8px;
  margin: 6px 0; color: #d5bd8f; font-weight: 750; letter-spacing: .06em;
  text-transform: uppercase; }
.game-log .log-divider::before, .game-log .log-divider::after {
  content: ""; flex: 1 1 0; border-top: 1px solid #7c694b; }
/* The New chip rides the list's bottom edge while the reader is scrolled up. */
.log-new { position: sticky; bottom: 0; z-index: 2; display: block;
  margin: 6px auto 0; }
.log-new[hidden] { display: none; }
/* The pane heading opens the whole log. */
.pane-log .pane-heading { cursor: pointer; }
.pane-log .pane-heading:hover { color: #fff2d6; }
```

Add `.log-new` and `.log-overlay-close` to the Control face selector at line 687 and to its hover list at lines 692–693.

After the card overlay's rules at the end of the file, add:

```css
/* The whole log, over the table with the card overlay's scrim: a pane-fill
   panel, full height, a reading measure wide. */
.log-overlay { position: fixed; z-index: 300; inset: 0; display: flex;
  justify-content: center; padding: 4vmin 16px; background: #0b0a08e8; }
.log-overlay[hidden] { display: none; }
.log-overlay-panel { display: flex; flex-direction: column; min-height: 0;
  width: min(46rem, 100%); background: #211f1b; border: 1px solid #40382b; }
.log-overlay .game-log { font-size: 13px; }
.log-overlay .log-heading { margin: 0; font: inherit; }
```

- [ ] **Step 3: Serve the worktree's build against a scratch database, and look once**

Copy `var/oathdigital` to a scratch directory, and never open the live one. Serve this worktree's build on port 8093:

```bash
./sbtw "runMain oathdigital.server.OathServer --database-path <scratch copy> --catalog-path docs/catalog/new-foundations-component-catalog.json --port 8093"
```

Run it in the background, and stop it at the end of this task. Open a seat on the six-player game `manual-1790205747051-112090`. In one batch, at the table's normal size, capture four views:
- The pane scrolled into the middle of a turn: the turn's headline is stuck at the top, and nothing shows above or through it.
- The pane after `localStorage.setItem('oath.log.seen.manual-1790205747051-112090.<seat>', '<a sequence halfway through>')` and a reload: the divider sits at the top, under the stuck headline.
- The pane scrolled up while new entries arrive: the chip shows at the bottom edge, and clicking it returns to the end. To produce entries, open the active seat in a second tab and take an action there. The first tab gets the entries at its next poll.
- The overlay open from the heading: the scrim, the panel, the stuck round headlines, and the same position as the pane.

- [ ] **Step 4: Fix what the look shows, in one batch**

Keep within DESIGN.md. Check these points, and fix only what is actually wrong:
- The sticky headline and the pane padding. Browsers keep a sticky box inside the scroll container's padding. If lines show in the 12px above the stuck headline, set `top` to minus the pane padding: `-12px`, and `-8px` inside the narrow layout's media query.
- The divider reads as a marker, not as an entry. It holds the 11px floor, and it stays inside the Brass tokens.
- The chip is a Control face and covers no text the reader needs. If it hides the last line when the reader is one line from the end, give the list `padding-bottom` equal to the chip's height, but only while the chip shows. Use `.game-log:has(+ .log-new:not([hidden]))`.
- The overlay's panel fills the height at 1440×900 and at a phone width, with no horizontal page scroll. At 13px the die chips stay inside the line.
- The heading reads as something that opens. If the hover color alone does not show it, use the brighter heading color at rest in the pane. Do not add a glyph: the spec makes the heading itself the control.
- There is no `transition`, `animation` or resting shadow, and no color outside the tokens.

Add one `GameLogPaneSuite` assertion for any class this step adds.

- [ ] **Step 5: Confirm once, then stop the server**

Take one confirming capture of the same views, then stop the scratch server and reset the browser viewport.

- [ ] **Step 6: Run the frontend tests and commit**

Run: `./sbtw "frontend/test"`
Expected: all pass.

```bash
git add frontend/styles.css
git commit -m "style(log): sticky headlines, the divider, the New chip and the log overlay"
```

Stage `GameLogPaneSuite.scala` too if Step 4 added an assertion.

---

### Task 5: Audit, polish, docs and gates

**Files:**
- Modify: `frontend/styles.css` and the Task 2–3 Scala files, only for findings
- Modify: `DESIGN.md`
- Modify: `docs/superpowers/specs/2026-09-25-game-log-design.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run Impeccable's audit on the reading aids**

Read `~/.claude/skills/impeccable/reference/audit.md` and apply its accessibility and interaction checks to the pane heading, the overlay, the divider and the chip. Use the markup and the Task 4 captures; do not start a second server. The checks must cover the following:
- Keyboard: Tab reaches the heading. Enter or Space opens the overlay. Focus lands on the list, and arrow keys scroll it. Shift+Tab reaches Close. Escape returns focus to the heading.
- Screen reader:
  - The dialog is named by "Game Log".
  - Headings in the overlay are `h3` and `h4`, with no `h3` or `h4` in the pane.
  - The list keeps `role="log"`.
  - The divider is read as text.
- Contrast: the divider's Brass on the pane fill, and the chip's Control face.

Then read `reference/polish.md` and apply it once to the same four views. Fix the material findings in one batch. Add one test for any behavior a fix changes. Record anything you decide not to fix in the spec's Resolved decisions, with the reason.

- [ ] **Step 2: Update DESIGN.md**

- In **Panes**, after the Log pane sentence, describe the reading aids:
  - The current turn's headline sticks to the pane's top on the pane fill.
  - "Since you last looked" is a Brass Label rule across the list.
  - A Control-face "New" chip rides the bottom edge while the reader is scrolled up.
  - The heading opens the log overlay.
- Add a **Log overlay** sentence: the card overlay's scrim, a pane-fill panel with a Line-pane border at a 46rem measure, and a pane header with Close. The list reads at 13px with the round headlines stuck.
- In the **Shadow Vocabulary**, change "the card inspection overlay darkens the table" to name both overlays. Do the same for "The Flat Board Rule" and the "Don't" item "only the card overlay scrims".
- In **Buttons**, add "the log's New chip" to the Control face's list.

- [ ] **Step 3: Run every gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: all pass. The server count is unchanged from the baseline. The frontend count is the baseline plus this plan's new tests.
Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both clean.

- [ ] **Step 4: Amend the spec**

Add to the status block: "Amended 2026-09-26 by the third slice's plan: where the marker, the divider and the overlay's position come from is recorded under 'Resolved decisions'." Then add a paragraph under Resolved decisions, "The third slice (2026-09-26) settled these:". Under it, record this plan's Decisions 1 to 9 in the spec's own prose style, one bullet each, together with anything Task 5 Step 1 chose not to fix.

- [ ] **Step 5: Update the roadmap and commit**

In `docs/ROADMAP.md`'s "Player-facing action history", tick the Slice 3 item. Add a "Slice 3 is done (2026-09-26)." paragraph in the style of Slice 2's, naming the overlay, the divider and its per-seat marker, the New chip and the sticky headline.

```bash
git add DESIGN.md docs/superpowers/specs/2026-09-25-game-log-design.md docs/ROADMAP.md
git commit -m "docs: mark the game log's third slice delivered"
```

Commit any Step 1 fixes before this, as `fix(log): …` or `style(log): …`, staging only the files they changed.

**Checkpoint C.** Dispatch a review subagent on the whole branch: `git diff 3f9448fe..HEAD`. It checks each spec requirement in "Client" and in the Testing section's frontend list against a test or a capture, and it confirms that DESIGN.md matches the shipped CSS.

---

## Self-review notes

- **Spec coverage (Slice 3):** each item and the task that delivers it.
  - The overlay opens on a click or Enter on the heading. It has a dim scrim, a pane-fill panel and `role="dialog"`. Escape closes it and returns focus. It holds the whole log with sticky round headlines, opens at the pane's position with the divider included, and the heading carries `role="button"` and `aria-expanded`. Tasks 2 and 3 build it, and Task 4 styles it.
  - Divider:
    - Reads `oath.log.seen.{gameId}.{seatId}`, inserts before the first newer entry, and scrolls it to the top; otherwise opens at the bottom: Tasks 1 and 2.
    - Writes after one second at the end, in the pane or the overlay, and on unload: Tasks 1 and 3.
    - Observer: Decision 2, tested in Tasks 1 and 3.
    - A blocked store degrades to no divider: Task 1's `LogStore.browser` test.
    - `TestBrowser` is left alone.
  - The "New" chip, in the Control face, returns to the end on click: Tasks 2 and 4.
  - The sticky turn headline in the pane: Tasks 2 and 4.
  - Accessibility:
    - `h3`/`h4` in the overlay and plain items in the pane: Task 2.
    - `role="log"` and `aria-live` are kept.
    - `title` on every line stays as it is.
  - Frontend testing list:
    - Sticky structure: Task 2's class and heading tests.
    - Stick-to-bottom versus the chip: Task 2.
    - The divider with a fake store and a throwing store: Tasks 1, 2 and 3.
    - Overlay open, close, focus return and `aria-expanded`: Task 3.
    - Fetch-only-when-advanced is Slice 1's and unchanged.
- **Deliberately not here:** a server-side marker, click-to-highlight, filters and timestamps. All of these are out of scope in the spec.
- **Type consistency:**
  - `GameLogPane.show(sessionKey, entries, colors, since)` is the same in Tasks 2 and 3.
  - `LogMarker.open(gameId, seatId)`, `observe(atEnd, last)` and `flush()` are the same in Tasks 1 and 3.
  - `position: Option[Int]` and `restore(Option[Int])` are the same in Tasks 2 and 3.
  - `GameLogOverlay.show` takes `since` explicitly, with no default.
- **Risks called out in their steps:**
  - A timer leaking into `ServerModeUiSuite`'s queue (Task 3, Step 5).
  - A sticky headline leaving a padding gap (Task 4, Step 4).
  - The chip covering the last line (Task 4, Step 4).
