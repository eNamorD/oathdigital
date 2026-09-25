# Session Drafts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 33-member `ServerUiView` with one immutable draft set (`SessionDrafts`) and three narrow sinks, so every renderer reads drafts as values, every draft write is one `stage` call, and the eight hand-written reset subsets in `ServerModeUi` become `SessionDrafts.empty` plus five named `FlowExit`s.

**Architecture:** Strangler order. Task 1 adds the pure module and its suite with no callers. Task 2 adds the sink traits and makes the old `ServerUiView` extend `ActionControls`, so a renderer can migrate to `controls: TableControls` while its callers still pass `ui`. Tasks 3–5 migrate the renderers one cluster at a time, each with its suites; `ServerModeUi` passes a bridge `SessionDrafts` built from its existing `var`s. Task 6 swaps the seven `var`s for one `drafts` value and the reset subsets for `empty`/`leave`. Task 7 deletes `ServerUiView`, renames the fake, and runs the completion greps. Every task leaves the frontend suite green.

**Tech Stack:** Scala 3.9 LTS, Scala.js 1.22 client in `frontend/`, munit, jsdom for DOM suites (`Test / jsEnv := JSDOMNodeJSEnv`), the shared wire codecs (`GameProjectionCodec.encode` builds a JSON snapshot from a `GameProjection` value). Build wrapper is `./sbtw`.

**Spec:** [docs/superpowers/specs/2026-09-25-session-drafts-design.md](../specs/2026-09-25-session-drafts-design.md)

## Global Constraints

- **The per-task gate is `./sbtw "frontend/test"`.** Run it before every commit. The final gate (Task 7) is `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` and `python3 scripts/check-architecture.py`.
- **Behavior-preserving except the two ruled changes** (spec, "Behavior changes"): a seat change and a reload clear every draft. No protocol, DTO, gameplay, CSS or DOM-structure change. Control strings, decision ids, submitted commands and the decision key are unchanged.
- **Sink method names are `submitCommand` and `handleSelection`**, as today, not the spec's shorter `submit`: `ServerModeUi` already has a local `submit` (the modifier-preview path) and the fakes already record `submitted`. Everything else follows the spec.
- **`Draft.Board` carries `Option[WalkerBoardDraft]`**, not a bare draft: `WalkerBoardDraft.toggle` returns `None` to clear a pick, and that clear is a stage like any other.
- **Scala 3 syntax**: indentation-based blocks, `then`/`do`, `enum`. Match the files you edit.
- **Production Scala files stay at or below 800 lines** (`scripts/check-architecture.py`). The largest file touched, `ServerModeUi.scala`, shrinks.
- **Do not raise the model or effort level of any subagent above this session's** (project `CLAUDE.md`).
- **Commit messages end with** `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- **Other sessions commit to `main` in this repository.** Run `git log --oneline -1` and confirm HEAD is what you expect before any `--amend`, `reset` or `rebase`. Prefer a new commit.
- **`docs/superpowers/` is tracked**; commit any edit you make to this plan.
- **macOS `sed` needs `-i ''`** for in-place edits.

## File Structure

| File | Responsibility |
| --- | --- |
| `frontend/.../SessionDrafts.scala` | **New.** The draft set: `SessionDrafts`, `Draft`, `FlowExit`; `reconcile`, `staged`, `leave`, `empty` (Task 1). |
| `frontend/.../TableControls.scala` | **New.** The three sink traits `TableControls`, `ActionControls`, `SessionControls` (Task 2). |
| `frontend/.../ModifierWorkflow.scala` | `reconcile` takes a `BoardSelectionContext` (Task 1). |
| `frontend/.../ServerUiSupport.scala` | `ViewerPresentation.playerId` (Task 2); `ServerUiView` bridged to `ActionControls` (Task 2) and deleted (Task 7). |
| `frontend/.../WalkerPanelSupport.scala` | Recover, choose-one, board and partition panels take their slot and `controls` (Task 3). |
| `frontend/.../DistributePanelRenderer.scala`, `WalkerSelectionPanels.scala`, `NegotiationDealPanel.scala` | Same migration; the amount select stops writing on change (Task 3). |
| `frontend/.../ParkedDecision.scala` | `render` takes `presentation`, `drafts`, `controls` (Task 3). |
| `frontend/.../WorldBoardRenderer.scala` | `players`/`playerBoards` take the viewer id; `world` takes `presentation`, `canControl`, `drafts`, `controls` (Task 4). |
| `frontend/.../FacedownAdviserRenderer.scala`, `DevelopmentRenderer.scala` | `ActionControls` / `SessionControls` (Task 4). |
| `frontend/.../ActionDecisionRenderer.scala` | `status(value, presentation)`; `actionsPanel` returns `ActionPane(element, prompt)` (Task 5). |
| `frontend/.../ServerModeUi.scala` | Bridge `currentDrafts` (Tasks 3–5); one `drafts` value, `empty`, `leave`, no `querySelector` (Task 6); no `ServerUiView` (Task 7). |
| `frontend/src/test/.../SessionDraftsSuite.scala` | **New.** Pure suite (Task 1). |
| `frontend/src/test/.../ActionPanePromptSuite.scala` | **New.** Pins `ActionPane.prompt` to the old scrape (Task 5). |
| `frontend/src/test/.../RecordingServerUiView.scala` | `RecordingView` gains `staged`/`drafts` (Task 2); becomes `RecordingControls` (Task 7). |
| `frontend/src/test/.../ServerModeUiSuite.scala` | The seat-change interim-frame test (Task 6). |

Tests live in `frontend/src/test/scala/oathdigital/frontend/`, one suite per production module where one exists.

---

### Task 1: `SessionDrafts` and its pure suite

The draft set as one immutable value with the three write methods, plus `ModifierWorkflow.reconcile` taking a context so all seven reconcilers share one signature. No production caller yet.

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala:41-46`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala:130-131` (the one caller of `ModifierWorkflow.reconcile`)
- Modify: `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala:158-161`
- Create: `frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala`

**Interfaces:**
- Consumes: the seven existing reconcilers (`BoardTargetSelectionState.reconcile`, `WalkerPartitionDraft.reconcile`, `WalkerDistributeDraft.reconcile`, `WalkerSelectionDraft.reconcile`, `WalkerBoardDraft.reconcile`, `ModifierWorkflow.reconcile`, `FacedownAdviserDraft.reconcile`), unchanged except `ModifierWorkflow.reconcile`.
- Produces:
  - `final case class SessionDrafts(context: Option[BoardSelectionContext], boardTargets: Option[BoardTargetSelectionState], partition: Option[WalkerPartitionDraft], distribute: Option[WalkerDistributeDraft], selection: Option[WalkerSelectionDraft], board: Option[WalkerBoardDraft], modifiers: Option[ModifierWorkflow], facedownAdviser: Option[FacedownAdviserDraft])` with `reconcile(context: BoardSelectionContext, projection: GameProjection): SessionDrafts`, `staged(draft: Draft): SessionDrafts`, `leave(exit: FlowExit): SessionDrafts`; `SessionDrafts.empty`.
  - `enum Draft { Partition(WalkerPartitionDraft), Distribute(WalkerDistributeDraft), Selection(WalkerSelectionDraft), Board(Option[WalkerBoardDraft]), BoardTargets(BoardTargetSelectionState) }`.
  - `enum FlowExit { Restarted, Failed, Completed, Cancelled(restored: Option[BoardTargetSelectionState]), TargetsLeft }`.
  - `ModifierWorkflow.reconcile(previous: Option[ModifierWorkflow], context: BoardSelectionContext): Option[ModifierWorkflow]`.

- [ ] **Step 1: Write the failing suite**

Create `frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{MajorActionPreviewResponse, PreviewModifier}

/** The draft set as a value: what `reconcile` keeps and drops, what `staged`
  * touches, and the exact slots each `FlowExit` clears (spec, "leave").
  */
class SessionDraftsSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 9)
  private val site = (id: String) => DecisionOptionState("site", id, id)
  private val forgeQuery = DecisionQueryState("partition",
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")),
    Vector(DecisionSectionState("pay-favor", "Pay Favor", 2),
      DecisionSectionState("pay-secret", "Pay Secret", 1)))
  private val forgeParked = WalkerDecisionState("forge", "forge-9", "decide",
    query = Some(forgeQuery))
  private val travel = BoardTargetAction("travel", "Choose a destination", 1, 1,
    autoActivate = true, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  private val adviser = MinorAdviser(CardDetails("a1", "denizen", "Old Oak",
    orientation = Some("face-down")), Vector.empty)
  private def projection(parked: Option[WalkerDecisionState] = Some(forgeParked))
      : GameProjection =
    GameProjection("game", 9L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
      boardTargetActions = Vector(travel), walkerDecision = parked,
      minorActions = Some(MinorActionsState(Vector(adviser),
        canPeekSiteRelics = false, Vector.empty, None, 0, 0)))
  private val workflow = ModifierWorkflow(None, Some("travel"), Map.empty,
    MajorActionPreviewResponse(9L, "travel", Vector.empty, Vector.empty,
      Vector.empty),
    ModifierSelectionState.reconcile(None,
      ModifierSelectionContext("game", "red", 9, "travel"), Vector.empty, "p"),
    ModifierWorkflowStage.Targets)
  private val targets = BoardTargetSelectionState(context, Vector(travel),
    Some("travel"), Set.empty)

  /** Every slot filled, all bound to `context`. */
  private def full: SessionDrafts =
    val opened = SessionDrafts.empty.reconcile(context, projection())
    opened.copy(boardTargets = Some(targets), modifiers = Some(workflow),
      facedownAdviser = opened.facedownAdviser.map(_.choose("a1")))

  test("reconcile fills every slot the projection asks for and sets the context"):
    val drafts = SessionDrafts.empty.reconcile(context, projection())
    assertEquals(drafts.context, Some(context))
    assert(drafts.boardTargets.exists(_.actions == Vector(travel)))
    assert(drafts.partition.exists(_.decisionId == "forge-9"))
    assertEquals(drafts.distribute, None)
    assertEquals(drafts.selection, None)
    assertEquals(drafts.board, None)
    assertEquals(drafts.modifiers, None)
    assert(drafts.facedownAdviser.exists(_.advisers.size == 1))

  test("reconcile keeps every slot under the same context and drops them under another"):
    val kept = full.reconcile(context, projection())
    assertEquals(kept.modifiers, Some(workflow))
    assertEquals(kept.facedownAdviser.flatMap(_.selectedCardId), Some("a1"))
    assertEquals(kept.boardTargets, Some(targets))
    val moved = full.reconcile(context.copy(sequence = 10), projection())
    assertEquals(moved.modifiers, None)
    assertEquals(moved.facedownAdviser.flatMap(_.selectedCardId), None)
    assertEquals(moved.boardTargets.flatMap(_.activeActionKind), Some("travel"))
    assertEquals(moved.partition.map(_.context), Some(context.copy(sequence = 10)))

  test("staged replaces exactly its own slot"):
    val base = full
    val partition = base.partition.get.move("denizen:denizen:3", "pay-favor")
    assertEquals(base.staged(Draft.Partition(partition)),
      base.copy(partition = Some(partition)))
    val board = WalkerBoardDraft(context, "forge-9", forgeQuery, site("site:a"))
    assertEquals(base.staged(Draft.Board(Some(board))), base.copy(board = Some(board)))
    assertEquals(base.staged(Draft.Board(Some(board))).staged(Draft.Board(None)),
      base.copy(board = None))
    val activated = targets.copy(selectedKeys = Set("site:site:woods"))
    assertEquals(base.staged(Draft.BoardTargets(activated)),
      base.copy(boardTargets = Some(activated)))

  test("Restarted clears the flow and cancels the board targets"):
    val left = full.leave(FlowExit.Restarted)
    assertEquals(left.modifiers, None)
    assertEquals(left.facedownAdviser, None)
    assertEquals(left.boardTargets, full.boardTargets.map(_.cancel))
    assertEquals(left.partition, full.partition)

  test("Failed clears the flow and keeps the board targets"):
    val left = full.leave(FlowExit.Failed)
    assertEquals(left.modifiers, None)
    assertEquals(left.facedownAdviser, None)
    assertEquals(left.boardTargets, full.boardTargets)

  test("Completed clears the flow and the board targets"):
    val left = full.leave(FlowExit.Completed)
    assertEquals(left.modifiers, None)
    assertEquals(left.facedownAdviser, None)
    assertEquals(left.boardTargets, None)

  test("Cancelled clears the flow and restores the board targets it is given"):
    val restored = BoardTargetSelectionState.restore(context, Vector(travel))
    val left = full.leave(FlowExit.Cancelled(Some(restored)))
    assertEquals(left.modifiers, None)
    assertEquals(left.facedownAdviser, None)
    assertEquals(left.boardTargets, Some(restored))
    assertEquals(full.leave(FlowExit.Cancelled(None)).boardTargets, None)

  test("TargetsLeft steps the workflow back and clears the pick and the targets"):
    val ordering = workflow.copy(preview = workflow.preview.copy(modifiers =
      Vector(PreviewModifier("adviser:p:denizen:a", "h.a", "A"))))
    val left = full.copy(modifiers = Some(ordering)).leave(FlowExit.TargetsLeft)
    assertEquals(left.modifiers.map(_.stage), Some(ModifierWorkflowStage.Ordering))
    assertEquals(left.facedownAdviser, None)
    assertEquals(left.boardTargets, None)
    // A workflow that never had a modifier stage has nowhere to go back to.
    assertEquals(full.leave(FlowExit.TargetsLeft).modifiers, None)

  test("empty has no context and nothing staged"):
    assertEquals(SessionDrafts.empty, SessionDrafts(None, None, None, None,
      None, None, None, None))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SessionDraftsSuite"`
Expected: compile error, `Not found: SessionDrafts`.

- [ ] **Step 3: Change `ModifierWorkflow.reconcile` to take a context**

In `frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala`, replace lines 41–46:

```scala
  def reconcile(previous: Option[ModifierWorkflow],
      context: BoardSelectionContext): Option[ModifierWorkflow] = previous.filter:
    workflow =>
      val selected = workflow.selection.context
      selected.gameId == context.gameId && selected.playerId == context.playerId &&
        selected.sequence == context.sequence
```

In `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala`, replace lines 130–131:

```scala
          modifierWorkflow = ModifierWorkflow.reconcile(modifierWorkflow,
            BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence))
```

In `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala`, replace lines 158–161 so the three calls pass a context:

```scala
    assertEquals(ModifierWorkflow.reconcile(Some(targets),
      BoardSelectionContext("g", "p", 4)), Some(targets))
    assertEquals(ModifierWorkflow.reconcile(Some(targets),
      BoardSelectionContext("g", "p", 5)), None)
    assertEquals(ModifierWorkflow.reconcile(Some(targets),
      BoardSelectionContext("g", "other", 4)), None)
```

(Keep whatever the first assertion's expected value was on line 158–159; only the argument list changes.)

- [ ] **Step 4: Write `SessionDrafts`**

Create `frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala`:

```scala
package oathdigital.frontend

/** The draft set (CONTEXT.md): everything a viewer has staged at the table
  * and not yet sent. One immutable value, bound to one game, seat and
  * position through `context`. `ServerModeUi` holds the current one; the
  * renderers read it; every write is one of the three methods below, so the
  * question "which drafts die together" has one answer, here.
  *
  * `empty` has no context. It is what a session holds before its first
  * snapshot, and what a seat change or a reload resets to; the next
  * `reconcile` binds it.
  */
private[frontend] final case class SessionDrafts(
    context: Option[BoardSelectionContext],
    boardTargets: Option[BoardTargetSelectionState],
    partition: Option[WalkerPartitionDraft],
    distribute: Option[WalkerDistributeDraft],
    selection: Option[WalkerSelectionDraft],
    board: Option[WalkerBoardDraft],
    modifiers: Option[ModifierWorkflow],
    facedownAdviser: Option[FacedownAdviserDraft]):

  /** The set after a snapshot: each slot asked to carry over what still
    * answers the same question in the same context, and to drop the rest.
    */
  def reconcile(context: BoardSelectionContext,
      projection: GameProjection): SessionDrafts =
    SessionDrafts(
      context = Some(context),
      boardTargets = Some(BoardTargetSelectionState.reconcile(boardTargets,
        context, projection.boardTargetActions)),
      partition = WalkerPartitionDraft.reconcile(partition, context,
        projection.walkerDecision),
      distribute = WalkerDistributeDraft.reconcile(distribute, context,
        projection.walkerDecision),
      selection = WalkerSelectionDraft.reconcile(selection, context,
        projection.walkerDecision),
      board = WalkerBoardDraft.reconcile(board, context,
        projection.walkerDecision),
      modifiers = ModifierWorkflow.reconcile(modifiers, context),
      facedownAdviser = FacedownAdviserDraft.reconcile(facedownAdviser,
        context, projection.minorActions))

  /** One slot replaced; the panel that holds a draft can replace only it. */
  def staged(draft: Draft): SessionDrafts = draft match
    case Draft.Partition(value) => copy(partition = Some(value))
    case Draft.Distribute(value) => copy(distribute = Some(value))
    case Draft.Selection(value) => copy(selection = Some(value))
    case Draft.Board(value) => copy(board = value)
    case Draft.BoardTargets(value) => copy(boardTargets = Some(value))

  /** The set after leaving the modifier flow one of five ways. The table
    * is the spec's; `Failed` and `Completed` differ only on the board
    * targets, and that is observable on a submit error.
    */
  def leave(exit: FlowExit): SessionDrafts = exit match
    case FlowExit.Restarted =>
      copy(modifiers = None, facedownAdviser = None,
        boardTargets = boardTargets.map(_.cancel))
    case FlowExit.Failed =>
      copy(modifiers = None, facedownAdviser = None)
    case FlowExit.Completed =>
      copy(modifiers = None, facedownAdviser = None, boardTargets = None)
    case FlowExit.Cancelled(restored) =>
      copy(modifiers = None, facedownAdviser = None, boardTargets = restored)
    case FlowExit.TargetsLeft =>
      copy(modifiers = modifiers.flatMap(_.backFromTargets),
        facedownAdviser = None, boardTargets = None)

private[frontend] object SessionDrafts:
  val empty: SessionDrafts =
    SessionDrafts(None, None, None, None, None, None, None, None)

/** A draft a panel or the board can stage. There is no case for the
  * modifier workflow or the facedown pick: no panel writes them; the modifier
  * flow does, through its commands. `Board` carries an `Option` because a
  * second click on the picked site clears the pick, and that clear is a
  * stage like any other.
  */
private[frontend] enum Draft:
  case Partition(value: WalkerPartitionDraft)
  case Distribute(value: WalkerDistributeDraft)
  case Selection(value: WalkerSelectionDraft)
  case Board(value: Option[WalkerBoardDraft])
  case BoardTargets(value: BoardTargetSelectionState)

/** The five ways a viewer leaves the modifier flow. `Cancelled` carries the
  * restored board targets because computing them needs the projection's
  * `boardTargetActions`; the caller builds them as `restoreBoardTargetActions`
  * always did.
  */
private[frontend] enum FlowExit:
  case Restarted
  case Failed
  case Completed
  case Cancelled(restored: Option[BoardTargetSelectionState])
  case TargetsLeft
```

- [ ] **Step 5: Run the suite and the frontend gate**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SessionDraftsSuite"`
Expected: 9 tests pass.

Run: `./sbtw "frontend/test"`
Expected: green (the `ModifierSelectionStateSuite` change and the `ServerModeUi` call site compile).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala \
  frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala \
  frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala
git commit -m "feat(frontend): add the draft set as one immutable value

SessionDrafts owns reconcile, staged and leave; nothing calls it yet.
ModifierWorkflow.reconcile takes the same BoardSelectionContext as the
other six reconcilers.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: The sink traits, the bridge, and `ViewerPresentation.playerId`

Three narrow traits. `ServerUiView` extends `ActionControls` for the duration of the migration, so a renderer that now takes `controls: TableControls` still accepts the `ui` its callers pass. `ViewerPresentation` gains the viewer id so `currentPlayerId` can leave the sink.

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/TableControls.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:7-39` (trait), `:214-255` (presentation)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala:377-421` (the `ui` adapter)
- Modify: `frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala`
- Modify: the six suites constructing `ViewerPresentation`: `ActionOptionRowSuite.scala`, `ModifierSelectionStateSuite.scala`, `ParkedDecisionSuite.scala`, `PanelPlacementSuite.scala`, `RestButtonSuite.scala`, `TemporaryHandPreviewSuite.scala`

**Interfaces:**
- Consumes: `Draft`, `SessionDrafts` (Task 1).
- Produces:
  - `trait TableControls { def stage(draft: Draft): Unit; def submitCommand(command: GameCommand): Unit; def handleSelection(result: BoardSelectionResult): Unit }`
  - `trait ActionControls extends TableControls` adding `chooseFacedownAdviser(cardId: String)`, `toggleModifier(value: PreviewModifier)`, `moveModifier(value: PreviewModifier, delta: Int)`, `confirmModifiers()`, `backFromModifiers()`, `cancelModifiers()`, `beginTargetedMajorAction(actionKind: String)`, `backFromTargets()`, `cancelTargetAction()`, `submitTargetCommand(command: GameCommand)`.
  - `trait SessionControls { def currentGameId: String; def currentPlayerId: String; def displayedProjection: Option[GameProjection]; def connectionState: ServerConnectionState; def loadSession(gameId: String, playerId: String): Unit; def reconnectSession(): Unit; def createGame(): Unit }`
  - `ViewerPresentation(showGameplayControls, waitingForPlayerId, waitingForDisplayName, procedureStatus = None, playerId: String)` — `playerId` is the new last parameter, required.
  - Bridge: `ServerUiView extends ActionControls`; `RecordingView` records `staged: Vector[Draft]` and exposes `drafts: SessionDrafts` (the fold of what was staged over `empty`).

- [ ] **Step 1: Write the sink traits**

Create `frontend/src/main/scala/oathdigital/frontend/TableControls.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, PreviewModifier}

/** What a panel or the board may do to the session: stage a draft, send a
  * command, or hand over a board selection. Three members; a renderer reads
  * its drafts as values and never asks the session for them.
  */
private[frontend] trait TableControls:
  /** Replaces one slot of the draft set and rerenders. */
  def stage(draft: Draft): Unit
  def submitCommand(command: GameCommand): Unit
  def handleSelection(result: BoardSelectionResult): Unit

/** The modifier flow's commands, on top of the table's. Only the action
  * pane and the facedown-adviser panel see these. Wide on purpose: the flow
  * behind them is the follow-up's module, and it moves as one piece.
  */
private[frontend] trait ActionControls extends TableControls:
  def chooseFacedownAdviser(cardId: String): Unit
  def toggleModifier(value: PreviewModifier): Unit
  def moveModifier(value: PreviewModifier, delta: Int): Unit
  def confirmModifiers(): Unit
  def backFromModifiers(): Unit
  def cancelModifiers(): Unit
  def beginTargetedMajorAction(actionKind: String): Unit
  def backFromTargets(): Unit
  def cancelTargetAction(): Unit
  def submitTargetCommand(command: GameCommand): Unit

/** The development toolbar's view of the session: which game and seat,
  * whether it is connected, and the three ways to change it.
  */
private[frontend] trait SessionControls:
  def currentGameId: String
  def currentPlayerId: String
  def displayedProjection: Option[GameProjection]
  def connectionState: ServerConnectionState
  def loadSession(gameId: String, playerId: String): Unit
  def reconnectSession(): Unit
  def createGame(): Unit
```

- [ ] **Step 2: Bridge `ServerUiView` onto `ActionControls`**

In `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`, replace the trait at lines 7–39 with:

```scala
/** Transitional: the session as the renderers still see it. Every member
  * below the `ActionControls` line leaves in its own task; the trait itself
  * goes in the last one.
  */
private[frontend] trait ServerUiView extends ActionControls:
  def currentGameId: String
  def currentPlayerId: String
  def displayedProjection: Option[GameProjection]
  def sessionCoordinator: ServerSessionCoordinator
  def currentBoardSelection: Option[BoardTargetSelectionState]
  def currentBoardSelection_=(value: Option[BoardTargetSelectionState]): Unit
  def currentWalkerPartition: Option[WalkerPartitionDraft]
  def currentWalkerPartition_=(value: Option[WalkerPartitionDraft]): Unit
  def currentWalkerDistribution: Option[WalkerDistributeDraft]
  def currentWalkerDistribution_=(value: Option[WalkerDistributeDraft]): Unit
  def currentWalkerSelection: Option[WalkerSelectionDraft]
  def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]): Unit
  def currentWalkerBoard: Option[WalkerBoardDraft]
  def currentWalkerBoard_=(value: Option[WalkerBoardDraft]): Unit
  def currentModifierWorkflow: Option[ModifierWorkflow]
  def currentFacedownAdviserDraft: Option[FacedownAdviserDraft]
  def canControl: Boolean
  def rerender(): Unit
  def loadSession(gameId: String, playerId: String): Unit
  def reconnectSession(): Unit
  def createGame(): Unit
```

(The ten flow commands, `submitCommand` and `handleSelection` are now inherited.)

In `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala`, inside `lazy val ui: ServerUiView = new ServerUiView:` add, after `def rerender() = render()`:

```scala
      def stage(draft: Draft) =
        draft match
          case Draft.Partition(value) => walkerPartitionDraft = Some(value)
          case Draft.Distribute(value) => walkerDistributeDraft = Some(value)
          case Draft.Selection(value) => walkerSelectionDraft = Some(value)
          case Draft.Board(value) => walkerBoardDraft = value
          case Draft.BoardTargets(value) => boardSelectionState = Some(value)
        render()
```

- [ ] **Step 3: Give `ViewerPresentation` the viewer id**

In `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`, replace lines 214–219:

```scala
  private[frontend] final case class ViewerPresentation(
      showGameplayControls: Boolean,
      waitingForPlayerId: Option[String],
      waitingForDisplayName: Option[String],
      procedureStatus: Option[String] = None,
      playerId: String
  )
```

In `viewerPresentation(value, playerId)` (lines 221–255) add `playerId = playerId` to each of the five `ViewerPresentation(...)` constructions. For example line 226 becomes:

```scala
      return ViewerPresentation(showGameplayControls = false,
        waitingForPlayerId = None, waitingForDisplayName = None,
        playerId = playerId)
```

Do the same at lines 236, 239, 246 and 251.

In each of the six suites, the construction `ServerUiSupport.ViewerPresentation(showGameplayControls = true, None, None)` becomes:

```scala
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None,
        None, playerId = "red")
```

(`ModifierSelectionStateSuite` uses seat `"p"`, so there write `playerId = "p"`. `ParkedDecisionSuite`: use whatever seat its projection's `viewerPlayerId` names; `"red"` if none.)

- [ ] **Step 4: Teach the fake to record stages**

In `frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala`, add after `var rerenders: Int = 0`:

```scala
  var staged: Vector[Draft] = Vector.empty
  /** The draft set as the stages so far leave it, from `empty`. */
  def drafts: SessionDrafts = staged.foldLeft(SessionDrafts.empty)(_.staged(_))
  def stage(draft: Draft): Unit =
    staged :+= draft
    // Mirror into the transitional slots so a suite that re-renders from
    // the fake's getters keeps seeing the staged draft until it migrates.
    draft match
      case Draft.Partition(value) => partition = Some(value)
      case Draft.Distribute(value) => distribution = Some(value)
      case Draft.Selection(value) => selection = Some(value)
      case Draft.Board(value) => board = value
      case Draft.BoardTargets(value) => boardSelection = Some(value)
```

- [ ] **Step 5: Run the frontend gate**

Run: `./sbtw "frontend/test"`
Expected: green. Nothing calls `stage` yet; the six suites compile with the new parameter.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/TableControls.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala \
  frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala \
  frontend/src/test/scala/oathdigital/frontend/ActionOptionRowSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ParkedDecisionSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/PanelPlacementSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/RestButtonSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/TemporaryHandPreviewSuite.scala
git commit -m "refactor(frontend): add the three sinks and bridge ServerUiView onto them

TableControls, ActionControls and SessionControls are the interfaces the
renderers migrate to; ServerUiView extends ActionControls until the last
renderer has. ViewerPresentation carries the viewer's id.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: The walker panels and `ParkedDecision.render`

Every panel the route dispatches takes the draft slot it reads and a `TableControls`; `ParkedDecision.render` takes the whole draft set and passes each panel its slot. `ActionDecisionRenderer` (still on `ui`) passes a bridge draft set built by `ServerModeUi`.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala:71-73,86,104,122,133-146,148-150,188,303-419`
- Modify: `frontend/src/main/scala/oathdigital/frontend/DistributePanelRenderer.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/NegotiationDealPanel.scala:17-22,64-67`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala:126-145`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:45-47,271`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala:78-83` (bridge)
- Modify tests: `PartitionPanelRenderSuite.scala`, `DistributePanelRenderSuite.scala`, `WalkerSelectionPanelsSuite.scala`, `NegotiationDealPanelSuite.scala`, `BoardSurfaceSuite.scala` (the `pane` helper and its two tests), `CardChoicePanelSuite.scala:71-75`, `WalkerChoicePanelRenderSuite.scala:28-33`, `RecoverPanelSuite.scala:18-26`, `SuitGlyphSuite.scala:36-41`

**Interfaces:**
- Consumes: `SessionDrafts`, `Draft` (Task 1); `TableControls`, `ViewerPresentation.playerId`, `RecordingView.staged`/`drafts` (Task 2).
- Produces:
  - `WalkerPanelSupport.renderRecoverPanel(surface, value, canControl, panel, controls: TableControls)`
  - `WalkerPanelSupport.renderChooseOnePanel(surface, value, canControl, panel, controls: TableControls)`
  - `WalkerPanelSupport.renderBoardPanel(surface, board: Option[WalkerBoardDraft], canControl, panel, controls: TableControls)`
  - `WalkerPanelSupport.renderPartitionPanel(surface, partition: Option[WalkerPartitionDraft], playerId: String, canControl, panel, controls: TableControls)`
  - `DistributePanelRenderer.render(surface, distribute: Option[WalkerDistributeDraft], canControl, panel, controls: TableControls)`
  - `WalkerSelectionPanels.render(surface, selection: Option[WalkerSelectionDraft], canControl, panel, controls: TableControls)`
  - `NegotiationDealPanel.render(surface, playerId: String, canControl, panel, controls: TableControls)`
  - `ParkedDecision.render(value, presentation: ViewerPresentation, routed, canControl, panel, drafts: SessionDrafts, controls: TableControls)`
  - `ActionDecisionRenderer.actionsPanel(value, presentation, routed, drafts: SessionDrafts, ui: ServerUiView)` — transitional shape; Task 5 finishes it.

- [ ] **Step 1: Migrate the four panels in `WalkerPanelSupport`**

`renderRecoverPanel` (line 71–73): change the last parameter to `controls: TableControls`; the three `ui.submitCommand(` calls at lines 86, 104 and 122 become `controls.submitCommand(`.

`renderChooseOnePanel` (line 148–150): same; line 188 becomes `controls.submitCommand(`.

`renderBoardPanel` (lines 133–146) becomes:

```scala
  private[frontend] def renderBoardPanel(
      surface: ParkedDecision.Surface.Board, board: Option[WalkerBoardDraft],
      canControl: Boolean, panel: dom.Element, controls: TableControls): Unit =
    panel.appendChild(text("h2", "", decisionHeading(surface.query)))
    val draft = board.filter(_.decisionId == surface.decision.decisionId)
    panel.appendChild(text("p", "board-draft",
      draft.fold("Choose a site on the board.")(_.option.label)))
    if surface.confirm then
      val confirm = button(partitionConfirmLabel(surface.query),
        "walker-board-confirm")
      confirm.disabled = !canControl || draft.isEmpty
      confirm.onclick = _ =>
        draft.foreach(value => controls.submitCommand(value.command))
      panel.appendChild(confirm)
```

`renderPartitionPanel` and its four private helpers (lines 303–419): the signature becomes

```scala
  private[frontend] def renderPartitionPanel(
      surface: ParkedDecision.Surface.Partition,
      partition: Option[WalkerPartitionDraft], playerId: String,
      canControl: Boolean, panel: dom.Element, controls: TableControls): Unit =
```

Inside it, `ui.currentWalkerPartition.filter(...)` becomes `partition.filter(...)`, `draft.command(ui.currentPlayerId).foreach(ui.submitCommand)` becomes `draft.command(playerId).foreach(controls.submitCommand)`, and every `ui: ServerUiView` parameter on `partitionZone`, `partitionOption`, `moveOption` and `update` becomes `controls: TableControls` (rename the argument at each call too). The last helper becomes:

```scala
  private def update(moved: WalkerPartitionDraft, draft: WalkerPartitionDraft,
      controls: TableControls): Unit =
    if moved != draft then controls.stage(Draft.Partition(moved))
```

- [ ] **Step 2: Migrate `DistributePanelRenderer`**

Replace the `render` signature and the two helpers' `ui` parameters:

```scala
  def render(surface: ParkedDecision.Surface.Distribute,
      distribute: Option[WalkerDistributeDraft], canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
```

`ui.currentWalkerDistribution.filter(...)` becomes `distribute.filter(...)`; `draft.command.foreach(ui.submitCommand)` becomes `draft.command.foreach(controls.submitCommand)`; `row(..., ui)` and `stepper(..., ui, ...)` take `controls: TableControls`; the stepper's click becomes:

```scala
    control.onclick = (event: dom.MouseEvent) =>
      controls.stage(Draft.Distribute(next(event.shiftKey)))
```

- [ ] **Step 3: Migrate `WalkerSelectionPanels`, and stop the amount select writing on change**

Replace the whole object body's three signatures and handlers:

```scala
  def render(surface: ParkedDecision.Surface.Selection,
      selection: Option[WalkerSelectionDraft], canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val draft = selection.filter(_.decisionId == surface.decision.decisionId)
    surface.form match
      case DecisionForm.ChooseMany => draft
        .collect { case many: WalkerChooseManyDraft => many }
        .foreach(renderMany(surface.query, _, canControl, panel, controls))
      case DecisionForm.ChooseAmount => draft
        .collect { case amount: WalkerAmountDraft => amount }
        .foreach(renderAmount(surface.decision, surface.query, _, canControl,
          panel, controls))
```

In `renderMany` (now `controls: TableControls`), the toggle's click becomes `toggle.onclick = _ => controls.stage(Draft.Selection(draft.toggle(item)))` and the confirm's `controls.submitCommand`.

`renderAmount` (now `controls: TableControls`): replace its doc comment and the two handlers. The doc comment becomes:

```scala
  /** A dropdown over the range. Nothing is staged on change: the select
    * itself holds the chosen amount, and `choose` clamps into the range the
    * select was built from, so confirm reads the control and submits.
    */
```

Delete the `select.onchange = ...` statement. The confirm becomes:

```scala
    confirm.onclick = _ => select.value.toIntOption
      .flatMap(value => draft.choose(value).command)
      .foreach(controls.submitCommand)
```

- [ ] **Step 4: Migrate `NegotiationDealPanel`**

```scala
  def render(surface: ParkedDecision.Surface.Negotiate, playerId: String,
      canControl: Boolean, panel: dom.Element, controls: TableControls): Unit =
    summary(surface.deal, panel)
    surface.editor.foreach { case (decisionId, editing) =>
      editor(decisionId, surface.deal, editing, playerId, canControl, panel,
        controls)
    }
```

`editor` gains `playerId: String` before `canControl` and takes `controls: TableControls` instead of `ui`; `val me = ui.currentPlayerId` becomes `val me = playerId`; every `ui.submitCommand` in it becomes `controls.submitCommand`.

- [ ] **Step 5: Migrate `ParkedDecision.render`**

Replace lines 126–145:

```scala
  def render(value: GameProjection, presentation: ViewerPresentation,
      routed: Routed, canControl: Boolean, panel: dom.Element,
      drafts: SessionDrafts, controls: TableControls): Unit =
    routed.surface.foreach:
      case surface: Surface.Recover => WalkerPanelSupport.renderRecoverPanel(
        surface, value, canControl, panel, controls)
      case surface: Surface.ChooseOne => WalkerPanelSupport.renderChooseOnePanel(
        surface, value, canControl, panel, controls)
      case surface: Surface.Partition => WalkerPanelSupport.renderPartitionPanel(
        surface, drafts.partition, presentation.playerId, canControl, panel,
        controls)
      case surface: Surface.Distribute => DistributePanelRenderer.render(
        surface, drafts.distribute, canControl, panel, controls)
      case surface: Surface.Negotiate => NegotiationDealPanel.render(
        surface, presentation.playerId, canControl, panel, controls)
      case surface: Surface.Selection => WalkerSelectionPanels.render(
        surface, drafts.selection, canControl, panel, controls)
      case surface: Surface.Board => WalkerPanelSupport.renderBoardPanel(
        surface, drafts.board, canControl, panel, controls)
    routed.notice.foreach(notice =>
      panel.appendChild(text("p", "walker-waiting", notice)))
```

- [ ] **Step 6: Thread the draft set through `ActionDecisionRenderer` and the bridge in `ServerModeUi`**

`ActionDecisionRenderer.actionsPanel` (line 45–47) gains a parameter:

```scala
 def actionsPanel(value: GameProjection, presentation: ViewerPresentation,
     routed: ParkedDecision.Routed, drafts: SessionDrafts,
     ui: ServerUiView): dom.Element =
```

and line 271 becomes `ParkedDecision.render(value, presentation, routed, canControl, panel, drafts, ui)`.

In `ServerModeUi.render`, inside the `case Some(value) =>` branch, before `val routed = ...`, add the bridge:

```scala
          // Transitional: the draft set assembled from the session's vars.
          // Task 6 makes it the session's one value.
          val currentDrafts = SessionDrafts(
            Some(BoardSelectionContext(gameId, selectedPlayer, value.nextSequence)),
            boardSelectionState, walkerPartitionDraft, walkerDistributeDraft,
            walkerSelectionDraft, walkerBoardDraft, modifierWorkflow,
            facedownAdviserDraft)
```

and pass it: `ActionDecisionRenderer.actionsPanel(value, presentation, routed, currentDrafts, ui)`.

- [ ] **Step 7: Migrate the nine suites**

Each suite passes the draft it used to seed on the fake as a parameter, and reads what was staged back through `ui.drafts`. Concretely:

`PartitionPanelRenderSuite.scala` — `render` and `opened` become:

```scala
  private def render(ui: RecordingView, draft: Option[WalkerPartitionDraft],
      canControl: Boolean = true,
      decision: WalkerDecisionState = parked): dom.Element =
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderPartitionPanel(
      Surface.Partition(decision, decision.query.get), draft, "red",
      canControl, panel, ui)
    panel

  private def opened(): Option[WalkerPartitionDraft] =
    WalkerPartitionDraft.reconcile(None,
      BoardSelectionContext("game", "red", 9), Some(parked))
```

Every test then holds `val ui = new RecordingView("game", "red")` and `val draft = opened()`, calls `render(ui, draft)`, and where it re-rendered after a click (`render(ui)` at lines 179, 237 and the confirm tests) it re-renders with `render(ui, ui.drafts.partition)`. `assertEquals(ui.rerenders, 1)` becomes `assertEquals(ui.staged.size, 1)`.

`DistributePanelRenderSuite.scala` — the same shape: `opened()` returns `Option[WalkerDistributeDraft]`; `render(ui, draft, canControl)`; after clicks re-render with `ui.drafts.distribute`; `assert(ui.rerenders >= 3)` becomes `assert(ui.staged.size >= 3)`; the ranged test at lines 100–110 passes its draft the same way.

`WalkerSelectionPanelsSuite.scala` — `opened` returns `Option[WalkerSelectionDraft]`; `render(ui, draft, id, query, ...)` passes it; the choose-many test re-renders from `ui.drafts.selection` after each toggle. The choose-amount test keeps `select.value = "5"` and the `change` dispatch (harmless now), and its final assertion is unchanged — confirm submits `ChooseAmountWire(5)` read from the select.

`NegotiationDealPanelSuite.scala` — the `render` helper gains a seat parameter and passes it, so nothing reads the fake's constructor arguments:

```scala
  private def render(surface: Surface.Negotiate, ui: RecordingView,
      canControl: Boolean = true, playerId: String = "red"): dom.Element =
    val panel = dom.document.createElement("div")
    NegotiationDealPanel.render(surface, playerId, canControl, panel, ui)
    panel
```

The test at line 84 that builds `new RecordingView("game", "green")` passes `playerId = "green"` to `render` as well.

`BoardSurfaceSuite.scala` — the `pane` helper takes the board draft:

```scala
  private def pane(ui: RecordingView, board: Surface.Board,
      draft: Option[WalkerBoardDraft] = None): dom.Element =
    val panel = dom.document.createElement("div")
    ParkedDecision.render(projection(),
      ServerUiSupport.viewerPresentation(projection(), "red"),
      Routed(Some(board), None), canControl = true, panel,
      SessionDrafts.empty.copy(context = Some(context), board = draft), ui)
    panel
```

In "the pane's Confirm waits for a draft, then submits it", `ui.board = Some(woodsDraft)` followed by `pane(ui, surface(confirm = true))` becomes `pane(ui, surface(confirm = true), Some(woodsDraft))`. Leave the `world(...)` tests alone; Task 4 migrates them.

`CardChoicePanelSuite.scala:71-75`, `WalkerChoicePanelRenderSuite.scala:28-33`, `RecoverPanelSuite.scala:18-26`, `SuitGlyphSuite.scala:36-41` — the argument lists are unchanged (a `RecordingView` is a `TableControls`); they compile as they are. Run them anyway.

- [ ] **Step 8: Run the frontend gate**

Run: `./sbtw "frontend/test"`
Expected: green. If `WalkerSelectionPanelsSuite`'s amount test fails on the submitted value, the `change` dispatch is not the cause: check the confirm reads `select.value`, not the draft's `amount`.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala \
  frontend/src/main/scala/oathdigital/frontend/DistributePanelRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala \
  frontend/src/main/scala/oathdigital/frontend/NegotiationDealPanel.scala \
  frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala \
  frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala \
  frontend/src/test/scala/oathdigital/frontend/PartitionPanelRenderSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/DistributePanelRenderSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/NegotiationDealPanelSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/BoardSurfaceSuite.scala
git commit -m "refactor(frontend): hand each parked-decision panel its draft and a TableControls

Panels read the slot they need as a value and stage a replacement through
one method; the choose-amount select no longer writes on change, since
confirm reads the control.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: The world board, the facedown-adviser panel and the development toolbar

`WorldBoardRenderer` reads the board targets, the board draft and the context off the draft set; `FacedownAdviserRenderer` takes `ActionControls`; `DevelopmentRenderer` takes `SessionControls`.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:7-8,31-37,139-190`
- Modify: `frontend/src/main/scala/oathdigital/frontend/FacedownAdviserRenderer.scala:7-8`
- Modify: `frontend/src/main/scala/oathdigital/frontend/DevelopmentRenderer.scala:7-8,38`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:150-152` (the facedown call)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` (the `players`/`world`/`controls` calls; a `SessionControls` adapter)
- Modify tests: `BoardSurfaceSuite.scala` (the `world` helper, `SelectingView`), `SiteBoxLayoutSuite.scala:25-50`, `PanelPlacementSuite.scala:37-38`, `PlayerBoardSuite.scala` (five `playerBoards` calls), `SuitGlyphSuite.scala:16-23`, `CardChoicePanelSuite.scala:26-27`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces:
  - `WorldBoardRenderer.players(value, playerId: String)`, `WorldBoardRenderer.playerBoards(value, playerId: String)`
  - `WorldBoardRenderer.world(value, board: Option[ParkedDecision.Surface.Board], presentation: ViewerPresentation, canControl: Boolean, drafts: SessionDrafts, controls: TableControls)`
  - `FacedownAdviserRenderer.render(draft, canControl: Boolean, controls: ActionControls)`
  - `DevelopmentRenderer.controls(session: SessionControls)`

- [ ] **Step 1: Migrate `WorldBoardRenderer`**

Lines 7–8 and 31–37: `players` and `playerBoards` take `playerId: String`; the seat order line becomes `seatOrder(value.players, Some(playerId).filter(_.nonEmpty))`.

`world` (from line 139) becomes:

```scala
 def world(
     value: GameProjection,
     board: Option[ParkedDecision.Surface.Board],
     presentation: ViewerPresentation,
     canControl: Boolean,
     drafts: SessionDrafts,
     controls: TableControls
 ): dom.Element =
   val panel = element("section", "panel world")
```

(delete `import ui._`). In the site loop:

```scala
       val candidate = drafts.boardTargets.flatMap(
         _.activeAction.flatMap(_.candidates.find(_.target == siteTarget)))
       val siteOption = board.flatMap(_.query.options.find(option =>
         option.kind == "site" && option.id == site.siteId))
       val isSelected = drafts.boardTargets.exists(_.selected(siteTarget)) ||
         siteOption.exists(option => drafts.board.exists(_.option == option))
```

and the two handlers:

```scala
       candidate.foreach { _ =>
         pickable(control, () => drafts.boardTargets.foreach(state =>
           controls.handleSelection(state.choose(siteTarget))))
       }
       siteOption.foreach { option =>
         pickable(control, () => if canControl then board.foreach { surface =>
           if surface.confirm then
             // No context means no bound session (a seat change's interim
             // frame); a pick then stages nothing, as the spec rules.
             drafts.context.foreach(context => controls.stage(Draft.Board(
               WalkerBoardDraft.toggle(drafts.board, context,
                 surface.decision.decisionId, surface.query, option))))
           else controls.submitCommand(WalkerPanelSupport.resolveChooseOneCommand(
             surface.decision, option))
         })
       }
```

Search the rest of `world` for any other `currentBoardSelection`, `currentPlayerId` or `submitCommand` use (e.g. `playerReference` calls are fine; they take `value`) and route it through `drafts`, `presentation.playerId` or `controls`.

- [ ] **Step 2: Migrate `FacedownAdviserRenderer`**

Line 7–8:

```scala
  def render(draft: FacedownAdviserDraft, canControl: Boolean,
      controls: ActionControls): dom.Element =
```

Delete `import ui._`; the three handlers use `controls.chooseFacedownAdviser(...)`, `controls.submitTargetCommand`, `controls.cancelTargetAction()`; `start.disabled = !canControl` keeps the parameter. In `ActionDecisionRenderer.scala:150-152`, the call becomes `FacedownAdviserRenderer.render(currentFacedownAdviserDraft.get, canControl, ui)`.

- [ ] **Step 3: Migrate `DevelopmentRenderer`**

Line 7–8: `def controls(session: SessionControls): dom.Element =` with `import session._`; line 38 `sessionCoordinator.connectionState match` becomes `connectionState match`. Nothing else in the body changes.

- [ ] **Step 4: Call sites in `ServerModeUi`**

In `render`: `WorldBoardRenderer.players(value, selectedPlayer)` and

```scala
            WorldBoardRenderer.world(value, routed.surface.collect {
              case board: ParkedDecision.Surface.Board => board
            }, presentation, ui.canControl, currentDrafts, ui), decisionKey)
```

and `development.appendChild(DevelopmentRenderer.controls(session))`, where `session` is a new adapter next to `ui`:

```scala
    lazy val session: SessionControls = new SessionControls:
      def currentGameId = gameId
      def currentPlayerId = selectedPlayer
      def displayedProjection = projection
      def connectionState = coordinator.connectionState
      def loadSession(id: String, playerId: String) = loadExisting(id, playerId)
      def reconnectSession() = reconnect()
      def createGame() = newGame()
```

Remove `currentGameId`, `displayedProjection`, `sessionCoordinator`, `loadSession`, `reconnectSession` and `createGame` from `ui` and from the `ServerUiView` trait (keep `currentPlayerId`: `ActionDecisionRenderer` still reads it until Task 5). Remove the same six from `RecordingView` (and its `sessionCoordinator` val).

- [ ] **Step 5: Migrate the suites**

`BoardSurfaceSuite.scala` — the `world` helper:

```scala
  private def world(ui: RecordingView, board: Option[Surface.Board] =
      Some(surface(confirm = true)), draft: Option[WalkerBoardDraft] = None,
      targets: Option[BoardTargetSelectionState] = None): dom.Element =
    WorldBoardRenderer.world(projection(), board,
      ServerUiSupport.viewerPresentation(projection(), "red"),
      canControl = true,
      SessionDrafts.empty.copy(context = Some(context), board = draft,
        boardTargets = targets), ui)
```

Then: tests that set `ui.board = Some(woodsDraft)` pass `draft = Some(woodsDraft)` instead; `assertEquals(ui.board, X)` becomes `assertEquals(ui.drafts.board, X)` where a click happened, and `assertEquals(ui.staged, Vector.empty)` where the test asserts nothing was drafted with no click (the two card-click tests); `assertEquals(ui.rerenders, 1)` becomes `assertEquals(ui.staged.size, 1)`. "a second click on the drafted site clears the draft" asserts `assertEquals(ui.staged, Vector(Draft.Board(None)))`. The `SelectingView` test passes its `BoardTargetSelectionState` as `targets = Some(...)` instead of `ui.boardSelection = ...`.

`SiteBoxLayoutSuite.scala` — `world(value, ui)` becomes `world(value, targets: Option[BoardTargetSelectionState] = None)` building the drafts the same way; the travel-badge test passes its state as `targets`.

`PanelPlacementSuite.scala:37-38`, `SuitGlyphSuite.scala:16-23` — pass `ServerUiSupport.viewerPresentation(value, "red")`, `canControl = true`, `SessionDrafts.empty`, and the `RecordingView` as `controls`.

`PlayerBoardSuite.scala` — every `WorldBoardRenderer.playerBoards(projection, new RecordingView("game", X))` becomes `WorldBoardRenderer.playerBoards(projection, X)` (line 134 passes `seat`).

`CardChoicePanelSuite.scala:26-27` — `FacedownAdviserRenderer.render(value, canControl = true, new RecordingView("game", "red"))`.

- [ ] **Step 6: Run the frontend gate**

Run: `./sbtw "frontend/test"`
Expected: green.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/FacedownAdviserRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/DevelopmentRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala \
  frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala \
  frontend/src/test/scala/oathdigital/frontend/BoardSurfaceSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/SiteBoxLayoutSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/PanelPlacementSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/SuitGlyphSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/CardChoicePanelSuite.scala
git commit -m "refactor(frontend): read the board's drafts as values, and split the dev toolbar's sink

WorldBoardRenderer takes the draft set and a TableControls; the facedown
panel takes ActionControls; DevelopmentRenderer takes SessionControls.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: The action pane returns its prompt

`ActionDecisionRenderer` reads drafts and `presentation.playerId`, takes `ActionControls`, and returns `ActionPane(element, prompt)` so `ServerModeUi` composes the decision key from values instead of scraping the DOM.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:5-8,45-50,73,86-87,92-93,142,150-171,246-253`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala:78-92`
- Create: `frontend/src/test/scala/oathdigital/frontend/ActionPanePromptSuite.scala`
- Modify tests: `ActionOptionRowSuite.scala:21-32`, `ModifierSelectionStateSuite.scala:27-35`, `PanelPlacementSuite.scala:40-43`, `RestButtonSuite.scala:14-24`, `TemporaryHandPreviewSuite.scala:12-20`, `ServerModeUiSuite.scala:731,747`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces:
  - `final case class ActionPane(element: dom.Element, prompt: String)` in `ActionDecisionRenderer`'s companion scope.
  - `ActionDecisionRenderer.status(value, presentation: ViewerPresentation): dom.Element`
  - `ActionDecisionRenderer.actionsPanel(value, presentation, routed, canControl: Boolean, drafts: SessionDrafts, controls: ActionControls): ActionPane`

- [ ] **Step 1: Write the failing prompt suite**

Create `frontend/src/test/scala/oathdigital/frontend/ActionPanePromptSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.MajorActionPreviewResponse
import org.scalajs.dom

/** The prompt the action pane reports is the text the old DOM scrape
  * (`querySelector(".selection-instruction,.modifier-confirm")`) found, so
  * the decision key `ServerModeUi` composes is the same string it was.
  */
class ActionPanePromptSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 1L)
  private val travel = BoardTargetAction("travel", "Choose a destination", 1, 1,
    autoActivate = true, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  private def projection(open: Boolean): GameProjection =
    GameProjection("game", 1L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
      actionSelectionOpen = open, boardTargetActions = Vector(travel))
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None, playerId = "red")
  private def pane(value: GameProjection, drafts: SessionDrafts)
      : ActionDecisionRenderer.ActionPane =
    ActionDecisionRenderer.actionsPanel(value, presentation,
      ParkedDecision.Routed(None, None), canControl = true, drafts,
      new RecordingView("game", "red"))
  private def scraped(element: dom.Element): String =
    Option(element.querySelector(".selection-instruction,.modifier-confirm"))
      .map(_.textContent).getOrElse("")

  test("the prompt is the active board target's instruction"):
    val drafts = SessionDrafts.empty.copy(boardTargets = Some(
      BoardTargetSelectionState(context, Vector(travel), Some("travel"), Set.empty)))
    val result = pane(projection(open = false), drafts)
    assertEquals(result.prompt, "Choose a destination")
    assertEquals(result.prompt, scraped(result.element))

  test("the prompt is the modifier confirm while ordering"):
    val workflow = ModifierWorkflow(None, Some("travel"), Map.empty,
      MajorActionPreviewResponse(1L, "travel", Vector.empty, Vector.empty,
        Vector.empty),
      ModifierSelectionState.reconcile(None,
        ModifierSelectionContext("game", "red", 1L, "travel"), Vector.empty, "p"),
      ModifierWorkflowStage.Ordering)
    val result = pane(projection(open = true),
      SessionDrafts.empty.copy(modifiers = Some(workflow)))
    assertEquals(result.prompt, "Confirm modifier order")
    assertEquals(result.prompt, scraped(result.element))

  test("no question in flight is an empty prompt"):
    val result = pane(projection(open = true), SessionDrafts.empty)
    assertEquals(result.prompt, "")
    assertEquals(result.prompt, scraped(result.element))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ActionPanePromptSuite"`
Expected: compile error, `Not found: ActionPane` (or a signature mismatch on `actionsPanel`).

- [ ] **Step 3: Migrate `ActionDecisionRenderer`**

Add the result type after the object's opening line:

```scala
private[frontend] object ActionDecisionRenderer:
 /** The pane and the one line `ServerModeUi` keys the panel's focus reset
   * on: the first selection instruction or modifier confirm appended, or
   * "" -- what the old `querySelector` over the same two classes returned.
   */
 final case class ActionPane(element: dom.Element, prompt: String)
```

`status` (line 5–8) becomes `def status(value: GameProjection, presentation: ViewerPresentation): dom.Element =` with the `import ui._` and the local `val presentation = ...` line deleted.

`actionsPanel` (line 45) becomes:

```scala
 def actionsPanel(value: GameProjection, presentation: ViewerPresentation,
     routed: ParkedDecision.Routed, canControl: Boolean,
     drafts: SessionDrafts, controls: ActionControls): ActionPane =
   import controls._
   var prompt = ""
   def promptOnce(line: String): Unit = if prompt.isEmpty then prompt = line
   val panel = element("section", "panel wake-actions")
   panel.appendChild(text("h2", "", "Available actions"))
   panel.appendChild(status(value, presentation))
```

Then, through the body:
- `currentPlayerId` (lines 73, 246) → `presentation.playerId`.
- `currentBoardSelection` (lines 86, 92, 166, 168, 251) → `drafts.boardTargets`.
- `currentModifierWorkflow` (lines 93–94, 160) → `drafts.modifiers`.
- `currentFacedownAdviserDraft` (lines 150–152) → `drafts.facedownAdviser`; the call is `FacedownAdviserRenderer.render(drafts.facedownAdviser.get, canControl, controls)`.
- The two `panel.appendChild(text("p", "selection-instruction", action.prompt))` lines (87 and 153): add `promptOnce(action.prompt)` on the line before each.
- Line 142 `val confirm = button("Confirm modifier order", "modifier-confirm")`: add `promptOnce("Confirm modifier order")` on the line before.
- Lines 251–253 become `drafts.boardTargets.foreach(state => stage(Draft.BoardTargets(state.activate(action.actionKind))))`.
- Line 271: `ParkedDecision.render(value, presentation, routed, canControl, panel, drafts, controls)`.
- The last line `panel` becomes `ActionPane(panel, prompt)`.

`import controls._` brings `submitCommand`, `toggleModifier`, `moveModifier`, `confirmModifiers`, `backFromModifiers`, `cancelModifiers`, `beginTargetedMajorAction`, `backFromTargets`, `cancelTargetAction`, `handleSelection` and `stage` into scope, so those call sites are unchanged. `rerender()` at line 253 is gone with the `stage`.

- [ ] **Step 4: Compose the key in `ServerModeUi`**

Replace lines 78–92 of `render`'s `case Some(value)` branch:

```scala
          val presentation = viewerPresentation(value, selectedPlayer)
          val currentDrafts = SessionDrafts(
            Some(BoardSelectionContext(gameId, selectedPlayer, value.nextSequence)),
            boardSelectionState, walkerPartitionDraft, walkerDistributeDraft,
            walkerSelectionDraft, walkerBoardDraft, modifierWorkflow,
            facedownAdviserDraft)
          // Routed once per render: both panes read the same answer.
          val routed = ParkedDecision.route(value, presentation)
          val pane = ActionDecisionRenderer.actionsPanel(value, presentation,
            routed, ui.canControl, currentDrafts, ui)
          actionContent.appendChild(pane.element)
          val decisionKey = Vector(selectedPlayer, value.phase,
            value.activeParticipantId.getOrElse(""),
            value.pendingCardDecision.map(_.decisionId).getOrElse(""),
            value.walkerDecision.map(_.decisionId).getOrElse(""),
            currentDrafts.boardTargets.flatMap(_.activeActionKind).getOrElse(""),
            currentDrafts.modifiers.map(_.stage.toString).getOrElse(""),
            pane.prompt).mkString("|")
```

(The `querySelector` and the `prompt` val are gone.) Remove `currentPlayerId` from `ui` and from `ServerUiView`; the trait now declares only the five draft getter/setter pairs, `currentModifierWorkflow`, `currentFacedownAdviserDraft`, `canControl` and `rerender`.

- [ ] **Step 5: Migrate the suites**

`ActionOptionRowSuite.scala`, `RestButtonSuite.scala`, `TemporaryHandPreviewSuite.scala`, `PanelPlacementSuite.scala` — each `actionsPanel(projection, presentation, Routed(None, None), new RecordingView(...))` becomes `actionsPanel(projection, presentation, Routed(None, None), canControl = true, SessionDrafts.empty, new RecordingView("game", "red")).element`.

`ModifierSelectionStateSuite.scala:27-35` — replace the `view` lines: drop `view.modifierWorkflow = Some(workflow)` and pass `SessionDrafts.empty.copy(modifiers = Some(workflow))` as `drafts`, `canControl = true`, and `new RecordingView("g", "p")` as `controls`; take `.element` of the result. Delete the doc comment at lines 15–17 that explains the fake's settable `modifierWorkflow`.

`ServerModeUiSuite.scala:731,747` — `ActionDecisionRenderer.status(value, ServerUiSupport.viewerPresentation(value, "blue-exile"))`.

Remove `currentPlayerId`, `currentGameId` and `modifierWorkflow` from `RecordingView` if any remain; keep the constructor arguments (Task 7 drops them).

- [ ] **Step 6: Run the suite and the gate**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ActionPanePromptSuite"`
Expected: 3 tests pass.

Run: `./sbtw "frontend/test"`
Expected: green.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala \
  frontend/src/test/scala/oathdigital/frontend/ActionPanePromptSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala \
  frontend/src/test/scala/oathdigital/frontend/ActionOptionRowSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/PanelPlacementSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/RestButtonSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/TemporaryHandPreviewSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala
git commit -m "refactor(frontend): return the action pane's prompt instead of scraping it

ActionDecisionRenderer reads the draft set and ActionControls, and hands
ServerModeUi the one line its decision key needs; the querySelector and
its two dead selectors go.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: One `drafts` value in `ServerModeUi`

The seven draft `var`s become one; the reconcile block becomes one line; the three session sites assign `empty`; the five flow exits call `leave`. The seat-change test is written first and fails against the old reset.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` (throughout `start`)
- Modify: `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala` (one new test)

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: no new interface. `ServerModeUi.start` holds `var drafts: SessionDrafts`; the transitional `currentDrafts` bridge is gone.

- [ ] **Step 1: Write the failing seat-change test**

In `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala`, after the test "development root retains query loading and raw history", add:

```scala
  /** Spec, behavior change 1. A development session follows the active
    * player; the render between the seat change and the reload used to show
    * the previous seat's walker drafts, since they were cleared only by the
    * reload's reconcile. The reload here never lands, so the interim frame
    * is what the test sees.
    */
  test("a seat change clears the walker drafts before the interim render"):
    val browser = new TestBrowser("?gameId=g&playerId=red")
    def snapshot(sequence: Long, active: String): String =
      oathdigital.protocol.projection.GameProjectionCodec.encode(
        GameProjection("g", sequence, "act", Some(active),
          Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red),
            GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)),
          Vector.empty, Vector.empty, Vector.empty, ready = false,
          completed = false, walkerDecision = Some(forgeParked)))
    var loads = 0
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        if url.contains("/events") then
          Future.successful(Right(TransportResponse(200, """{"events":[]}""")))
        else
          loads += 1
          loads match
            case 1 => Future.successful(Right(TransportResponse(200, snapshot(1L, "red"))))
            case 2 => Future.successful(Right(TransportResponse(200, snapshot(2L, "blue"))))
            case _ => Future.never
    Main.start(browser.mount, "/", trustedAlpha = false, transport)
    browser.settle.flatMap { _ => browser.tick(); browser.settle }.map { _ =>
      assertEquals(loads, 3)
      // The route still shows Forge's partition heading to the new seat;
      // the zones come from a draft, and there is none.
      assert(browser.byClass("partition-zones").isEmpty, browser.text)
    }.andThen { case _ => browser.close() }
```

`forgeParked` is the suite's existing fixture (line 394). `PlayerColor.Blue` exists (`PlayerColor.scala:13`).

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ServerModeUiSuite"`
Expected: the new test fails on `partition-zones` being non-empty (the stale draft renders); every other test passes.

- [ ] **Step 3: Replace the seven vars with one value**

In `ServerModeUi.start`, delete lines 21–27 (`boardSelectionState` through `facedownAdviserDraft`) and add:

```scala
    var drafts = SessionDrafts.empty
```

Then, site by site:

`render`, `case Some(value)`: delete the `currentDrafts` bridge and pass `drafts` in its place to `actionsPanel`, `world` and the `decisionKey` reads (`drafts.boardTargets...`, `drafts.modifiers...`).

`store`, `case ProjectionRoute.Display` (lines 130–153): replace the seven reconcile assignments with

```scala
          drafts = drafts.reconcile(
            BoardSelectionContext(gameId, selectedPlayer, displayed.nextSequence),
            displayed)
```

`store`, `case ProjectionRoute.ReloadForActivePlayer` (lines 171–173): replace the three `= None` lines with `drafts = SessionDrafts.empty`.

`loadExisting` (lines 206–207): replace the two `= None` lines with `drafts = SessionDrafts.empty`.

`submitTransport`, the `StalePosition` branch (lines 272–273): replace the two `= None` lines with `drafts = SessionDrafts.empty`.

`submit` (line 303–308): `ModifierSelectionState.reconcile(modifierWorkflow.map(_.selection), ...)` becomes `ModifierSelectionState.reconcile(drafts.modifiers.map(_.selection), ...)`, and the assignment becomes `drafts = drafts.copy(modifiers = Some(ModifierWorkflow(...)))`.

`activatePreviewTargets` becomes:

```scala
    def activatePreviewTargets(workflow: ModifierWorkflow,
        response: MajorActionPreviewResponse): Unit = for
      current <- projection
      context <- drafts.context
      actionKind <- workflow.actionKind
    do
      val entered = if actionKind == "play-facedown-adviser" then
        drafts.copy(facedownAdviser = current.minorActions.flatMap(
          FacedownAdviserDraft.initial(context, _)), boardTargets = None)
      else ModifierWorkflow.targetAction(actionKind, response,
        current.boardTargetActions).fold(drafts)(action =>
        drafts.copy(boardTargets = Some(BoardTargetSelectionState.reconcile(None,
          context, Vector(action)).activate(actionKind))))
      drafts = entered.copy(modifiers = Some(workflow.showTargets(response)))
      render()
```

`startTargetedFlow`: the three lines `modifierWorkflow = None; facedownAdviserDraft = None; boardSelectionState = boardSelectionState.map(_.cancel)` become `drafts = drafts.leave(FlowExit.Restarted)`; `if workflow.ordering then { modifierWorkflow = Some(workflow); render() }` becomes `if workflow.ordering then { drafts = drafts.copy(modifiers = Some(workflow)); render() }`.

`confirmModifierSelection`: `modifierWorkflow.foreach { workflow =>` becomes `drafts.modifiers.foreach { workflow =>`; in `case Some(command) =>`, `modifierWorkflow = None` becomes `drafts = drafts.copy(modifiers = None)`; in `case Left(error) =>`, the two `= None` lines become `drafts = drafts.leave(FlowExit.Failed)`.

`completeTargetCommand`: `modifierWorkflow.filter(...)` becomes `drafts.modifiers.filter(...)`; the three `= None` lines become `drafts = drafts.leave(FlowExit.Completed)`.

`restoreBoardTargetActions` is deleted; in its place:

```scala
    def restoredTargets: Option[BoardTargetSelectionState] = for
      current <- projection
      context <- drafts.context
    yield BoardTargetSelectionState.restore(context, current.boardTargetActions)
```

`handleBoardSelection`: `case BoardSelectionResult.Updated(state) => boardSelectionState = Some(state); render()` becomes `case BoardSelectionResult.Updated(state) => ui.stage(Draft.BoardTargets(state))`.

The `ui` adapter:

```scala
    lazy val ui: ServerUiView = new ServerUiView:
      def currentBoardSelection = drafts.boardTargets
      def currentBoardSelection_=(value: Option[BoardTargetSelectionState]) =
        drafts = drafts.copy(boardTargets = value)
      def currentWalkerPartition = drafts.partition
      def currentWalkerPartition_=(value: Option[WalkerPartitionDraft]) =
        drafts = drafts.copy(partition = value)
      def currentWalkerDistribution = drafts.distribute
      def currentWalkerDistribution_=(value: Option[WalkerDistributeDraft]) =
        drafts = drafts.copy(distribute = value)
      def currentWalkerSelection = drafts.selection
      def currentWalkerSelection_=(value: Option[WalkerSelectionDraft]) =
        drafts = drafts.copy(selection = value)
      def currentWalkerBoard = drafts.board
      def currentWalkerBoard_=(value: Option[WalkerBoardDraft]) =
        drafts = drafts.copy(board = value)
      def currentModifierWorkflow = drafts.modifiers
      def currentFacedownAdviserDraft = drafts.facedownAdviser
      def stage(draft: Draft) =
        drafts = drafts.staged(draft)
        render()
      def chooseFacedownAdviser(cardId: String) =
        drafts = drafts.copy(facedownAdviser =
          drafts.facedownAdviser.map(_.choose(cardId)))
        render()
      def toggleModifier(value: PreviewModifier) =
        drafts = drafts.copy(modifiers = drafts.modifiers.map(workflow =>
          workflow.copy(selection = workflow.selection.toggle(value))))
        render()
      def moveModifier(value: PreviewModifier, delta: Int) =
        drafts = drafts.copy(modifiers = drafts.modifiers.map(workflow =>
          workflow.copy(selection =
            if delta < 0 then workflow.selection.moveEarlier(value)
            else workflow.selection.moveLater(value))))
        render()
      def confirmModifiers() = confirmModifierSelection()
      def backFromModifiers() =
        drafts = drafts.copy(modifiers = None)
        render()
      def cancelModifiers() =
        drafts = drafts.leave(FlowExit.Cancelled(restoredTargets))
        render()
      def beginTargetedMajorAction(actionKind: String) =
        startTargetedFlow(actionKind)
      // Guarded as before: nothing happens when no flow is in flight.
      def backFromTargets() = drafts.modifiers.foreach { _ =>
        drafts = drafts.leave(FlowExit.TargetsLeft)
        render()
      }
      def cancelTargetAction() =
        drafts = drafts.leave(FlowExit.Cancelled(restoredTargets))
        render()
      def submitTargetCommand(command: GameCommand) =
        completeTargetCommand(command)
      def canControl = controlsAvailable
      def rerender() = render()
      def submitCommand(command: GameCommand) = submit(command)
      def handleSelection(result: BoardSelectionResult) = handleBoardSelection(result)
```

(`cancelModifiers` and `cancelTargetAction` were already the same reset: `ModifierWorkflow.cancel` is `None`.)

- [ ] **Step 4: Run the suite and the gate**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ServerModeUiSuite"`
Expected: the seat-change test now passes, and every other test still does.

Run: `./sbtw "frontend/test"`
Expected: green.

Then confirm `BoardSelectionContext(` is constructed exactly once in the file:

```bash
grep -c 'BoardSelectionContext(' frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala
```

Expected: `1` (the `store` reconcile). `ModifierSelectionContext(` is a different type and may appear twice.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala \
  frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala
git commit -m "refactor(frontend): hold the draft set as one value with named exits

Seven draft vars become one SessionDrafts; the reconcile block is one
line; a seat change, a reload and a stale position reset to empty; the
five ways out of the modifier flow are FlowExit cases. A seat change
no longer shows the previous seat's drafts for one frame.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Delete `ServerUiView`, rename the fake, run the completion greps

Nothing reads the transitional getters any more. The trait goes; `ServerModeUi`'s adapter is typed `ActionControls`; the fake becomes `RecordingControls` with no constructor arguments.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala` (delete the trait)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` (the adapter's type and members)
- Rename: `frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala` → `RecordingControls.scala`
- Modify: every suite naming `RecordingView` (17 files)

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: `class RecordingControls extends ActionControls` with `var staged: Vector[Draft]`, `var submitted: Vector[GameIntent]`, `def drafts: SessionDrafts`, and no-op flow commands; `SelectingView` in `BoardSurfaceSuite` extends it.

- [ ] **Step 1: Delete the trait and narrow the adapter**

Delete the `ServerUiView` trait from `ServerUiSupport.scala` (and its doc comment). In `ServerModeUi.scala`, `lazy val ui: ServerUiView = new ServerUiView:` becomes `lazy val ui: ActionControls = new ActionControls:` and the five getter/setter pairs, `currentModifierWorkflow`, `currentFacedownAdviserDraft`, `canControl` and `rerender` are deleted from it. Replace the two remaining `ui.canControl` reads in `render` with `controlsAvailable`.

- [ ] **Step 2: Rename the fake**

```bash
git mv frontend/src/test/scala/oathdigital/frontend/RecordingServerUiView.scala \
  frontend/src/test/scala/oathdigital/frontend/RecordingControls.scala
```

Its content becomes:

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => Intent, PreviewModifier}

/** An `ActionControls` that records what was staged and submitted instead
  * of touching a session, shared by every panel render suite. `drafts` is
  * the draft set the stages so far leave, from `empty`, so a suite re-renders
  * a panel from exactly what the panel staged.
  */
private[frontend] class RecordingControls extends ActionControls:
  var staged: Vector[Draft] = Vector.empty
  var submitted: Vector[Intent] = Vector.empty

  def drafts: SessionDrafts = staged.foldLeft(SessionDrafts.empty)(_.staged(_))
  def stage(draft: Draft): Unit = staged :+= draft
  def submitCommand(command: Intent): Unit = submitted :+= command
  def handleSelection(result: BoardSelectionResult): Unit = ()

  def chooseFacedownAdviser(cardId: String): Unit = ()
  def toggleModifier(value: PreviewModifier): Unit = ()
  def moveModifier(value: PreviewModifier, delta: Int): Unit = ()
  def confirmModifiers(): Unit = ()
  def backFromModifiers(): Unit = ()
  def cancelModifiers(): Unit = ()
  def beginTargetedMajorAction(actionKind: String): Unit = ()
  def backFromTargets(): Unit = ()
  def cancelTargetAction(): Unit = ()
  def submitTargetCommand(command: Intent): Unit = ()
```

Then across the test tree:

```bash
cd frontend/src/test/scala/oathdigital/frontend
sed -i '' -E 's/new RecordingView\("[^"]*", *"?[a-zA-Z]*"?\)/new RecordingControls()/g; s/\bRecordingView\b/RecordingControls/g' *.scala
```

Then `grep -rn 'RecordingView\|currentPlayerId\|currentGameId' frontend/src/test` must return nothing: `PlayerBoardSuite` stopped building a fake in Task 4, and `NegotiationDealPanelSuite` passes its seat explicitly since Task 3. `BoardSurfaceSuite`'s `SelectingView` now extends `RecordingControls`.

- [ ] **Step 3: Run the completion greps**

```bash
grep -rn 'ServerUiView' frontend/src | wc -l
grep -c 'querySelector' frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala
grep -ln 'rerender' frontend/src/main/scala/oathdigital/frontend/*.scala
grep -c 'BoardSelectionContext(' frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala
```

Expected, in order: `0`; `0`; no files; `1`.

- [ ] **Step 4: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: green.

Run: `python3 scripts/check-architecture.py`
Expected: no errors.

- [ ] **Step 5: Record the outcome and commit**

In `docs/superpowers/specs/2026-09-25-session-drafts-design.md`, change the status line to `> Status: implemented 2026-09-25 (seven commits, this plan). ...` keeping the rest of the paragraph.

```bash
git add -A frontend/src docs/superpowers/specs/2026-09-25-session-drafts-design.md
git commit -m "refactor(frontend): retire ServerUiView

Every renderer now takes the drafts it reads and the narrowest of the
three sinks; the recording fake is an ActionControls with two vectors.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Self-review

**Spec coverage.** `SessionDrafts` with `reconcile`/`staged`/`leave`/`empty` and the `Option` context — Task 1. `ModifierWorkflow.reconcile` on a context — Task 1. Three sinks, no getters — Task 2 (introduced), Task 7 (only sinks remain). `ViewerPresentation.playerId` — Task 2. The renderer table — Tasks 3–5. The choose-amount select — Task 3. `ServerModeUi`: one value, `empty` at three sites, `leave` at five, flow entries as `copy`, one `BoardSelectionContext` construction — Task 6. Decision key from values, dead selectors gone — Task 5. Behavior change 1's test — Task 6; behavior change 2 has no test, as the spec says. The four completion greps — Task 7. `RecordingSession` for `DevelopmentRenderer`: no suite calls `DevelopmentRenderer.controls`, so none is written (YAGNI); the spec's mention is satisfied by the trait existing.

**Deviations from the spec, all named in Global Constraints:** sink method names keep `submitCommand`/`handleSelection`; `Draft.Board` carries an `Option`; `SessionControls` has seven members (the toolbar prints `currentPlayerId`); `ParkedDecision.render` takes `presentation` in addition to the spec's tuple, because two panels need the viewer id.

**Type consistency.** `stage(draft: Draft)` everywhere; `drafts.boardTargets`/`partition`/`distribute`/`selection`/`board`/`modifiers`/`facedownAdviser` match the case class; `FlowExit.Cancelled(restored: Option[BoardTargetSelectionState])` matches `restoredTargets: Option[...]`; `ActionPane(element, prompt)` is read as `.element` and `.prompt` in Task 5 and 6.
