# Session Drafts

> Status: implemented 2026-09-25 (seven commits, this plan). This is a behavior-preserving architecture slice with two stated
exceptions (see "Behavior changes"). It is candidate D of the 2026-09-22
architecture review, taken as option 1 of three; option 3 (decomposing
`ServerModeUi.start`) is the recorded follow-up.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **draft set**, **modifier
flow**, **parked decision**, **surface** and **board draft**.

## Purpose

`ServerUiView` (`ServerUiSupport.scala:7-39`) is a 33-member interface over
the whole of `ServerModeUi.start`'s mutable state plus every command a
renderer can issue. Its widest adapter, `ActionDecisionRenderer`, reads 16
members; five panels read two or three. The test fake, `RecordingView`
(59 lines), stubs 19 members with `()` or `None` and is constructed in 17
suites. The 463-line `ServerModeUi.start` closure is reachable only through
jsdom.

The seven draft `var`s in `ServerModeUi` are reconciled by seven calls that
build the same `BoardSelectionContext` six times (`ServerModeUi.scala:130-153`)
and are cleared by eight hand-written subsets at `:171`, `:205`, `:272`,
`:324`, `:356`, `:365`, `:418` and `:424`, each choosing a different subset
of the seven. Every reconciler is unit-tested; the composition and the reset
invariant are not.

`ServerModeUi.render` also computes its "did the question change" key by
running `querySelector` over the DOM it has just built (`:84-86`); two of the
four selectors are emitted by nothing in `frontend/src/main`.

## Ownership and interface

### `SessionDrafts`

A new pure frontend module owns the draft set as one immutable value:

```scala
final case class SessionDrafts(
    context: Option[BoardSelectionContext],
    boardTargets: Option[BoardTargetSelectionState],
    partition: Option[WalkerPartitionDraft],
    distribute: Option[WalkerDistributeDraft],
    selection: Option[WalkerSelectionDraft],
    board: Option[WalkerBoardDraft],
    modifiers: Option[ModifierWorkflow],
    facedownAdviser: Option[FacedownAdviserDraft]):
  def reconcile(context: BoardSelectionContext, projection: GameProjection): SessionDrafts
  def staged(draft: Draft): SessionDrafts
  def leave(exit: FlowExit): SessionDrafts

object SessionDrafts:
  val empty: SessionDrafts

enum Draft:
  case Partition(value: WalkerPartitionDraft)
  case Distribute(value: WalkerDistributeDraft)
  case Selection(value: WalkerSelectionDraft)
  case Board(value: WalkerBoardDraft)
  case BoardTargets(value: BoardTargetSelectionState)

enum FlowExit:
  case Restarted
  case Failed
  case Completed
  case Cancelled(restored: Option[BoardTargetSelectionState])
  case TargetsLeft
```

`reconcile` is today's seven reconcile calls with the context built once and
stored on the value. `empty` has no context; the only renders that see
`empty` with a projection are the seat-change interim frame and the reload's
"Loading" state, and a board pick with no context stages nothing (today it
would stage a draft bound to the old seat, dropped on the reload; the
observable outcome is the same, no draft survives). `ModifierWorkflow.reconcile` changes to take a
`BoardSelectionContext` like the other six; its filter is unchanged.

`staged` replaces exactly one slot. Modifier and facedown state have no
`Draft` case because no panel writes them; the modifier flow does, through
commands.

`leave` applies one of five named exits from the modifier flow:

| exit | `modifiers` | `facedownAdviser` | `boardTargets` |
| --- | --- | --- | --- |
| `Restarted` | `None` | `None` | `.map(_.cancel)` |
| `Failed` | `None` | `None` | kept |
| `Completed` | `None` | `None` | `None` |
| `Cancelled(restored)` | `None` | `None` | `restored` |
| `TargetsLeft` | `.flatMap(_.backFromTargets)` | `None` | `None` |

`Cancelled` carries the restored board-target state because computing it
needs the projection's `boardTargetActions`; the caller builds it exactly as
`restoreBoardTargetActions` does today. `Failed` and `Completed` differ only
on `boardTargets`, and that difference is observable on a non-stale submit
error (the board keeps or loses its highlight), so they stay separate.

### Sinks

Three traits replace `ServerUiView`:

- `TableControls` — every panel and the world board: `stage(draft: Draft)`,
  `submit(command)`, `handleSelection(result)`. `stage` replaces the slot and
  rerenders; it absorbs every `ui.currentX = …; ui.rerender()` pair.
- `ActionControls extends TableControls` — `ActionDecisionRenderer` and
  `FacedownAdviserRenderer` only: `toggleModifier`, `moveModifier`,
  `confirmModifiers`, `backFromModifiers`, `cancelModifiers`,
  `beginTargetedMajorAction`, `backFromTargets`, `cancelTargetAction`,
  `submitTargetCommand`, `chooseFacedownAdviser`. Wide on purpose: this is
  the modifier flow's interface, and the follow-up moves the flow behind it
  as one module.
- `SessionControls` — `DevelopmentRenderer` only: `loadSession`,
  `reconnectSession`, `createGame`, `connectionState`, `currentGameId`,
  `displayedProjection`.

No sink has a getter for a draft. `rerender`, `canControl`, `currentPlayerId`
and `currentGameId` are not sink members.

### Viewer identity

`ViewerPresentation` gains `playerId`. It is already computed from the
projection and the selected player and already passed to most renderers.
`canControl` stays the Boolean parameter it is today; it means "connected",
a session fact, not a viewer fact.

## Renderer interfaces

Renderers receive the draft set (or the one slot they read) as a value, the
presentation, `canControl`, and the narrowest sink.

| renderer | today reads off `ui` | after |
| --- | --- | --- |
| `ParkedDecision.render` | passes `ui` through | `(value, routed, canControl, panel, drafts, controls: TableControls)` |
| `WalkerPanelSupport` partition panel | partition draft, `rerender`, `submit`, `playerId` | `drafts.partition`, `controls`, `presentation.playerId` |
| `WalkerPanelSupport` board confirm | board draft, `playerId`, `submit` | `drafts.board`, `presentation.playerId`, `controls.submit` |
| `DistributePanelRenderer` | distribute draft, `rerender`, `submit` | `drafts.distribute`, `controls` |
| `WalkerSelectionPanels` | selection draft, `rerender`, `submit` | `drafts.selection`, `controls` |
| `NegotiationDealPanel` | `playerId`, `submit` | `presentation.playerId`, `controls.submit` |
| `WorldBoardRenderer.world` | board targets, board draft, `gameId`, `playerId`, `handleSelection`, `submit`, `rerender` | `drafts.boardTargets`, `drafts.board`, `drafts.context`, `presentation.playerId`, `controls` |
| `WorldBoardRenderer.players` | `playerId` | `presentation.playerId` |
| `ActionDecisionRenderer.actionsPanel` | 16 members | `(value, presentation, routed, canControl, drafts, controls: ActionControls)` |
| `FacedownAdviserRenderer` | 4 members | `(draft, canControl, controls: ActionControls)` |
| `DevelopmentRenderer.controls` | 7 members | `(session: SessionControls)` |

Every `rerender()` call in a renderer today follows a draft write
(`WalkerPanelSupport:418`, `DistributePanelRenderer:62`,
`WalkerSelectionPanels:43`, `WorldBoardRenderer:184`,
`ActionDecisionRenderer:251`); each becomes one `stage` call.

The choose-amount select (`WalkerSelectionPanels:79-87`) today writes the
draft on every change without rerendering and reads it back on confirm.
`WalkerAmountDraft.choose` clamps into `[minimum, maximum]` and the select
offers only those values, so `canConfirm` holds after any change. The select
no longer writes on change; confirm computes `draft.choose(select.value)` and
submits its command. Rendered output and submitted commands are unchanged.

## `ServerModeUi` after the slice

- `var drafts = SessionDrafts.empty` replaces seven draft `var`s.
- `store` on `Display`:
  `drafts = drafts.reconcile(BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence), displayed)`.
- `ReloadForActivePlayer`, `loadExisting` and the `StalePosition` branch of
  `submitTransport` assign `drafts = SessionDrafts.empty`.
- The five flow-exit sites call `drafts = drafts.leave(FlowExit.X)`:
  `startTargetedFlow` → `Restarted`; the `confirmModifierSelection` error
  branch → `Failed`; `completeTargetCommand` → `Completed`; `cancelModifiers`
  and `cancelTargetAction` → `Cancelled(restored)` (they are already the same
  reset, since `ModifierWorkflow.cancel` is `None` unconditionally);
  `backFromTargets` → `TargetsLeft`.
- Flow entries and steps (`submit` and `startTargetedFlow` setting
  `modifiers`; `activatePreviewTargets` setting `modifiers`, `facedownAdviser`
  and `boardTargets` together; `toggleModifier`, `moveModifier` and
  `chooseFacedownAdviser` mapping one slot) stay as `copy` on the value inside
  `ServerModeUi`. They are not named this slice: they are the modifier flow,
  and the follow-up moves them behind `ActionControls` as one module.
- `ServerModeUi` builds one anonymous `ActionControls` (which is also the
  `TableControls` every panel receives) and one `SessionControls`, the way it
  builds `ui` today. `stage(draft)` is `drafts = drafts.staged(draft); render()`.
  `handleSelection` keeps its two branches: `Updated(state)` becomes
  `stage(Draft.BoardTargets(state))`; `Submit(action, targets)` still goes to
  `completeTargetCommand`.

### Decision key

`ActionDecisionRenderer.actionsPanel` returns `ActionPane(element, prompt)`,
where `prompt` is the text of the first element it appended with class
`selection-instruction` or `modifier-confirm`, or `""`. These are the only two
live selectors of today's four (`ActionDecisionRenderer:87`, `:142`, `:153`);
`#card-decision-title` and `.resolution-choice` are emitted by nothing.
`ServerModeUi` composes the same key from values: `presentation.playerId`,
phase, active participant, `pendingCardDecision` id, `walkerDecision` id,
`drafts.boardTargets.flatMap(_.activeActionKind)`,
`drafts.modifiers.map(_.stage)`, `pane.prompt`. The string is identical, so
`PanelContent.replace`'s focus and scroll reset behavior is unchanged.

`ServerUiSupport.scala` loses the `ServerUiView` trait. Nothing else in it
moves.

## Behavior changes

Two, both ruled on 2026-09-25 as "clear all":

1. **Seat change** (`ReloadForActivePlayer`). Today clears board targets,
   modifiers and the facedown pick; the four walker drafts survive until the
   reload's `reconcile`. One `render()` runs before the reload with the old
   seat's walker drafts still set, and panels pair a draft with the parked
   decision by `decisionId` alone, so a stale partition could show for one
   frame. After: every draft is cleared; that frame shows none.
2. **Reload** (`loadExisting`). Today clears board targets and modifiers
   only; if the same game, seat and position reload, the walker drafts and
   facedown pick survive. After: every draft is cleared. A half-built draft
   does not survive a reload of the same position.

The `StalePosition` branch also assigns `empty`, but that is unobservable:
the reload lands on a new sequence, every reconciler filters on
`draft.context == context`, and no render runs in between.

## Verification

New pure suite `SessionDraftsSuite` (no jsdom): `reconcile` keeps all seven
slots under an unchanged context and drops all seven under a changed one, and
sets `context`; `staged` replaces exactly its slot for each `Draft` case;
`leave` matches the exit table, one test per exit.

The 17 suites that build `RecordingView` migrate to `RecordingControls extends
ActionControls`, which records `staged: Vector[Draft]` and
`submitted: Vector[GameIntent]`. Assertions on `ui.board` and
`ui.boardSelection` become `staged.last`; on `ui.rerenders == n` become
`staged.size == n`. `WalkerSelectionPanelsSuite`'s amount test asserts confirm
submits `choose(select.value)`'s command. A `RecordingSession extends
SessionControls` serves `DevelopmentRenderer`'s suite.

`ServerModeUiSuite`'s 14 end-to-end tests bracket the composition and run
green before and after. Two additions: a seat change renders no stale walker
draft in the interim frame (behavior change 1); `actionsPanel(...).prompt`
equals the text `querySelector` over the two live selectors finds, pinned once
in the test so the decision key is provably the same string. Behavior change 2
has no test beyond `SessionDrafts.empty` being the only path.

Gates: the full frontend suite, the coverage floor, and
`python3 scripts/check-architecture.py`. On completion, four greps:
`ServerUiView` has zero references; `querySelector` does not appear in
`ServerModeUi.scala`; `rerender` does not appear in any renderer;
`BoardSelectionContext(` is constructed once in `ServerModeUi.scala`.

## Preservation boundary

No protocol, DTO, gameplay, CSS or DOM-structure change. Control strings,
decision ids, submitted commands and the decision key are unchanged. The only
behavior changes are the two listed above.

## Files

New: `SessionDrafts.scala`, `TableControls.scala` (the three sink traits),
`SessionDraftsSuite.scala`. Edited: `ServerModeUi`, `ServerUiSupport`,
`ModifierWorkflow`, `ParkedDecision`, `ActionDecisionRenderer`,
`WorldBoardRenderer`, `WalkerPanelSupport`, `DistributePanelRenderer`,
`WalkerSelectionPanels`, `NegotiationDealPanel`, `FacedownAdviserRenderer`,
`DevelopmentRenderer`; `RecordingServerUiView.scala` and the 17 suites that
use it. The open worktree `walker-continuation-removal` touches no frontend
file.

## Follow-up (option 3)

Move the modifier flow — `submit`, `startTargetedFlow`,
`activatePreviewTargets`, `confirmModifierSelection`, `completeTargetCommand`
and the ten `ActionControls` commands — behind `ActionControls` as its own
module over `SessionDrafts`, giving the flow entries the names this slice
leaves as `copy`. Then session identity and polling, until `ServerModeUi.start`
is composition only.
