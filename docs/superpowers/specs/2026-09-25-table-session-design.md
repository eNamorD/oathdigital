# Table Session

> Status: designed 2026-09-25, not yet planned. This is the second and last
move of the session-drafts spec's option 3 (`2026-09-25-session-drafts-design.md`,
"Follow-up"; `2026-09-25-modifier-flow-module-design.md` was the first). Two
moves are behavior-preserving; two declared fixes follow them.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **draft set** and
**modifier flow**. This spec adds **table session** to the glossary's "Table
session" section.

## Purpose

After the modifier flow left, `ServerModeUi.start` (289 lines) still holds
two jobs in one closure. It is the session: ten `var`s and `val`s
(`ServerModeUi.scala:14-24`) and the functions that load, route, poll and
submit (`store`, `accept`, `loadExisting`, `newGame`, `reconnect`, `poll`,
`submitTransport`, with `recovery`, `invalidTrustedViewer` and
`updateSessionUrl`, `:26-245`). And it is the screen: `render` (`:37-98`),
about sixty lines of view composition. Both are reachable only through
jsdom. The two coordinators the session drives, `ServerSessionCoordinator`
and `SnapshotPollingCoordinator`, are already pure and unit-tested; the code
that combines them with the client, the draft set and the URL is not.

The target: `ServerModeUi.start` is composition only. It reads the page,
builds a session, a flow and a screen, wires them, and opens the session.

## Ownership and interface

### `TableSession`

A new frontend class owns the session. It holds the state that is not the
shell, runs every load, poll and submit, and implements the three seams the
rest of the table already uses or needs:

```scala
private[frontend] final class TableSession(
    client: GameClient,
    val trusted: Boolean,
    initialGameId: String,
    initialPlayerId: String,
    clock: PollClock,
    navigation: Navigation,
    repaint: () => Unit
) extends SessionControls, FlowHost, TableView
```

The screen callback is `repaint`, not `redraw`: `FlowHost` already declares
`redraw()`, and the session implements it by calling `repaint()`.

- State moved in unchanged: `projection`, `failure`, `selectedPlayer`,
  `gameId`, `drafts`, `rawEvents`, `rawHistorySequence`, the
  `ServerSessionCoordinator`, and the `SnapshotPollingCoordinator`, which the
  session now builds itself from `clock` and its own `poll`.
- Functions moved in unchanged in logic: `store`, `accept`, `loadExisting`,
  `newGame`, `reconnect`, `poll`, `submitTransport`, `invalidTrustedViewer`.
  Every `render()` becomes `redraw()` (the `FlowHost` member). `updateSessionUrl()` becomes
  `if !trusted then navigation.showSession(gameId, selectedPlayer)`.
  `newGame`'s `history.replaceState` and `startOver()` become
  `navigation.startOver()`.
- `fixedSeat` is renamed `trusted`; its eight branches move as they are.
- The development-only raw history keeps its `client match case development:
  HttpGameClient` downcast.
- Two new public members for `start`:
  - `visibilityChanged(hidden: Boolean): Unit` forwards to the poller.
  - `open(queryGameId: Option[String]): Unit` is the bootstrap moved from
    `:282-286`: `redraw()`, then the trusted load, or `loadExisting` with the
    query's game, or `newGame()`.

`SessionControls` (the development toolbar's view) and `FlowHost` (the
modifier flow's seam) do not change. They share `currentGameId`,
`currentPlayerId` and `displayedProjection`; the session implements each
once and satisfies both. The `FlowHost` members forward exactly as the
anonymous host in `ServerModeUi` does today (`:256-267`): `replaceDrafts`
writes without a redraw, `fail` sets the failure and redraws, `preview`
adds the game and seat, `send` is `submitTransport`.

### The seat-link rule

`recovery(error)` (`:28-30`) is read by `accept`, which stops polling and
clears the projection on it, and by `render`, which picks the notice text.
It becomes one pure function on the companion:

```scala
private[frontend] object TableSession:
  /** A trusted seat whose cookie no longer opens the game: the viewer must
    * reopen their seat link. Development sessions never need one.
    */
  def needsSeatLink(trusted: Boolean, error: GameClientFailure): Boolean =
    error match
      case GameClientFailure.HttpFailure(401 | 403, _, _) if trusted => true
      case _ => false
```

The rule lives here; the wording ("Open your assigned seat link…") stays in
the screen.

### `Navigation`

The session's only browser effects are the URL and the start-over jump. They
go behind a seam, so the session's suite runs without jsdom:

```scala
/** The session's two effects on the page's address. */
private[frontend] trait Navigation:
  /** Shows the current game and seat in the address bar. */
  def showSession(gameId: String, playerId: String): Unit
  /** Leaves the table for the development start page. */
  def startOver(): Unit
```

The browser implementation lives in `ServerModeUi.scala`: `showSession` is
`updateUrl`, and `startOver` is `history.replaceState(null, "",
Main.DevelopmentStartUrl)` followed by `leave`, the callback `start` passes
it (its own `startOver` parameter; the class cannot take a parameter with
its method's name).
`queryParameter` is read in `start` only.

### `TableView` and `TableScreen`

The screen reads the session through a narrow trait, defined next to the
screen in `TableScreen.scala`:

```scala
/** What the table screen reads: the displayed position, the failure to
  * show, the draft set, and who is looking. Read-only.
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

The member names repeat neither the session's private state (`projection`,
`failure`, `drafts`, `rawEvents`, …) nor `SessionControls`' or
`FlowHost`'s members, so one class implements all three traits with no name
answering two questions. `trusted` is the constructor's `val`. `controlsAvailable` is `coordinator.connectionState ==
ServerConnectionState.Connected`, moved from `:288-289`.

```scala
private[frontend] final class TableScreen(
    shell: GameTableShell,
    view: TableView,
    controls: ActionControls,
    session: SessionControls):
  def render(): Unit
```

`render` is `:37-98` with its reads rewritten to `view` members: the
disconnected banner and its Reconnect button (`session.reconnectSession()`),
the failure notice (`TableSession.needsSeatLink(view.trusted, error)`), the
trusted seat line, the action pane and the world with `controls`, the
decision key, the development pane (`DevelopmentRenderer.controls(session)`
and the raw event log), the attention key, and `shell.update`.

### `ServerModeUi.start` after the slice

```scala
def start(mount, client, trustedGameId, startOver): Unit =
  val trusted = trustedGameId.nonEmpty
  val initialPlayer = if trusted then "" else queryParameter("playerId").getOrElse("red-exile")
  val initialGame = trustedGameId.getOrElse(queryParameter("gameId").getOrElse(freshGameId()))
  val shell = new GameTableShell(mount, !trusted)
  lazy val session: TableSession = new TableSession(client, trusted,
    initialGame, initialPlayer, new BrowserPollClock,
    new BrowserNavigation(startOver), () => screen.render())
  lazy val flow = new ModifierFlow(session)
  lazy val screen = new TableScreen(shell, session, flow, session)
  dom.document.addEventListener("visibilitychange",
    (_: dom.Event) => session.visibilityChanged(dom.document.hidden))
  session.visibilityChanged(dom.document.hidden)
  session.open(queryParameter("gameId"))
```

The order is today's: the poller exists and knows visibility before the
first render, and the first render comes before the first load. The
signature does not change; `Main` and the jsdom suite call it as they do.
`ServerModeUi.scala` drops to about 50 lines with `BrowserNavigation`.

## The trusted `store` records success against `capture`

The trusted branch of `store` calls
`coordinator.recordSnapshotSuccess(coordinator.capture)`, not the `request`
it was handed. This looks like a slip and is load-bearing. `store` is only
reached through `accept`, which has already checked `request == capture`,
so the two are equal on every path but one: the first trusted load with an
empty seat, where `switchSession` runs two lines above and moves `capture`
to a new generation. Recording against `request` there would fail the
generation check, leave the connection `Connecting`, and keep every control
disabled. The move adds a comment saying so; the code stays.

## Declared fixes

Both land after the two moves, one commit each, so each diff shows only its
change.

### Fix 1: a stale-position submit renders at once

Today `submitTransport`'s `StalePosition` branch (`:238-243`) sets `drafts
= SessionDrafts.empty` and `failure = Some(stale)` and starts the reload,
without a redraw. Until the reload lands the viewer sees the old controls
with their drafts, which are already gone from the draft set, and no
notice. After the fix the branch calls `redraw()` before the reload: the
notice shows and the drafts clear at once, as a seat change already does
("a seat change clears the walker drafts before the interim render"). The
reload then re-renders as before.

### Fix 2: a preview response from another session is dropped

Load, poll and submit responses are each checked against the session
identity (`coordinator.accepts`); a preview response is not. The session's
`preview` captures the identity when it sends, and completes the returned
future only if the identity is unchanged when the response lands:

```scala
def preview(request: MajorActionPreviewRequest) =
  val identity = coordinator.capture
  client.preview(gameId, selectedPlayer, request).flatMap: result =>
    if coordinator.accepts(identity) then Future.successful(result)
    else Future.never
```

A dropped response writes nothing, fails nothing, and redraws nothing.
`ModifierFlow` and its suite do not change; the guard is the host's.

No reachable window shows a wrong screen today: a seat change clears the
projection, so no panel shows until the next snapshot reconciles the late
draft away; the active-player reload happens only before the game is ready,
when no major action can be previewed; a reconnect keeps the same context,
so a late draft is still the viewer's. The fix is for uniformity: after it,
every response the session receives is checked the same way. The
modifier-flow spec's "Stale preview responses" section is superseded for
identity changes; a response from an older position under the same identity
is still left to `reconcile`.

## Behavior changes

The two moves: none. Fix 1: the interim render on a stale-position submit.
Fix 2: preview responses that land after the session identity changed are
dropped.

## Verification

New pure suite `TableSessionSuite`, no jsdom. Fakes:

- a `GameClient` whose `load`, `submit` and `preview` record their calls and
  answer through a promise queue, as `ModifierFlowSuite`'s host does;
- a manual `PollClock` that records scheduled tasks and runs them on demand;
- a recording `Navigation`;
- a `redraw` counter.

Cases:

- Development load: `open(Some("g"))` loads, `store` displays, the draft set
  is reconciled to the snapshot's context, polling resumes, the URL is shown.
- `ReloadForActivePlayer`: a not-ready snapshot whose active participant is
  another seat empties the drafts, stops polling, switches the seat, shows
  the URL, and loads again as the active seat.
- Failures: a transient failure disconnects and stops polling; a trusted 401
  or 403 clears the projection and needs the seat link; a development 403
  does neither.
- Polling: a snapshot at the same position records success and keeps
  polling; an advancing one is accepted and displayed; a transient poll
  failure stops polling.
- Trusted: the first load with an empty seat takes the viewer's seat from
  the snapshot and ends `Connected` (the `capture` case above); a later
  snapshot for another viewer stops polling and asks for the seat link.
- A stale-position submit empties the drafts, keeps the notice, and reloads.
- Trusted: `loadSession` and `createGame` do nothing, and the session never
  calls `navigation.showSession`.
- Fix 1: the stale-position submit redraws once before the reload lands.
- Fix 2: a preview answered after `reconnectSession()` never completes; one
  answered under the same identity does.
- `needsSeatLink`: the rule's table.

The jsdom suite (`ServerModeUiSuite`, 55 tests, the trusted-seat, seat-change,
conflict-reload and polling tests among them) does not change and must stay green after every commit. It is the
proof that the moves change nothing.

Greps after the slice:

- `grep -c "var " frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` is `0`.
- `grep -n "dom\." frontend/src/main/scala/oathdigital/frontend/TableSession.scala` finds nothing.
- `grep -n "client\." frontend/src/main/scala/oathdigital/frontend/TableScreen.scala` finds nothing.

## Preservation boundary

Unchanged: `ServerModeUi.start`'s signature; `ServerSessionCoordinator` and
`SnapshotPollingCoordinator`; `ActionControls`, `TableControls`,
`SessionControls` and `FlowHost`; `ModifierFlow`; every renderer; every
existing suite. No protocol, DTO, CSS or DOM-structure change.

## Files

- Create: `frontend/src/main/scala/oathdigital/frontend/TableSession.scala`
  (`TableSession`, its companion, `Navigation`), about 230 lines.
- Create: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala`
  (`TableView`, `TableScreen`), about 100 lines.
- Create: `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala`.
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala`
  (composition and `BrowserNavigation`).
- Modify: `CONTEXT.md` ("Table session" section, one entry).

## Commits

1. `TableSession`, `Navigation`, `needsSeatLink`, `TableSessionSuite`, and
   the wiring. `render` stays in `start` for this commit and reads the
   session through `TableView`; the `capture` comment lands here.
2. `TableScreen`: `render` moves out and `start` is composition only.
3. Fix 1, with its test.
4. Fix 2, with its test.
5. The glossary entry and this spec's status line.

## Glossary

Add to CONTEXT.md's "Table session" section:

```
**Table session**:
One viewer's live connection to one game from one seat. It knows the
position on display, holds the viewer's draft set, and says whether the
viewer can act; changing the game or the seat starts a new one.
_Avoid_: server session, connection, client state
```

## Parked

- A `SeatMode` enum, or separate trusted and development sessions, in place
  of the `trusted` branches.
- Moving the raw event history behind the development renderer, ending the
  `HttpGameClient` downcast.
- Pruning the jsdom session tests that `TableSessionSuite` duplicates.
