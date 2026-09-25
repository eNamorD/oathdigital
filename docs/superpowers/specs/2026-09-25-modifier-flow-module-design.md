# Modifier Flow Module

> Status: implemented 2026-09-25 (five commits, this plan). This is a behavior-preserving
architecture slice with no stated exceptions. It is the first move of the
session-drafts spec's option 3 (`2026-09-25-session-drafts-design.md`,
"Follow-up"); session identity and polling are the second move and get their
own spec.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **draft set** and
**modifier flow**. This spec adds one sentence to the modifier flow entry
naming a **flow step** (a move that keeps the viewer in the flow) and a
**flow exit** (a move that leaves it).

## Purpose

The modifier flow lives inside `ServerModeUi.start` as seven local
functions and the anonymous `ActionControls` that calls them
(`ServerModeUi.scala:247-391`): `submit`, `startTargetedFlow`,
`activatePreviewTargets`, `confirmModifierSelection`,
`completeTargetCommand`, `restoredTargets` and `handleBoardSelection`. They
read the closure's `projection`, `gameId`, `selectedPlayer` and `drafts`,
write `drafts` and `failure`, call `client.preview`, `submitTransport` and
`render`. The flow is reachable only through jsdom, and no jsdom test drives
the preview endpoint: the flow's entries, its Targets stage and its exits
are untested end to end. Only its pure parts have suites
(`ModifierSelectionStateSuite`, `SessionDraftsSuite`).

The session-drafts slice named the six exits (`FlowExit`) but left the
entries and steps as `copy` on `SessionDrafts` inside the flow, and built
the same `ModifierWorkflow` from a preview response twice
(`ServerModeUi.scala:255-263` and `:292-299`).

## Ownership and interface

### `ModifierFlow`

A new frontend class owns the flow. It implements every member of
`ActionControls`, so `ServerModeUi`'s `ui` becomes one instance of it:

```scala
private[frontend] final class ModifierFlow(host: FlowHost) extends ActionControls
```

The seven functions move into it unchanged in logic. `submitCommand` is
`submit` (preview when the command is a major action with a preview,
otherwise `host.send`); `submitTargetCommand` is `completeTargetCommand`;
`handleSelection` keeps its two branches (`Updated` stages
`Draft.BoardTargets`, `Submit` completes the target command). `stage` is
`host.replaceDrafts(host.currentDrafts.staged(draft)); host.redraw()`. The ten flow
commands read as today, with `drafts.copy(...)` replaced by
`drafts.step(FlowStep.X)` and the two workflow constructions replaced by
`ModifierWorkflow.fromPreview`.

`ModifierFlow` never sees `GameClient`, `ServerSessionCoordinator`, the
polling coordinator or the connection state. It is constructed once, after
`render` and `submitTransport` exist, the way `ui` is today.

### `FlowHost`

What the session gives the flow. One trait, in `ModifierFlow.scala` above
the class, implemented anonymously in `ServerModeUi` next to `session`:

```scala
private[frontend] trait FlowHost:
  def displayedProjection: Option[GameProjection]
  def currentGameId: String
  def currentPlayerId: String
  def currentDrafts: SessionDrafts
  def replaceDrafts(value: SessionDrafts): Unit
  def redraw(): Unit
  def fail(error: GameClientFailure): Unit
  def preview(request: MajorActionPreviewRequest)
      : Future[Either[GameClientFailure, MajorActionPreviewResponse]]
  def send(command: GameCommand, modifiers: Vector[ModifierInvocation]): Unit
```

- Member names follow `SessionControls` (`currentGameId`, `displayedProjection`)
  rather than the closure's own names: inside the anonymous implementation in
  `ServerModeUi.start`, a member named `projection` or `render` would shadow
  the local it forwards to and recurse.
- `replaceDrafts` writes without rendering. Three flow sites write and then send
  or preview without a render in between (`Restarted` before the targeted
  preview, `OrderingLeft` before the direct submit, `Completed` before the
  target submit), so a write that always rendered would not fit. Every
  "write then render" pair stays visible in the module.
- `redraw` is `render()`. `fail` sets `failure` and renders. Every failure write in the flow today is
  followed by `render()`.
- `preview` is `client.preview(gameId, selectedPlayer, request)`. `send` is
  `submitTransport`. Both add the identity themselves; the flow keeps
  `gameId` and `playerId` only to build `ModifierSelectionContext`.
- `displayedProjection` is read for `nextSequence`, `minorActions` and
  `boardTargetActions`, as today.

The draft set stays owned by `ServerModeUi`: `reconcile` on `Display`, and
the three `empty` assignments (`ReloadForActivePlayer`, `loadExisting`, the
`StalePosition` branch of `submitTransport`) do not move. `submitTransport`,
`render`, `controlsAvailable` and `session` do not move.

### `FlowStep`

The flow's entries and steps get names, in `SessionDrafts.scala` next to
`FlowExit`:

```scala
private[frontend] enum FlowStep:
  case Ordering(workflow: ModifierWorkflow)
  case Targets(workflow: ModifierWorkflow, entry: TargetsEntry)
  case Toggle(value: PreviewModifier)
  case Move(value: PreviewModifier, delta: Int)
  case ChooseFacedown(cardId: String)

private[frontend] enum TargetsEntry:
  case Facedown(pick: Option[FacedownAdviserDraft])
  case Board(targets: BoardTargetSelectionState)
  case NoTargets
```

applied by one method on `SessionDrafts`:

| step | `modifiers` | `facedownAdviser` | `boardTargets` |
| --- | --- | --- | --- |
| `Ordering(w)` | `Some(w)` | kept | kept |
| `Targets(w, Facedown(pick))` | `Some(w)` | `pick` | `None` |
| `Targets(w, Board(t))` | `Some(w)` | kept | `Some(t)` |
| `Targets(w, NoTargets)` | `Some(w)` | kept | kept |
| `Toggle(m)` | `map(selection.toggle(m))` | kept | kept |
| `Move(m, d)` | `map(selection.moveEarlier/moveLater(m))` by sign of `d` | kept | kept |
| `ChooseFacedown(id)` | kept | `map(_.choose(id))` | kept |

The partition, distribute, selection and board drafts are kept by every
step. The three `Targets` rows are the three branches of
`activatePreviewTargets` today: the facedown branch sets the pick from
`FacedownAdviserDraft.initial(context, minor)` and clears the board targets;
the board branch sets the activated targets and leaves the pick; a target
action that `ModifierWorkflow.targetAction` does not find leaves both. The
`w` in `Targets` is `workflow.showTargets(response)`. The module computes
the entry, since it needs the projection and the context; `step` only
applies it.

### `ModifierWorkflow.fromPreview`

One factory replaces the two nine-line constructions:

```scala
def fromPreview(command: Option[GameIntent], actionKind: Option[String],
    parameters: Map[String, String], response: MajorActionPreviewResponse,
    previous: Option[ModifierSelectionState],
    context: ModifierSelectionContext): ModifierWorkflow
```

It builds the fingerprint
`s"${response.nextSequence}:${response.action}:" + modifiers.map(m => s"${m.sourceKey}/${m.handlerId}").mkString("|")`,
reconciles the selection from `previous`, and sets the stage to `Ordering`
when `response.modifiers` is non-empty and `Targets` otherwise. Both flow
entries pass `host.currentDrafts.modifiers.map(_.selection)` as `previous`.

Two unifications, both equal to today with one preview in flight:

- `startTargetedFlow` passes `None` as the previous selection today;
  `submit` passes the current one. Passing the current one in both is
  identical because `startTargetedFlow` applies `leave(FlowExit.Restarted)`
  before the preview, and `Restarted` sets `modifiers = None`. The selection
  is read when the response lands, so with two targeted previews in flight
  for the same action and position, the later-landing one now reconciles
  against the workflow the earlier-landing one opened, and keeps its
  selection where today it wiped it. That case is a stale response (below).
- `submit` always builds with stage `Ordering` today; `startTargetedFlow`
  picks by `response.modifiers.nonEmpty`. The unified rule gives `Ordering`
  in `submit` because `submit` only builds a workflow in the branch where
  `response.modifiers` is non-empty.

`ModifierWorkflow` keeps its name in this slice. The follow-up commit after
it renamed it to `ModifierFlowDraft` (and `ModifierWorkflowStage` to
`ModifierFlowStage`) to match the glossary; this spec keeps the old names.

## `ServerModeUi` after the slice

- `lazy val ui: ActionControls = new ModifierFlow(host)` where `host` is
  the anonymous `FlowHost`.
- Lines `:247-391` are gone except `submitTransport` (`:231-245`), which
  `host.send` calls.
- `render`, `store`, `accept`, `loadExisting`, `newGame`, `reconnect`,
  `poll`, `session`, `controlsAvailable` and the bootstrap are unchanged.
- `ServerModeUi.scala` drops from 411 lines to about 270.

## Stale preview responses

A preview response is not checked against the session when it lands. After
a seat change or reload, a late response writes `drafts.modifiers` under the
old context, and the next `reconcile` drops it because
`ModifierWorkflow.reconcile` filters on the selection's context. `send` is
guarded inside `submitTransport` by `coordinator.accepts`. This slice keeps
the preview path unguarded. Known, covered by reconcile, and out of scope.
Superseded for session-identity changes by the table-session spec's Fix 2:
the host now drops a preview response that lands under another identity. A
response from an older position under the same identity is still left to
reconcile.

## Behavior changes

None with one preview in flight. The two `fromPreview` unifications above
are named because they change the text of the flow, not its behavior; the
first differs only for out-of-order stale responses, which stay unguarded.

## Verification

New pure suite `ModifierFlowSuite` (no jsdom). A fake `FlowHost` holds
`drafts`, `projection`, records `renders: Int`, `failures: Vector[...]`,
`sent: Vector[(GameCommand, Vector[ModifierInvocation])]` and the preview
requests it received, and answers `preview` from a scripted response or a
`Promise` so a test can land the response after the next call. One test per
entry and exit:

- a command with no preview action sends it with no modifiers;
- a major action whose preview offers no modifiers sends it directly;
- a major action whose preview offers modifiers enters `Ordering` and
  renders, with the previous selection carried into the new one;
- a preview failure on `submit` fails without touching the drafts;
- `beginTargetedMajorAction("travel")` leaves `Restarted`, then enters
  `Ordering` when modifiers are offered and `Targets(Board(...))` when not;
- `beginTargetedMajorAction("play-facedown-adviser")` enters
  `Targets(Facedown(...))` with the pick built from the projection's minor
  actions and the board targets cleared;
- `confirmModifiers` with a command leaves `OrderingLeft` and sends the
  submission with the ordered invocations, without rendering first;
- `confirmModifiers` without a command enters `Targets`;
- `confirmModifiers` on a preview failure leaves `Failed` and fails;
- `submitTargetCommand` in `Targets` leaves `Completed` and sends the
  submission; outside `Targets` it behaves as `submitCommand`;
- `cancelModifiers` and `cancelTargetAction` leave `Cancelled(restored)`
  where `restored` is `BoardTargetSelectionState.restore` over the
  projection's `boardTargetActions`;
- `backFromModifiers` leaves `OrderingLeft`; `backFromTargets` leaves
  `TargetsLeft`, and does nothing with no flow in flight;
- `toggleModifier`, `moveModifier` and `chooseFacedownAdviser` apply their
  step and render;
- `handleSelection(Updated)` stages `Draft.BoardTargets`;
  `handleSelection(Submit)` completes the target command.

`SessionDraftsSuite` gets a `step` table like its `leave` table: one test
per row above, asserting the three touched slots and that the four walker
drafts are kept.

New small suite `ModifierWorkflowSuite`: `fromPreview` gives `Ordering`
with modifiers present and `Targets` with none; the fingerprint has the
documented shape; the previous selection reaches `reconcile`.

One jsdom `ServerModeUiSuite` test through the transport fake: a submit
whose preview answers with one modifier renders the `modifier-confirm`
control, and clicking it posts the command with `orderedModifiers` carrying
that modifier's invocation. This is the only test that proves the
`FlowHost` wiring; the fake host cannot.

`ServerModeUiSuite`'s existing tests bracket the composition and run green
before and after.

Gates: the full frontend suite, the coverage floor, and
`python3 scripts/check-architecture.py`. On completion, three greps:
`client.preview` appears once in `ServerModeUi.scala` (inside the host);
`copy(modifiers` and `copy(facedownAdviser` appear nowhere in
`frontend/src/main` outside `SessionDrafts.scala`; `ModifierSelectionContext(`
is constructed once in `frontend/src/main`, in a private helper of
`ModifierFlow` that both entries call.

## Preservation boundary

No protocol, DTO, gameplay, CSS or DOM-structure change. Control strings,
decision ids, preview requests, submitted commands and the decision key are
unchanged. `ActionControls`, `TableControls` and `SessionControls` are
unchanged, so no renderer and no renderer suite changes.

## Files

New: `ModifierFlow.scala` (`FlowHost` and `ModifierFlow`),
`ModifierFlowSuite.scala`, `ModifierWorkflowSuite.scala`. Edited:
`SessionDrafts.scala` (`FlowStep`, `TargetsEntry`, `step`),
`ModifierWorkflow.scala` (`fromPreview`), `ServerModeUi.scala`,
`SessionDraftsSuite.scala`, `ServerModeUiSuite.scala`, `CONTEXT.md`.

## Commits

Four, in order:

1. `FlowStep`, `TargetsEntry` and `SessionDrafts.step` with the step table
   in `SessionDraftsSuite`. Nothing calls `step` yet.
2. `ModifierWorkflow.fromPreview` with `ModifierWorkflowSuite`; both flow
   entries in `ServerModeUi` call it.
3. `FlowHost`, `ModifierFlow`, `ModifierFlowSuite`, and `ServerModeUi`
   wired to `new ModifierFlow(host)` in the same commit. The flow leaves
   `ServerModeUi` as it arrives in the module, so one diff shows the move.
4. The jsdom preview test, the glossary sentence, this spec's status line.

## Follow-up (option 3, second move)

Session identity and polling: `store`, `accept`, `loadExisting`,
`reconnect`, `poll` and the coordinator wiring, behind `SessionControls` or
a sibling, until `ServerModeUi.start` is composition only. Its own spec.
