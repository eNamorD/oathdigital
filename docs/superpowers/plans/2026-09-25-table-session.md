# Table Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the session (state, loads, routing, polling, submit) out of `ServerModeUi.start` into `TableSession`, and the view into `TableScreen`, so `start` is composition only; then land two declared fixes.

**Architecture:** `TableSession` implements `SessionControls`, `FlowHost` and a new read trait `TableView`, and takes its browser effects through `Navigation` and `PollClock`, so a pure suite drives it with fakes. `TableScreen` renders from `TableView`. `ServerModeUi.start` reads the page, builds session, flow and screen, wires them, and opens the session. Tasks 1-2 change no behavior; Tasks 3-4 are the spec's two fixes.

**Tech Stack:** Scala 3, Scala.js, munit, jsdom (existing suite only). Build through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-09-25-table-session-design.md`

## Global Constraints

- Tasks 1 and 2 are behavior-preserving: every jsdom test in `ServerModeUiSuite` stays green unchanged. Tasks 3 and 4 change only what the spec's "Declared fixes" section names.
- Unchanged: `ServerModeUi.start`'s signature; `ServerSessionCoordinator`; `SnapshotPollingCoordinator`; `ActionControls`, `TableControls`, `SessionControls` (`TableControls.scala`); `FlowHost` and `ModifierFlow` (`ModifierFlow.scala`); every renderer; every existing suite.
- `TableView` member names are `trusted`, `viewedGameId`, `viewedPlayerId`, `viewedProjection`, `shownFailure`, `viewedDrafts`, `viewedConnection`, `controlsAvailable`, `viewedRawEvents`.
- `TableSession`'s screen callback is the constructor parameter `repaint: () => Unit`, not `redraw`: `FlowHost` already declares `redraw()`, which the session implements as `repaint()`. `BrowserNavigation`'s parameter is `leave: () => Unit`, not `startOver`, for the same reason. (The spec was amended with this plan.)
- Every `Future` callback in frontend main code uses `scala.scalajs.concurrent.JSExecutionContext.Implicits.queue`.
- The compiler runs with `-Wunused:imports,privates,locals,implicits,nowarn` and `-Werror`: remove an import the moved code no longer uses; a private member nothing reads fails the build.
- Gates: `./sbtw "frontend/test"` after each task; `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` and `python3 scripts/check-architecture.py` (800-line cap) in Task 5.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Never commit `node_modules` or `.tooling` symlinks; stage explicit paths only.

---

### Task 1: `TableSession`, `Navigation`, `TableView`, and the wiring

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/TableSession.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala` (the `TableView` trait only; Task 2 adds the class)
- Create: `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` (whole file; its current 289 lines are the source of every moved function)

**Interfaces:**
- Consumes: `GameClient` (`load`, `submit`, `preview`), `HttpGameClient.loadRawEventHistory`, `ServerSessionCoordinator`, `SnapshotPollingCoordinator(clock, requestPoll)`, `PollClock`, `BrowserPollClock`, `SessionDrafts`, `SessionControls`, `FlowHost`, `ModifierFlow(host)`, `ServerUiSupport.{queryParameter, freshGameId, updateUrl}`.
- Produces:
  - `trait Navigation { def showSession(gameId: String, playerId: String): Unit; def startOver(): Unit }`
  - `trait TableView` with the nine members in Global Constraints
  - `final class TableSession(client: GameClient, val trusted: Boolean, initialGameId: String, initialPlayerId: String, clock: PollClock, navigation: Navigation, repaint: () => Unit) extends SessionControls, FlowHost, TableView`, plus `visibilityChanged(hidden: Boolean): Unit` and `open(queryGameId: Option[String]): Unit`
  - `object TableSession { def needsSeatLink(trusted: Boolean, error: GameClientFailure): Boolean }`
  - `final class BrowserNavigation(leave: () => Unit) extends Navigation` in `ServerModeUi.scala`

- [ ] **Step 1: Write the failing suite**

Create `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => GameCommand, MajorActionPreviewRequest,
  MajorActionPreviewResponse, ModifierInvocation}

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.setTimeout

/** The table session over a fake client, a manual poll clock and a recording
  * navigation: which loads it makes, how it routes and fails, when it polls,
  * and what it shows. The jsdom suite proves the wiring; this proves the
  * session.
  */
class TableSessionSuite extends munit.FunSuite:
  private type Answer[A] = Either[GameClientFailure, A]

  /** Records every call and answers each through a promise, oldest first. */
  private final class FakeClient extends GameClient:
    var loads = Vector.empty[(String, String)]
    var submits = Vector.empty[(Long, GameCommand)]
    var previews = Vector.empty[(String, String, MajorActionPreviewRequest)]
    private var pendingLoads = Vector.empty[Promise[Answer[GameProjection]]]
    private var pendingSubmits = Vector.empty[Promise[Answer[GameProjection]]]
    private var pendingPreviews = Vector.empty[Promise[Answer[MajorActionPreviewResponse]]]
    def load(gameId: String, selectedPlayerId: String) =
      loads :+= (gameId -> selectedPlayerId)
      val promise = Promise[Answer[GameProjection]]()
      pendingLoads :+= promise
      promise.future
    def submit(gameId: String, selectedPlayerId: String, expectedNextSequence: Long,
        command: GameCommand, orderedModifiers: Vector[ModifierInvocation]) =
      submits :+= (expectedNextSequence -> command)
      val promise = Promise[Answer[GameProjection]]()
      pendingSubmits :+= promise
      promise.future
    def preview(gameId: String, selectedPlayerId: String,
        request: MajorActionPreviewRequest) =
      previews :+= ((gameId, selectedPlayerId, request))
      val promise = Promise[Answer[MajorActionPreviewResponse]]()
      pendingPreviews :+= promise
      promise.future
    def answerLoad(result: Answer[GameProjection]): Unit =
      val promise = pendingLoads.head
      pendingLoads = pendingLoads.tail
      promise.success(result)
    def answerSubmit(result: Answer[GameProjection]): Unit =
      val promise = pendingSubmits.head
      pendingSubmits = pendingSubmits.tail
      promise.success(result)
    def answerPreview(result: Answer[MajorActionPreviewResponse]): Unit =
      val promise = pendingPreviews.head
      pendingPreviews = pendingPreviews.tail
      promise.success(result)

  /** Holds the latest scheduled poll; `fire` runs it as if its delay passed. */
  private final class ManualClock extends PollClock:
    var delays = Vector.empty[Int]
    private var latest = Option.empty[() => Unit]
    def schedule(delayMillis: Int)(task: () => Unit) =
      delays :+= delayMillis
      latest = Some(task)
      new PollCancellation:
        def cancel(): Unit = if latest.contains(task) then latest = None
    def pending: Boolean = latest.nonEmpty
    def fire(): Unit =
      val task = latest.get
      latest = None
      task()

  private final class RecordingNavigation extends Navigation:
    var shown = Vector.empty[(String, String)]
    var startedOver = 0
    def showSession(gameId: String, playerId: String): Unit = shown :+= (gameId -> playerId)
    def startOver(): Unit = startedOver += 1

  private final class Fixture(trusted: Boolean, player: String = "red"):
    val client = new FakeClient
    val clock = new ManualClock
    val navigation = new RecordingNavigation
    var redraws = 0
    val session = new TableSession(client, trusted, "g", player, clock,
      navigation, () => redraws += 1)

  /** Every queued callback has run: a timer fires after the microtask queue
    * drains, and the queue execution context runs on microtasks.
    */
  private def settle(): Future[Unit] =
    val done = Promise[Unit]()
    setTimeout(0)(done.success(()))
    done.future

  private val players = Vector(
    GamePlayer("red", "Red", "Exile", PlayerColor.Red),
    GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue))

  private def snapshot(sequence: Long, active: String = "red", ready: Boolean = true,
      viewer: Option[String] = None): GameProjection =
    GameProjection("g", sequence, "act", Some(active), players, Vector.empty,
      Vector.empty, Vector.empty, ready = ready, completed = false,
      viewerPlayerId = viewer)

  /** A development session showing `snapshot(sequence)` as red, polling. */
  private def displayed(sequence: Long): Future[Fixture] =
    val fixture = new Fixture(trusted = false)
    fixture.session.open(Some("g"))
    fixture.client.answerLoad(Right(snapshot(sequence)))
    settle().map(_ => fixture)

  /** A trusted seat that has taken red from its first snapshot, polling. */
  private def seated(): Future[Fixture] =
    val fixture = new Fixture(trusted = true, player = "")
    fixture.session.open(None)
    fixture.client.answerLoad(Right(snapshot(1, viewer = Some("red"))))
    settle().map(_ => fixture)

  test("a development session opens the queried game, displays it and polls"):
    val fixture = new Fixture(trusted = false)
    fixture.session.open(Some("g"))
    assertEquals(fixture.client.loads, Vector("g" -> "red"))
    assertEquals(fixture.navigation.shown, Vector("g" -> "red"))
    assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connecting)
    fixture.client.answerLoad(Right(snapshot(3)))
    settle().map { _ =>
      val session = fixture.session
      assertEquals(session.viewedProjection.map(_.nextSequence), Some(3L))
      assertEquals(session.viewedDrafts.context, Some(BoardSelectionContext("g", "red", 3)))
      assertEquals(session.viewedConnection, ServerConnectionState.Connected)
      assert(session.controlsAvailable)
      assertEquals(fixture.clock.delays, Vector(5000))
      assert(fixture.clock.pending)
      // open, loadExisting, and the displayed snapshot.
      assertEquals(fixture.redraws, 3)
    }

  test("a development session with no queried game starts over"):
    val fixture = new Fixture(trusted = false)
    fixture.session.open(None)
    assertEquals(fixture.navigation.startedOver, 1)
    assertEquals(fixture.client.loads, Vector.empty)

  test("a snapshot waiting on another seat before the game is ready reloads as that seat"):
    val fixture = new Fixture(trusted = false)
    fixture.session.open(Some("g"))
    fixture.client.answerLoad(Right(snapshot(1, active = "blue", ready = false)))
    settle().flatMap { _ =>
      assertEquals(fixture.session.currentPlayerId, "blue")
      assertEquals(fixture.navigation.shown, Vector("g" -> "red", "g" -> "blue"))
      assertEquals(fixture.client.loads, Vector("g" -> "red", "g" -> "blue"))
      assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
      assert(!fixture.clock.pending)
      fixture.client.answerLoad(Right(snapshot(1, active = "blue", ready = false)))
      settle()
    }.map { _ =>
      assertEquals(fixture.session.viewedDrafts.context,
        Some(BoardSelectionContext("g", "blue", 1)))
      assert(fixture.clock.pending)
    }

  test("a transient load failure disconnects, and reconnect loads again"):
    val fixture = new Fixture(trusted = false)
    fixture.session.open(Some("g"))
    val down = GameClientFailure.NetworkFailure("down")
    fixture.client.answerLoad(Left(down))
    settle().flatMap { _ =>
      assertEquals(fixture.session.viewedConnection, ServerConnectionState.Disconnected(down))
      assertEquals(fixture.session.shownFailure, Some(down))
      assert(!fixture.session.controlsAvailable)
      fixture.session.reconnectSession()
      assertEquals(fixture.session.shownFailure, None)
      assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connecting)
      assertEquals(fixture.client.loads.size, 2)
      fixture.client.answerLoad(Right(snapshot(1)))
      settle()
    }.map { _ =>
      assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connected)
    }

  test("a trusted seat refused by a poll clears the projection and needs its seat link"):
    seated().flatMap { fixture =>
      fixture.clock.fire()
      val refused = GameClientFailure.HttpFailure(401, "unauthorized", "no seat")
      fixture.client.answerLoad(Left(refused))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection, None)
        assertEquals(fixture.session.shownFailure, Some(refused))
        assert(TableSession.needsSeatLink(fixture.session.trusted, refused))
        assert(!fixture.clock.pending)
      }
    }

  test("a development 403 on a poll keeps the position on display and keeps polling"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fire()
      val refused = GameClientFailure.HttpFailure(403, "forbidden", "no")
      fixture.client.answerLoad(Left(refused))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(2L))
        assertEquals(fixture.session.shownFailure, Some(refused))
        assert(fixture.clock.pending)
      }
    }

  test("a poll at the same position keeps polling, and an advancing one is displayed"):
    displayed(2).flatMap { fixture =>
      val before = fixture.redraws
      fixture.clock.fire()
      fixture.client.answerLoad(Right(snapshot(2)))
      settle().flatMap { _ =>
        assertEquals(fixture.redraws, before)
        assert(fixture.clock.pending)
        fixture.clock.fire()
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(3L))
        assertEquals(fixture.session.viewedDrafts.context,
          Some(BoardSelectionContext("g", "red", 3)))
        assertEquals(fixture.redraws, before + 1)
        assert(fixture.clock.pending)
      }
    }

  test("a transient poll failure disconnects and stops polling"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fire()
      val down = GameClientFailure.NetworkFailure("down")
      fixture.client.answerLoad(Left(down))
      settle().map { _ =>
        assertEquals(fixture.session.viewedConnection, ServerConnectionState.Disconnected(down))
        assert(!fixture.clock.pending)
      }
    }

  /** Spec, "The trusted `store` records success against `capture`". */
  test("a trusted seat takes the viewer from its first snapshot and ends connected"):
    val fixture = new Fixture(trusted = true, player = "")
    fixture.session.open(None)
    assertEquals(fixture.client.loads, Vector("g" -> ""))
    fixture.client.answerLoad(Right(snapshot(1, viewer = Some("red"))))
    settle().map { _ =>
      assertEquals(fixture.session.currentPlayerId, "red")
      assertEquals(fixture.session.viewedConnection, ServerConnectionState.Connected)
      assert(fixture.session.controlsAvailable)
      assertEquals(fixture.session.viewedDrafts.context,
        Some(BoardSelectionContext("g", "red", 1)))
      assertEquals(fixture.navigation.shown, Vector.empty)
    }

  test("a trusted snapshot for another viewer stops the session and asks for the seat link"):
    seated().flatMap { fixture =>
      fixture.clock.fire()
      fixture.client.answerLoad(Right(snapshot(1, viewer = Some("blue"))))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection, None)
        assertEquals(fixture.session.shownFailure, Some(GameClientFailure.HttpFailure(403,
          "seat-changed", "Open your assigned seat link.")))
        assert(!fixture.clock.pending)
      }
    }

  test("a trusted seat ignores the development calls and never shows its address"):
    seated().map { fixture =>
      fixture.session.loadSession("other", "blue")
      fixture.session.createGame()
      assertEquals(fixture.session.currentGameId, "g")
      assertEquals(fixture.session.currentPlayerId, "red")
      assertEquals(fixture.client.loads.size, 1)
      assertEquals(fixture.navigation.shown, Vector.empty)
      assertEquals(fixture.navigation.startedOver, 0)
    }

  test("a stale-position submit empties the drafts, reloads, and keeps the notice"):
    displayed(2).flatMap { fixture =>
      fixture.session.send(GameCommand.BeginRest, Vector.empty)
      assertEquals(fixture.client.submits, Vector(2L -> GameCommand.BeginRest))
      val stale = GameClientFailure.StalePosition("moved")
      fixture.client.answerSubmit(Left(stale))
      settle().flatMap { _ =>
        assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
        assertEquals(fixture.client.loads.size, 2)
        fixture.client.answerLoad(Right(snapshot(3)))
        settle()
      }.map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(3L))
        assertEquals(fixture.session.shownFailure, Some(stale))
      }
    }

  test("the flow's preview carries the session's game and seat"):
    displayed(2).map { fixture =>
      val request = MajorActionPreviewRequest(2L, "recover", Map.empty)
      fixture.session.preview(request)
      assertEquals(fixture.client.previews, Vector(("g", "red", request)))
    }

  test("fail shows the failure and redraws; replaceDrafts does not redraw"):
    displayed(2).map { fixture =>
      val before = fixture.redraws
      fixture.session.replaceDrafts(SessionDrafts.empty)
      assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
      assertEquals(fixture.redraws, before)
      val down = GameClientFailure.NetworkFailure("down")
      fixture.session.fail(down)
      assertEquals(fixture.session.shownFailure, Some(down))
      assertEquals(fixture.redraws, before + 1)
    }

  test("only a trusted 401 or 403 needs the seat link"):
    val http = (status: Int) => GameClientFailure.HttpFailure(status, "code", "detail")
    assert(TableSession.needsSeatLink(trusted = true, http(401)))
    assert(TableSession.needsSeatLink(trusted = true, http(403)))
    assert(!TableSession.needsSeatLink(trusted = false, http(403)))
    assert(!TableSession.needsSeatLink(trusted = true, http(500)))
    assert(!TableSession.needsSeatLink(trusted = true,
      GameClientFailure.NetworkFailure("down")))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TableSessionSuite"`
Expected: compile error, `Navigation` / `TableSession` not found.

- [ ] **Step 3: Write `TableView`**

Create `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala`:

```scala
package oathdigital.frontend

/** What the table screen reads: the displayed position, the failure to
  * show, the draft set, and who is looking. Read-only. The names repeat
  * neither the session's private state nor `SessionControls`' or
  * `FlowHost`'s members, so one class implements all three traits with no
  * name answering two questions.
  */
private[frontend] trait TableView:
  def trusted: Boolean
  def viewedGameId: String
  def viewedPlayerId: String
  def viewedProjection: Option[GameProjection]
  def shownFailure: Option[GameClientFailure]
  def viewedDrafts: SessionDrafts
  def viewedConnection: ServerConnectionState
  def controlsAvailable: Boolean
  def viewedRawEvents: Vector[RawEvent]
```

- [ ] **Step 4: Write `TableSession.scala`**

Every function body below is the one in today's `ServerModeUi.start`, with four rewrites and nothing else: `render()` → `redraw()`; `polling.foreach(_.x(...))` / `polling.exists(_.x(...))` → `polling.x(...)` (the poller is built in the constructor, so it is never absent); `updateSessionUrl()` → `showSession()`; `fixedSeat` → `trusted`, `recovery(error)` → `TableSession.needsSeatLink(trusted, error)`. Keep `open`'s `accept(coordinator.capture, _)` exactly as written: the placeholder reads `capture` when the load lands, as today.

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The session's two effects on the page's address. */
private[frontend] trait Navigation:
  /** Shows the current game and seat in the address bar. */
  def showSession(gameId: String, playerId: String): Unit
  /** Leaves the table for the development start page. */
  def startOver(): Unit

/** The table session (CONTEXT.md): one viewer's live connection to one game
  * from one seat. It holds the displayed position, the failure on show and
  * the draft set; loads, polls and submits under the session identity; and
  * is the modifier flow's host, the development toolbar's session and the
  * screen's view. `repaint` draws the screen; the session calls it through
  * `redraw()` wherever `ServerModeUi` used to render.
  */
private[frontend] final class TableSession(
    client: GameClient,
    val trusted: Boolean,
    initialGameId: String,
    initialPlayerId: String,
    clock: PollClock,
    navigation: Navigation,
    repaint: () => Unit
) extends SessionControls, FlowHost, TableView:
  private var projection = Option.empty[GameProjection]
  private var failure = Option.empty[GameClientFailure]
  private var selectedPlayer = initialPlayerId
  private var gameId = initialGameId
  private val coordinator = new ServerSessionCoordinator(gameId, selectedPlayer)
  private val polling = new SnapshotPollingCoordinator(clock, poll)
  private var drafts = SessionDrafts.empty
  private var rawEvents = Vector.empty[RawEvent]
  private var rawHistorySequence = Option.empty[Long]

  private def showSession(): Unit =
    if !trusted then navigation.showSession(gameId, selectedPlayer)

  private def invalidTrustedViewer(value: GameProjection): Boolean =
    trusted && !value.viewerPlayerId.exists(player =>
      value.players.exists(_.playerId == player) &&
        (selectedPlayer.isEmpty || selectedPlayer == player))

  private def store(
      request: ServerRequestIdentity,
      value: GameProjection,
      notice: Option[GameClientFailure]
  ): Unit =
    val routed = if !trusted then coordinator.route(request, value, notice)
    else value.viewerPlayerId match
      case Some(player) if !invalidTrustedViewer(value) =>
        if selectedPlayer.isEmpty then
          selectedPlayer = player
          coordinator.switchSession(gameId, selectedPlayer)
        // Against `capture`, not `request`: on the first load of an empty
        // seat, `switchSession` just above has moved `capture` to a new
        // generation, `request` would no longer be accepted, and the
        // connection would stay Connecting with every control disabled. On
        // every other path the two are equal; `accept` checked them.
        coordinator.recordSnapshotSuccess(coordinator.capture)
        Some(ProjectionRoute.Display(value, notice))
      case _ =>
        polling.stop()
        coordinator.switchSession(gameId, selectedPlayer)
        projection = None
        failure = Some(GameClientFailure.HttpFailure(403, "seat-changed",
          "Open your assigned seat link."))
        redraw()
        None
    routed.foreach:
      case ProjectionRoute.Display(displayed, retainedNotice) =>
        drafts = drafts.reconcile(
          BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence),
          displayed)
        projection = Some(displayed)
        failure = retainedNotice
        redraw()
        polling.resume(coordinator.capture)
        if !trusted && !rawHistorySequence.contains(displayed.nextSequence) then
          rawHistorySequence = Some(displayed.nextSequence)
          client match
            case development: HttpGameClient => development.loadRawEventHistory(gameId).foreach:
              case Right(events) => rawEvents = events; redraw()
              case Left(_) => ()
            case _ => ()
      case ProjectionRoute.ReloadForActivePlayer(
            displayed,
            nextRequest,
            retainedNotice
          ) =>
        drafts = SessionDrafts.empty
        polling.stop()
        projection = Some(displayed)
        failure = retainedNotice
        selectedPlayer = nextRequest.playerId
        showSession()
        redraw()
        client.load(gameId, selectedPlayer).foreach { result =>
          accept(nextRequest, result, retainedNotice)
        }

  private def accept(
      request: ServerRequestIdentity,
      result: Either[GameClientFailure, GameProjection],
      notice: Option[GameClientFailure] = None
  ): Unit =
    if coordinator.accepts(request) then result match
      case Right(value) => store(request, value, notice)
      case Left(error) =>
        coordinator.recordFailure(request, error)
        if GameClientFailure.isTransient(error) ||
            TableSession.needsSeatLink(trusted, error) then
          polling.stop()
        if TableSession.needsSeatLink(trusted, error) then
          coordinator.switchSession(gameId, selectedPlayer)
          projection = None
        failure = Some(error)
        redraw()

  private def loadExisting(id: String, playerId: String): Unit =
    if trusted then return
    polling.stop()
    gameId = id.trim
    projection = None
    drafts = SessionDrafts.empty
    rawEvents = Vector.empty
    rawHistorySequence = None
    failure = None
    selectedPlayer = playerId.trim match
      case "" => "red-exile"
      case value => value
    val request = coordinator.switchSession(gameId, selectedPlayer)
    showSession()
    redraw()
    client.load(gameId, selectedPlayer).foreach(accept(request, _))

  private def newGame(): Unit =
    if trusted then return
    polling.stop()
    navigation.startOver()

  private def reconnect(): Unit =
    polling.stop()
    failure = None
    val request = coordinator.reconnect()
    showSession()
    redraw()
    client.load(gameId, selectedPlayer).foreach(accept(request, _))

  private def poll(request: ServerRequestIdentity): Unit =
    client.load(request.gameId, request.playerId).foreach:
      case Right(snapshot) if invalidTrustedViewer(snapshot) =>
        val accepted = polling.complete(request, continuePolling = false)
        if accepted then accept(request, Right(snapshot))
      case Right(snapshot) =>
        val advances = projection.forall(current =>
          coordinator.snapshotAdvances(
            request,
            current.nextSequence,
            snapshot.nextSequence
          )
        )
        if advances then
          val accepted = polling.complete(request, continuePolling = false)
          if accepted then accept(request, Right(snapshot))
        else
          val accepted = polling.complete(request, continuePolling = true)
          if accepted then coordinator.recordSnapshotSuccess(request)
      case Left(error) =>
        val transient = GameClientFailure.isTransient(error)
        val accepted = polling.complete(request, continuePolling = !transient)
        if accepted then accept(request, Left(error))

  private def submitTransport(command: GameCommand,
      modifiers: Vector[ModifierInvocation]): Unit =
    projection.foreach { current =>
      val request = coordinator.capture
      client
        .submit(gameId, selectedPlayer, current.nextSequence, command, modifiers)
        .foreach:
          case Left(stale: GameClientFailure.StalePosition)
              if coordinator.accepts(request) =>
            drafts = SessionDrafts.empty
            failure = Some(stale)
            client.load(gameId, selectedPlayer).foreach:
              refreshed => accept(request, refreshed, Some(stale))
          case other => accept(request, other)
    }

  /** Forwards the page's visibility to the poller. */
  def visibilityChanged(hidden: Boolean): Unit = polling.visibilityChanged(hidden)

  /** The bootstrap: the first render, then the trusted seat's load, the
    * queried game, or a new game.
    */
  def open(queryGameId: Option[String]): Unit =
    redraw()
    if trusted then client.load(gameId, selectedPlayer).foreach(accept(coordinator.capture, _))
    else queryGameId match
      case Some(existing) => loadExisting(existing, selectedPlayer)
      case None => newGame()

  // SessionControls (the development toolbar).
  def currentGameId: String = gameId
  def currentPlayerId: String = selectedPlayer
  def displayedProjection: Option[GameProjection] = projection
  def connectionState: ServerConnectionState = coordinator.connectionState
  def loadSession(id: String, playerId: String): Unit = loadExisting(id, playerId)
  def reconnectSession(): Unit = reconnect()
  def createGame(): Unit = newGame()

  // FlowHost (the modifier flow). The three identity members are above.
  def currentDrafts: SessionDrafts = drafts
  def replaceDrafts(value: SessionDrafts): Unit = drafts = value
  def redraw(): Unit = repaint()
  def fail(error: GameClientFailure): Unit =
    failure = Some(error)
    redraw()
  def preview(request: MajorActionPreviewRequest) =
    client.preview(gameId, selectedPlayer, request)
  def send(command: GameCommand, modifiers: Vector[ModifierInvocation]): Unit =
    submitTransport(command, modifiers)

  // TableView (the screen).
  def viewedGameId: String = gameId
  def viewedPlayerId: String = selectedPlayer
  def viewedProjection: Option[GameProjection] = projection
  def shownFailure: Option[GameClientFailure] = failure
  def viewedDrafts: SessionDrafts = drafts
  def viewedConnection: ServerConnectionState = coordinator.connectionState
  def controlsAvailable: Boolean =
    coordinator.connectionState == ServerConnectionState.Connected
  def viewedRawEvents: Vector[RawEvent] = rawEvents

private[frontend] object TableSession:
  /** A trusted seat whose cookie no longer opens the game: the viewer must
    * reopen their seat link. Development sessions never need one.
    */
  def needsSeatLink(trusted: Boolean, error: GameClientFailure): Boolean =
    error match
      case GameClientFailure.HttpFailure(401 | 403, _, _) if trusted => true
      case _ => false
```

- [ ] **Step 5: Run the suite to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TableSessionSuite"`
Expected: 15 tests pass. `ServerModeUi` still compiles on its own locals at this point.

- [ ] **Step 6: Prove the `capture` test bites**

Temporarily change `coordinator.recordSnapshotSuccess(coordinator.capture)` in `store` to `coordinator.recordSnapshotSuccess(request)`. Run the suite: "a trusted seat takes the viewer from its first snapshot and ends connected" fails (connection `Connecting`). Revert, and confirm `git diff` shows the original line.

- [ ] **Step 7: Wire `ServerModeUi` to the session**

Replace `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` with the following. `render` keeps its body; its reads go through `view` (the session as a `TableView`), `ui` is the flow over the session, and the Reconnect button calls `session.reconnectSession()`.

```scala
package oathdigital.frontend

import ServerUiSupport._

import org.scalajs.dom

object ServerModeUi:
  def start(mount: dom.Element,
      client: GameClient = new HttpGameClient(new SameOriginJsonTransport),
      trustedGameId: Option[String] = None,
      startOver: () => Unit = () => dom.window.location.assign(Main.DevelopmentStartUrl)): Unit =
    val trusted = trustedGameId.nonEmpty
    val initialPlayer = if trusted then "" else queryParameter("playerId").getOrElse("red-exile")
    val initialGame = trustedGameId.getOrElse(queryParameter("gameId").getOrElse(freshGameId()))
    val shell = new GameTableShell(mount, !trusted)

    def render(): Unit =
      val view: TableView = session
      val projection = view.viewedProjection
      val failure = view.shownFailure
      val selectedPlayer = view.viewedPlayerId
      val drafts = view.viewedDrafts
      val connection = view.viewedConnection
      val actionContent = element("div", "action-content")
      connection match
        case ServerConnectionState.Disconnected(_) =>
          actionContent.appendChild(text(
            "div",
            "status error disconnected",
            "Disconnected. Reconnect to fetch the authoritative current " +
              "state before issuing another command."
          ))
          val retry = button("Reconnect", "reconnectSession")
          retry.onclick = _ => session.reconnectSession()
          actionContent.appendChild(retry)
        case ServerConnectionState.Connecting if projection.nonEmpty =>
          actionContent.appendChild(text(
            "div",
            "status",
            "Reconnecting to server…"
          ))
        case _ => ()
      failure.foreach { error =>
        val notice = text("div", "status error",
          if TableSession.needsSeatLink(view.trusted, error) then
            "Open your assigned seat link to restore access to this game."
          else error.message)
        notice.setAttribute("role", "alert")
        actionContent.appendChild(notice)
      }
      if view.trusted && selectedPlayer.nonEmpty then
        actionContent.appendChild(text("p", "seat-identity", "Your seat: " +
          projection.fold(selectedPlayer)(playerDisplayName(_, selectedPlayer))))
      val (players, world, decision) = projection match
        case None if failure.isEmpty =>
          actionContent.appendChild(text("div", "status", "Loading game…"))
          (text("p", "empty-state", "Loading players…"),
            text("p", "empty-state", "Loading world…"), "loading")
        case None =>
          (text("p", "empty-state", "Players unavailable."),
            text("p", "empty-state", "World unavailable."), "unavailable")
        case Some(value) =>
          val presentation = viewerPresentation(value, selectedPlayer)
          // Routed once per render: both panes read the same answer.
          val routed = ParkedDecision.route(value, presentation)
          val pane = ActionDecisionRenderer.actionsPanel(value, presentation,
            routed, view.controlsAvailable, drafts, ui)
          actionContent.appendChild(pane.element)
          val decisionKey = Vector(selectedPlayer, value.phase,
            value.activeParticipantId.getOrElse(""),
            value.pendingCardDecision.map(_.decisionId).getOrElse(""),
            value.walkerDecision.map(_.decisionId).getOrElse(""),
            drafts.boardTargets.flatMap(_.activeActionKind).getOrElse(""),
            drafts.modifiers.map(_.stage.toString).getOrElse(""),
            pane.prompt).mkString("|")
          (WorldBoardRenderer.players(value, selectedPlayer),
            WorldBoardRenderer.world(value, routed.surface.collect {
              case board: ParkedDecision.Surface.Board => board
            }, view.controlsAvailable, drafts, ui), decisionKey)
      val development = element("div", "development-content")
      if !view.trusted then
        development.appendChild(DevelopmentRenderer.controls(session))
        if projection.nonEmpty then
          development.appendChild(DevelopmentRenderer.rawEventLog(view.viewedRawEvents))
      val attention = s"$decision|$connection|${failure.map(_.message)}"
      shell.update(view.viewedGameId, attention, players, world, actionContent, development)

    lazy val session: TableSession = new TableSession(client, trusted,
      initialGame, initialPlayer, new BrowserPollClock,
      new BrowserNavigation(startOver), () => render())
    lazy val ui: ActionControls = new ModifierFlow(session)

    dom.document.addEventListener(
      "visibilitychange",
      (_: dom.Event) => session.visibilityChanged(dom.document.hidden)
    )
    session.visibilityChanged(dom.document.hidden)
    session.open(queryParameter("gameId"))

/** The browser's address bar and start page, for the table session. */
private[frontend] final class BrowserNavigation(leave: () => Unit) extends Navigation:
  def showSession(gameId: String, playerId: String): Unit = updateUrl(gameId, playerId)
  def startOver(): Unit =
    dom.window.history.replaceState(null, "", Main.DevelopmentStartUrl)
    leave()
```

The `oathdigital.protocol` and `JSExecutionContext` imports are gone: nothing left in the file names a protocol type or registers a `Future` callback. `updateUrl` resolves through `import ServerUiSupport._`.

- [ ] **Step 8: Run the frontend suite**

Run: `./sbtw "frontend/test"`
Expected: all green, 420 + 15 = 435, every `ServerModeUiSuite` test unchanged.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/TableSession.scala frontend/src/main/scala/oathdigital/frontend/TableScreen.scala frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala
git commit -m "refactor(frontend): move the session out of ServerModeUi into TableSession

The state, loads, routing, polling and submit leave ServerModeUi.start
unchanged in logic. TableSession implements SessionControls, FlowHost and
the screen's new TableView, takes its address-bar effects through
Navigation, and gets a pure suite. The trusted store's capture now says
why it is capture.

Co-Authored-By: <the committing model's trailer>"
```

---

### Task 2: `TableScreen`, and `start` as composition only

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala` (add the class)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` (remove `render`, add `screen`)

**Interfaces:**
- Consumes: `TableView`, `TableSession` (Task 1), `ActionControls`, `SessionControls`, `GameTableShell.update(gameId, decisionKey, playerContent, worldContent, actionContent, development)`.
- Produces: `final class TableScreen(shell: GameTableShell, view: TableView, controls: ActionControls, session: SessionControls) { def render(): Unit }`.

- [ ] **Step 1: Add `TableScreen`**

Append to `TableScreen.scala`, and add `import ServerUiSupport._` under the `package` line. The body is Task 1's `render` with `ui` renamed `controls` and the local `val view: TableView = session` line dropped (`view` is now the constructor's).

```scala
/** The table: the action pane, the players, the world and the development
  * pane, drawn from the session's view. Every `render` replaces them all;
  * `GameTableShell` keeps focus and scroll across the replacement.
  */
private[frontend] final class TableScreen(
    shell: GameTableShell,
    view: TableView,
    controls: ActionControls,
    session: SessionControls):
  def render(): Unit =
    val projection = view.viewedProjection
    val failure = view.shownFailure
    val selectedPlayer = view.viewedPlayerId
    val drafts = view.viewedDrafts
    val connection = view.viewedConnection
    val actionContent = element("div", "action-content")
    connection match
      case ServerConnectionState.Disconnected(_) =>
        actionContent.appendChild(text(
          "div",
          "status error disconnected",
          "Disconnected. Reconnect to fetch the authoritative current " +
            "state before issuing another command."
        ))
        val retry = button("Reconnect", "reconnectSession")
        retry.onclick = _ => session.reconnectSession()
        actionContent.appendChild(retry)
      case ServerConnectionState.Connecting if projection.nonEmpty =>
        actionContent.appendChild(text(
          "div",
          "status",
          "Reconnecting to server…"
        ))
      case _ => ()
    failure.foreach { error =>
      val notice = text("div", "status error",
        if TableSession.needsSeatLink(view.trusted, error) then
          "Open your assigned seat link to restore access to this game."
        else error.message)
      notice.setAttribute("role", "alert")
      actionContent.appendChild(notice)
    }
    if view.trusted && selectedPlayer.nonEmpty then
      actionContent.appendChild(text("p", "seat-identity", "Your seat: " +
        projection.fold(selectedPlayer)(playerDisplayName(_, selectedPlayer))))
    val (players, world, decision) = projection match
      case None if failure.isEmpty =>
        actionContent.appendChild(text("div", "status", "Loading game…"))
        (text("p", "empty-state", "Loading players…"),
          text("p", "empty-state", "Loading world…"), "loading")
      case None =>
        (text("p", "empty-state", "Players unavailable."),
          text("p", "empty-state", "World unavailable."), "unavailable")
      case Some(value) =>
        val presentation = viewerPresentation(value, selectedPlayer)
        // Routed once per render: both panes read the same answer.
        val routed = ParkedDecision.route(value, presentation)
        val pane = ActionDecisionRenderer.actionsPanel(value, presentation,
          routed, view.controlsAvailable, drafts, controls)
        actionContent.appendChild(pane.element)
        val decisionKey = Vector(selectedPlayer, value.phase,
          value.activeParticipantId.getOrElse(""),
          value.pendingCardDecision.map(_.decisionId).getOrElse(""),
          value.walkerDecision.map(_.decisionId).getOrElse(""),
          drafts.boardTargets.flatMap(_.activeActionKind).getOrElse(""),
          drafts.modifiers.map(_.stage.toString).getOrElse(""),
          pane.prompt).mkString("|")
        (WorldBoardRenderer.players(value, selectedPlayer),
          WorldBoardRenderer.world(value, routed.surface.collect {
            case board: ParkedDecision.Surface.Board => board
          }, view.controlsAvailable, drafts, controls), decisionKey)
    val development = element("div", "development-content")
    if !view.trusted then
      development.appendChild(DevelopmentRenderer.controls(session))
      if projection.nonEmpty then
        development.appendChild(DevelopmentRenderer.rawEventLog(view.viewedRawEvents))
    val attention = s"$decision|$connection|${failure.map(_.message)}"
    shell.update(view.viewedGameId, attention, players, world, actionContent, development)
```

- [ ] **Step 2: Make `start` composition only**

In `ServerModeUi.scala`, delete the whole `def render(): Unit = ...` block and replace the two `lazy val`s with:

```scala
    lazy val session: TableSession = new TableSession(client, trusted,
      initialGame, initialPlayer, new BrowserPollClock,
      new BrowserNavigation(startOver), () => screen.render())
    lazy val flow: ActionControls = new ModifierFlow(session)
    lazy val screen: TableScreen = new TableScreen(shell, session, flow, session)
```

The rest of `start` (the four `val`s, the listener, `visibilityChanged`, `open`) and `BrowserNavigation` stay as Task 1 left them. `import ServerUiSupport._` stays (`queryParameter`, `freshGameId`, `updateUrl`).

- [ ] **Step 3: Run the frontend suite and the greps**

Run: `./sbtw "frontend/test"`
Expected: all green, 435.

```bash
grep -c "var " frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala
```
Expected: `0`.

```bash
grep -n "dom\." frontend/src/main/scala/oathdigital/frontend/TableSession.scala
```
Expected: no output.

```bash
grep -n "client\." frontend/src/main/scala/oathdigital/frontend/TableScreen.scala
```
Expected: no output.

```bash
wc -l frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala frontend/src/main/scala/oathdigital/frontend/TableSession.scala frontend/src/main/scala/oathdigital/frontend/TableScreen.scala
```
Expected: about 35, 250 and 100.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/TableScreen.scala frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala
git commit -m "refactor(frontend): draw the table from TableScreen, leaving start as composition

Co-Authored-By: <the committing model's trailer>"
```

---

### Task 3: Fix 1, a stale-position submit renders at once

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableSession.scala` (`submitTransport`'s `StalePosition` branch)
- Test: `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala`

**Interfaces:**
- Consumes: `TableSession`, the suite's `displayed`, `settle`, `FakeClient.answerSubmit` (Task 1).
- Produces: nothing later tasks use.

- [ ] **Step 1: Write the failing test**

Add after "a stale-position submit empties the drafts, reloads, and keeps the notice":

```scala
  /** Spec, Fix 1. */
  test("a stale-position submit redraws before the reload lands"):
    displayed(2).flatMap { fixture =>
      fixture.session.send(GameCommand.BeginRest, Vector.empty)
      val before = fixture.redraws
      val stale = GameClientFailure.StalePosition("moved")
      fixture.client.answerSubmit(Left(stale))
      settle().map { _ =>
        assertEquals(fixture.redraws, before + 1)
        assertEquals(fixture.session.shownFailure, Some(stale))
        assertEquals(fixture.session.viewedDrafts, SessionDrafts.empty)
      }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TableSessionSuite"`
Expected: FAIL, `redraws` equals `before` (no redraw until the reload lands).

- [ ] **Step 3: Redraw in the branch**

In `submitTransport`:

```scala
          case Left(stale: GameClientFailure.StalePosition)
              if coordinator.accepts(request) =>
            drafts = SessionDrafts.empty
            failure = Some(stale)
            redraw()
            client.load(gameId, selectedPlayer).foreach:
              refreshed => accept(request, refreshed, Some(stale))
```

- [ ] **Step 4: Run the frontend suite**

Run: `./sbtw "frontend/test"`
Expected: all green, 436. In particular "trusted UI reloads after command conflict without retrying or changing seat" in `ServerModeUiSuite` stays green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/TableSession.scala frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala
git commit -m "fix(frontend): show a stale-position notice as soon as the submit is refused

The draft set was emptied and the notice set without a redraw, so until
the reload landed the viewer saw the old controls, drafts already gone,
and no notice.

Co-Authored-By: <the committing model's trailer>"
```

---

### Task 4: Fix 2, drop a preview response from another session

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableSession.scala` (`preview`, one import)
- Test: `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala`

**Interfaces:**
- Consumes: `TableSession.preview`, `reconnectSession`, the suite's `displayed`, `settle`, `FakeClient.answerPreview` (Task 1).
- Produces: nothing later tasks use.

- [ ] **Step 1: Write the failing tests**

Add after "the flow's preview carries the session's game and seat":

```scala
  /** Spec, Fix 2. */
  test("a preview answered after the session changed never lands"):
    displayed(2).flatMap { fixture =>
      val answer = fixture.session.preview(MajorActionPreviewRequest(2L, "recover", Map.empty))
      fixture.session.reconnectSession()
      fixture.client.answerPreview(Right(MajorActionPreviewResponse(2L, "recover",
        Vector.empty, Vector.empty, Vector.empty)))
      settle().map(_ => assert(!answer.isCompleted))
    }

  test("a preview answered under the same session lands"):
    displayed(2).flatMap { fixture =>
      val answer = fixture.session.preview(MajorActionPreviewRequest(2L, "recover", Map.empty))
      val response = MajorActionPreviewResponse(2L, "recover", Vector.empty,
        Vector.empty, Vector.empty)
      fixture.client.answerPreview(Right(response))
      answer.map(result => assertEquals(result, Right(response)))
    }
```

- [ ] **Step 2: Run them to verify the first fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.TableSessionSuite"`
Expected: "a preview answered after the session changed never lands" FAILS (the future completed); "a preview answered under the same session lands" passes.

- [ ] **Step 3: Guard `preview`**

Add `import scala.concurrent.Future` to `TableSession.scala` (next to the `JSExecutionContext` import) and replace `preview`:

```scala
  /** Completes only if the session is the one that asked: a response that
    * lands after a seat change, a reload or a reconnect writes nothing,
    * fails nothing and redraws nothing, like every other late response.
    */
  def preview(request: MajorActionPreviewRequest)
      : Future[Either[GameClientFailure, MajorActionPreviewResponse]] =
    val identity = coordinator.capture
    client.preview(gameId, selectedPlayer, request).flatMap: result =>
      if coordinator.accepts(identity) then Future.successful(result)
      else Future.never
```

- [ ] **Step 4: Run the frontend suite**

Run: `./sbtw "frontend/test"`
Expected: all green, 438, including every `ModifierFlowSuite` test and "a previewed major action orders its modifier and posts the ordered command" in `ServerModeUiSuite`.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/TableSession.scala frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala
git commit -m "fix(frontend): drop a preview response that lands after the session changed

Load, poll and submit responses were already checked against the session
identity; a preview response was the one that was not.

Co-Authored-By: <the committing model's trailer>"
```

---

### Task 5: Glossary, spec records, full gate

**Files:**
- Modify: `CONTEXT.md` ("Table session" section: add one entry after "Modifier flow")
- Modify: `docs/superpowers/specs/2026-09-25-table-session-design.md` (status line)
- Modify: `docs/superpowers/specs/2026-09-25-modifier-flow-module-design.md` ("Stale preview responses": one closing sentence)

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: nothing.

- [ ] **Step 1: Add the glossary entry**

In `CONTEXT.md`, after the "Modifier flow" entry's `_Avoid_` lines and before `### Game`, insert (blank line before and after):

```
**Table session**:
One viewer's live connection to one game from one seat. It knows the
position on display, holds the viewer's draft set, and says whether the
viewer can act; changing the game or the seat starts a new one.
_Avoid_: server session, connection, client state
```

If another session has edited nearby entries, keep their wording and add this one.

- [ ] **Step 2: Record the slice in the table-session spec**

Line 3: `> Status: designed 2026-09-25, not yet planned.` → `> Status: implemented 2026-09-25 (five commits, this plan).` Keep the rest of the paragraph.

- [ ] **Step 3: Point the modifier-flow spec at the fix**

At the end of the "Stale preview responses" section of `docs/superpowers/specs/2026-09-25-modifier-flow-module-design.md`, add: "Superseded for session-identity changes by the table-session spec's Fix 2: the host now drops a preview response that lands under another identity. A response from an older position under the same identity is still left to reconcile."

- [ ] **Step 4: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` then `python3 scripts/check-architecture.py`
Expected: backend 1700 green, frontend 438 green, link succeeds, architecture check passes.

- [ ] **Step 5: Commit**

```bash
git add CONTEXT.md docs/superpowers/specs/2026-09-25-table-session-design.md docs/superpowers/specs/2026-09-25-modifier-flow-module-design.md
git commit -m "docs: record the table session in the glossary and its spec

Co-Authored-By: <the committing model's trailer>"
```
