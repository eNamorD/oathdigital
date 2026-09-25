# Modifier Flow Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the modifier flow out of `ServerModeUi.start` into a `ModifierFlow` class that implements `ActionControls` over a `FlowHost` seam, with the flow's steps named as values and the flow itself unit-tested without jsdom.

**Architecture:** `SessionDrafts` gains `step(FlowStep)` next to `leave(FlowExit)`, so every draft-set write the flow makes has a name and a table test. `ModifierWorkflow.fromPreview` replaces two hand-built constructions. `ModifierFlow(host)` holds the seven flow functions unchanged in logic and is the session's `ui`; `ServerModeUi` keeps the draft set, `render`, `submitTransport` and every session reset, and hands the flow a `FlowHost`. One jsdom test proves the wiring.

**Tech Stack:** Scala 3, Scala.js, munit, jsdom (only Task 4). Build through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-09-25-modifier-flow-module-design.md`

## Global Constraints

- Behavior-preserving. No protocol, DTO, gameplay, CSS or DOM-structure change. Preview requests, submitted commands, control strings and the decision key are byte-identical.
- `ActionControls`, `TableControls` and `SessionControls` (`frontend/src/main/scala/oathdigital/frontend/TableControls.scala`) do not change. No renderer and no renderer suite changes.
- `ServerModeUi` keeps `var drafts`, `render`, `submitTransport`, `store`, `accept`, `loadExisting`, `newGame`, `reconnect`, `poll`, `session`, `controlsAvailable` and the bootstrap unchanged.
- `FlowHost` member names are `displayedProjection`, `currentGameId`, `currentPlayerId`, `currentDrafts`, `replaceDrafts`, `redraw`, `fail`, `preview`, `send`. Inside the anonymous implementation in `ServerModeUi.start`, a member named `projection` or `render` would shadow the local it forwards to and recurse.
- `ModifierWorkflow` keeps its name.
- Every `Future` callback in frontend main code uses `scala.scalajs.concurrent.JSExecutionContext.Implicits.queue`.
- Gates: `./sbtw "frontend/test"` after each task; `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` and `python3 scripts/check-architecture.py` (800-line cap) before the final review.
- Commit trailer on every commit: `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Never commit `node_modules` or `.tooling` symlinks.

---

### Task 1: `FlowStep`, `TargetsEntry` and `SessionDrafts.step`

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala` (add `step` after `leave`; add the two enums after `FlowExit`)
- Test: `frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala`

**Interfaces:**
- Consumes: `SessionDrafts`, `FlowExit`, `ModifierWorkflow`, `ModifierSelectionState.toggle/moveEarlier/moveLater`, `FacedownAdviserDraft.choose`, `BoardTargetSelectionState` (all existing).
- Produces:
  - `enum FlowStep { Ordering(workflow: ModifierWorkflow); Targets(workflow: ModifierWorkflow, entry: TargetsEntry); Toggle(value: PreviewModifier); Move(value: PreviewModifier, delta: Int); ChooseFacedown(cardId: String) }`
  - `enum TargetsEntry { Facedown(pick: Option[FacedownAdviserDraft]); Board(targets: BoardTargetSelectionState); NoTargets }`
  - `SessionDrafts.step(value: FlowStep): SessionDrafts`

- [ ] **Step 1: Write the failing tests**

Append to `SessionDraftsSuite` (before the final `empty has no context` test). The suite already defines `context`, `travel`, `minor`, `workflow` (stage `Targets`, no modifiers), `targets`, `full`, and imports `PreviewModifier`.

```scala
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val ordering = workflow.copy(
    preview = workflow.preview.copy(modifiers = Vector(modifier)),
    selection = workflow.selection.copy(candidates = Vector(modifier)),
    stage = ModifierWorkflowStage.Ordering)

  test("Ordering sets only the workflow"):
    val stepped = full.copy(modifiers = None).step(FlowStep.Ordering(ordering))
    assertEquals(stepped, full.copy(modifiers = Some(ordering)))

  test("Targets with a facedown entry sets the pick and clears the board targets"):
    val pick = FacedownAdviserDraft.initial(context, minor)
    val stepped = full.step(FlowStep.Targets(workflow, TargetsEntry.Facedown(pick)))
    assertEquals(stepped.modifiers, Some(workflow))
    assertEquals(stepped.facedownAdviser, pick)
    assertEquals(stepped.boardTargets, None)
    assertEquals(stepped.partition, full.partition)

  test("Targets with a board entry sets the targets and keeps the pick"):
    val activated = targets.copy(selectedKeys = Set("site:site:woods"))
    val stepped = full.step(FlowStep.Targets(workflow, TargetsEntry.Board(activated)))
    assertEquals(stepped.modifiers, Some(workflow))
    assertEquals(stepped.boardTargets, Some(activated))
    assertEquals(stepped.facedownAdviser, full.facedownAdviser)
    assertEquals(stepped.partition, full.partition)

  test("Targets with no entry sets only the workflow"):
    val stepped = full.copy(modifiers = None)
      .step(FlowStep.Targets(workflow, TargetsEntry.NoTargets))
    assertEquals(stepped, full.copy(modifiers = Some(workflow)))

  test("Toggle and Move map the selection and nothing else"):
    val base = full.copy(modifiers = Some(ordering))
    val toggled = base.step(FlowStep.Toggle(modifier))
    assertEquals(toggled.modifiers.map(_.selection.selected), Some(Vector(modifier)))
    assertEquals(toggled.copy(modifiers = None), base.copy(modifiers = None))
    val second = PreviewModifier("adviser:p:denizen:b", "h.b", "B")
    val two = ordering.copy(selection = ordering.selection.copy(
      candidates = Vector(modifier, second), selected = Vector(modifier, second)))
    val moved = full.copy(modifiers = Some(two)).step(FlowStep.Move(second, -1))
    assertEquals(moved.modifiers.map(_.selection.selected), Some(Vector(second, modifier)))
    val back = moved.step(FlowStep.Move(second, 1))
    assertEquals(back.modifiers.map(_.selection.selected), Some(Vector(modifier, second)))
    // No workflow: the step is a no-op.
    assertEquals(full.copy(modifiers = None).step(FlowStep.Toggle(modifier)),
      full.copy(modifiers = None))

  test("ChooseFacedown maps the pick and nothing else"):
    val stepped = full.step(FlowStep.ChooseFacedown("a2"))
    assertEquals(stepped.facedownAdviser.flatMap(_.selectedCardId), Some("a2"))
    assertEquals(stepped.copy(facedownAdviser = None), full.copy(facedownAdviser = None))
    assertEquals(full.copy(facedownAdviser = None).step(FlowStep.ChooseFacedown("a2")),
      full.copy(facedownAdviser = None))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SessionDraftsSuite"`
Expected: compile error, `FlowStep` not found.

- [ ] **Step 3: Implement `step` and the enums**

In `SessionDrafts.scala`, after `leave`:

```scala
  /** The set after one step inside the modifier flow. The table is the
    * spec's; the module computes a `Targets` entry from the projection and
    * the context, and this only applies it.
    */
  def step(value: FlowStep): SessionDrafts = value match
    case FlowStep.Ordering(workflow) => copy(modifiers = Some(workflow))
    case FlowStep.Targets(workflow, TargetsEntry.Facedown(pick)) =>
      copy(modifiers = Some(workflow), facedownAdviser = pick, boardTargets = None)
    case FlowStep.Targets(workflow, TargetsEntry.Board(targets)) =>
      copy(modifiers = Some(workflow), boardTargets = Some(targets))
    case FlowStep.Targets(workflow, TargetsEntry.NoTargets) =>
      copy(modifiers = Some(workflow))
    case FlowStep.Toggle(modifier) =>
      copy(modifiers = modifiers.map(workflow =>
        workflow.copy(selection = workflow.selection.toggle(modifier))))
    case FlowStep.Move(modifier, delta) =>
      copy(modifiers = modifiers.map(workflow => workflow.copy(selection =
        if delta < 0 then workflow.selection.moveEarlier(modifier)
        else workflow.selection.moveLater(modifier))))
    case FlowStep.ChooseFacedown(cardId) =>
      copy(facedownAdviser = facedownAdviser.map(_.choose(cardId)))
```

After the `FlowExit` enum, at the end of the file:

```scala
/** The five ways a viewer moves inside the modifier flow without leaving
  * it. `Ordering` and `Targets` are the two entries; the other three are the
  * steps a panel's controls take. Only `ModifierFlow` applies these.
  */
private[frontend] enum FlowStep:
  case Ordering(workflow: ModifierWorkflow)
  case Targets(workflow: ModifierWorkflow, entry: TargetsEntry)
  case Toggle(value: PreviewModifier)
  case Move(value: PreviewModifier, delta: Int)
  case ChooseFacedown(cardId: String)

/** What entering the Targets stage opens beside the workflow: the facedown
  * pick (and no board targets), one activated board action, or nothing when
  * the preview names no target action the board knows.
  */
private[frontend] enum TargetsEntry:
  case Facedown(pick: Option[FacedownAdviserDraft])
  case Board(targets: BoardTargetSelectionState)
  case NoTargets
```

Add `import oathdigital.protocol.PreviewModifier` at the top of `SessionDrafts.scala` (the file has no protocol import today).

- [ ] **Step 4: Run the suite to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SessionDraftsSuite"`
Expected: all tests pass (10 existing + 6 new).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/SessionDrafts.scala frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala
git commit -m "feat(frontend): name the modifier flow's steps on the draft set

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `ModifierWorkflow.fromPreview`

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala` (companion, after `targeted`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala:255-263` and `:292-299` (the two constructions in `submit` and `startTargetedFlow`)
- Create: `frontend/src/test/scala/oathdigital/frontend/ModifierWorkflowSuite.scala`

**Interfaces:**
- Consumes: `ModifierSelectionState.reconcile(previous, context, candidates, fingerprint)`, `ModifierWorkflowStage`.
- Produces: `ModifierWorkflow.fromPreview(command: Option[GameIntent], actionKind: Option[String], parameters: Map[String, String], response: MajorActionPreviewResponse, previous: Option[ModifierSelectionState], context: ModifierSelectionContext): ModifierWorkflow`

- [ ] **Step 1: Write the failing suite**

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse, PreviewModifier}

/** `fromPreview` is the one constructor both flow entries use: stage by the
  * response, fingerprint by its shape, selection carried through reconcile.
  */
class ModifierWorkflowSuite extends munit.FunSuite:
  private val context = ModifierSelectionContext("game", "red", 9, "recover")
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private def response(modifiers: Vector[PreviewModifier]) =
    MajorActionPreviewResponse(9L, "recover", modifiers, Vector.empty, Vector.empty)

  test("a preview with modifiers starts in Ordering with the documented fingerprint"):
    val workflow = ModifierWorkflow.fromPreview(
      Some(GameIntent.StartWalker("recover", Vector.empty)), None, Map.empty,
      response(Vector(modifier)), None, context)
    assertEquals(workflow.stage, ModifierWorkflowStage.Ordering)
    assertEquals(workflow.command, Some(GameIntent.StartWalker("recover", Vector.empty)))
    assertEquals(workflow.actionKind, None)
    assertEquals(workflow.selection.previewFingerprint, "9:recover:adviser:p:denizen:a/h.a")
    assertEquals(workflow.selection.context, context)
    assertEquals(workflow.selection.candidates, Vector(modifier))
    assertEquals(workflow.selection.selected, Vector.empty)

  test("a preview without modifiers starts in Targets"):
    val workflow = ModifierWorkflow.fromPreview(None, Some("travel"),
      Map("procedure" -> "x"), response(Vector.empty), None, context)
    assertEquals(workflow.stage, ModifierWorkflowStage.Targets)
    assertEquals(workflow.actionKind, Some("travel"))
    assertEquals(workflow.baseParameters, Map("procedure" -> "x"))
    assertEquals(workflow.selection.previewFingerprint, "9:recover:")

  test("the previous selection survives when context, candidates and fingerprint match"):
    val first = ModifierWorkflow.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), None, context)
    val chosen = first.selection.toggle(modifier)
    val again = ModifierWorkflow.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), Some(chosen), context)
    assertEquals(again.selection.selected, Vector(modifier))
    val moved = ModifierWorkflow.fromPreview(None, Some("recover"), Map.empty,
      response(Vector(modifier)), Some(chosen), context.copy(sequence = 10))
    assertEquals(moved.selection.selected, Vector.empty)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierWorkflowSuite"`
Expected: compile error, `fromPreview` is not a member.

- [ ] **Step 3: Implement `fromPreview`**

In the `ModifierWorkflow` companion, after `targeted`:

```scala
  /** The workflow a preview response opens. Ordering when the response
    * offers modifiers, Targets otherwise; the selection is reconciled from
    * `previous` so a re-preview of the same shape keeps the viewer's order.
    */
  def fromPreview(command: Option[GameIntent], actionKind: Option[String],
      parameters: Map[String, String], response: MajorActionPreviewResponse,
      previous: Option[ModifierSelectionState],
      context: ModifierSelectionContext): ModifierWorkflow =
    val fingerprint = s"${response.nextSequence}:${response.action}:" +
      response.modifiers.map(m => s"${m.sourceKey}/${m.handlerId}").mkString("|")
    ModifierWorkflow(command, actionKind, parameters, response,
      ModifierSelectionState.reconcile(previous, context, response.modifiers,
        fingerprint),
      if response.modifiers.nonEmpty then ModifierWorkflowStage.Ordering
      else ModifierWorkflowStage.Targets)
```

- [ ] **Step 4: Run the suite to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierWorkflowSuite"`
Expected: 3 tests pass.

- [ ] **Step 5: Call it from both flow entries in `ServerModeUi`**

In `submit`, replace the `case Right(response) =>` branch body (the `val context`, `val fingerprint` and `drafts = drafts.copy(modifiers = Some(ModifierWorkflow(...)))` lines) with:

```scala
          case Right(response) =>
            drafts = drafts.copy(modifiers = Some(ModifierWorkflow.fromPreview(
              Some(command), None, parameters, response,
              drafts.modifiers.map(_.selection),
              ModifierSelectionContext(gameId, selectedPlayer,
                current.nextSequence, action))))
            render()
```

In `startTargetedFlow`, replace the `case Right(response) =>` branch's `val context`, `val fingerprint` and `val workflow = ModifierWorkflow(...)` lines with:

```scala
        case Right(response) =>
          val workflow = ModifierWorkflow.fromPreview(None, Some(actionKind),
            parameters, response, drafts.modifiers.map(_.selection),
            ModifierSelectionContext(gameId, selectedPlayer,
              current.nextSequence, action))
          if workflow.ordering then { drafts = drafts.copy(modifiers = Some(workflow)); render() }
          else activatePreviewTargets(workflow, response)
```

The `if workflow.ordering ... else ...` lines already exist; keep them. Passing the current selection instead of `None` in `startTargetedFlow` is identical: `leave(FlowExit.Restarted)` ran before the preview and set `modifiers = None`.

- [ ] **Step 6: Run the frontend suite**

Run: `./sbtw "frontend/test"`
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala frontend/src/test/scala/oathdigital/frontend/ModifierWorkflowSuite.scala
git commit -m "refactor(frontend): build the modifier workflow from a preview in one place

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `FlowHost`, `ModifierFlow`, its suite, and the wiring

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/ModifierFlow.scala`
- Create: `frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` (delete `submit`, `activatePreviewTargets`, `startTargetedFlow`, `confirmModifierSelection`, `completeTargetCommand`, `restoredTargets`, `handleBoardSelection` and the anonymous `ui`; add `host` and the one-line `ui`)

**Interfaces:**
- Consumes: `FlowStep`, `TargetsEntry`, `SessionDrafts.step` (Task 1); `ModifierWorkflow.fromPreview` (Task 2); `ActionControls` (unchanged); `ServerUiSupport.commandForSelection(action, targets, playerId)`; `ModifierWorkflow.action/targeted/targetAction/submission`; `BoardTargetSelectionState.reconcile/restore`; `FacedownAdviserDraft.initial`.
- Produces:
  - `trait FlowHost` with `displayedProjection: Option[GameProjection]`, `currentGameId: String`, `currentPlayerId: String`, `currentDrafts: SessionDrafts`, `replaceDrafts(value: SessionDrafts): Unit`, `redraw(): Unit`, `fail(error: GameClientFailure): Unit`, `preview(request: MajorActionPreviewRequest): Future[Either[GameClientFailure, MajorActionPreviewResponse]]`, `send(command: GameCommand, modifiers: Vector[ModifierInvocation]): Unit`
  - `final class ModifierFlow(host: FlowHost) extends ActionControls`

- [ ] **Step 1: Write the failing suite**

The fake host answers previews through a `Promise`, so a test lands the response after the flow has registered its callback. Callbacks on the Scala.js queue run first in, first out, so a future mapped after `answer` sees the flow's effects.

```scala
package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => GameCommand, MajorActionPreviewRequest,
  MajorActionPreviewResponse, ModifierInvocation, PreviewModifier}

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** The modifier flow over a fake host: which preview each entry sends, which
  * step or exit each answer applies, what is sent, and what is redrawn.
  */
class ModifierFlowSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 9)
  private val travel = BoardTargetAction("travel", "Choose a destination", 1, 1,
    autoActivate = true, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  private val advisers = Vector("a1", "a2").map(id => MinorAdviser(
    CardDetails(id, "denizen", s"Old Oak $id", orientation = Some("face-down")),
    Vector.empty))
  private val minor = MinorActionsState(advisers, canPeekSiteRelics = false,
    Vector.empty, None, 0, 0)
  private val projection = GameProjection("game", 9L, "act", Some("red"),
    Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
    Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
    boardTargetActions = Vector(travel), minorActions = Some(minor))
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val invocation = ModifierInvocation("adviser", "a", None, "h.a")
  private val recover = GameCommand.StartWalker("recover", Vector.empty)
  private def response(action: String, modifiers: Vector[PreviewModifier]) =
    MajorActionPreviewResponse(9L, action, modifiers, Vector.empty, Vector.empty)
  private def bound: SessionDrafts = SessionDrafts.empty.reconcile(context, projection)
  /** A workflow the flow would have opened for `action`, with `modifier`
    * offered and (when `chosen`) selected.
    */
  private def workflow(action: String, command: Option[GameCommand],
      actionKind: Option[String], stage: ModifierWorkflowStage,
      chosen: Boolean = true): ModifierWorkflow =
    val fresh = ModifierWorkflow.fromPreview(command, actionKind, Map.empty,
      response(action, Vector(modifier)), None,
      ModifierSelectionContext("game", "red", 9, action))
    val selected = if chosen then fresh.selection.toggle(modifier) else fresh.selection
    fresh.copy(selection = selected, stage = stage)

  private final class Host(var currentDrafts: SessionDrafts) extends FlowHost:
    var displayedProjection: Option[GameProjection] = Some(projection)
    val currentGameId = "game"
    val currentPlayerId = "red"
    var redraws = 0
    var failures = Vector.empty[GameClientFailure]
    var sent = Vector.empty[(GameCommand, Vector[ModifierInvocation])]
    var previews = Vector.empty[MajorActionPreviewRequest]
    private var pending = Vector.empty[Promise[Either[GameClientFailure, MajorActionPreviewResponse]]]
    def replaceDrafts(value: SessionDrafts) = currentDrafts = value
    def redraw() = redraws += 1
    def fail(error: GameClientFailure) = { failures :+= error; redraws += 1 }
    def preview(request: MajorActionPreviewRequest) =
      previews :+= request
      val promise = Promise[Either[GameClientFailure, MajorActionPreviewResponse]]()
      pending :+= promise
      promise.future
    def send(command: GameCommand, modifiers: Vector[ModifierInvocation]) =
      sent :+= (command -> modifiers)
    /** Lands the oldest unanswered preview; the returned future completes
      * after the flow's own callback has run.
      */
    def answer(result: Either[GameClientFailure, MajorActionPreviewResponse]): Future[Unit] =
      val promise = pending.head
      pending = pending.tail
      promise.success(result)
      promise.future.map(_ => ())

  private def flow(drafts: SessionDrafts = bound): (Host, ModifierFlow) =
    val host = new Host(drafts)
    host -> new ModifierFlow(host)

  test("a command with no preview action is sent as it is"):
    val (host, ui) = flow()
    ui.submitCommand(GameCommand.BeginRest)
    assertEquals(host.sent, Vector(GameCommand.BeginRest -> Vector.empty))
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.redraws, 0)

  test("a major action whose preview offers no modifiers is sent directly"):
    val (host, ui) = flow()
    ui.submitCommand(recover)
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "recover", Map.empty)))
    host.answer(Right(response("recover", Vector.empty))).map { _ =>
      assertEquals(host.sent, Vector(recover -> Vector.empty))
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.redraws, 0)
    }

  test("a major action whose preview offers modifiers enters Ordering and carries the previous selection"):
    val previous = workflow("recover", Some(recover), None, ModifierWorkflowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(previous)))
    ui.submitCommand(recover)
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      val opened = host.currentDrafts.modifiers.get
      assertEquals(opened.stage, ModifierWorkflowStage.Ordering)
      assertEquals(opened.command, Some(recover))
      assertEquals(opened.actionKind, None)
      assertEquals(opened.selection.selected, Vector(modifier))
      assertEquals(host.redraws, 1)
      assertEquals(host.sent, Vector.empty)
    }

  test("a preview failure on submit fails without touching the drafts"):
    val (host, ui) = flow()
    val before = host.currentDrafts
    ui.submitCommand(recover)
    val error = GameClientFailure.NetworkFailure("down")
    host.answer(Left(error)).map { _ =>
      assertEquals(host.failures, Vector(error))
      assertEquals(host.currentDrafts, before)
      assertEquals(host.sent, Vector.empty)
    }

  test("a targeted travel restarts the flow, then enters Ordering when modifiers are offered"):
    val stale = workflow("recover", Some(recover), None, ModifierWorkflowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(stale),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    ui.beginTargetedMajorAction("travel")
    // Restarted, before the preview lands.
    assertEquals(host.currentDrafts.modifiers, None)
    assertEquals(host.currentDrafts.facedownAdviser, None)
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "travel", Map.empty)))
    host.answer(Right(response("travel", Vector(modifier)))).map { _ =>
      val opened = host.currentDrafts.modifiers.get
      assertEquals(opened.stage, ModifierWorkflowStage.Ordering)
      assertEquals(opened.command, None)
      assertEquals(opened.actionKind, Some("travel"))
      assertEquals(host.redraws, 1)
    }

  test("a targeted travel with no modifiers enters Targets on the board"):
    val (host, ui) = flow()
    ui.beginTargetedMajorAction("travel")
    host.answer(Right(response("travel", Vector.empty))).map { _ =>
      assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierWorkflowStage.Targets))
      assertEquals(host.currentDrafts.boardTargets.flatMap(_.activeActionKind), Some("travel"))
      assertEquals(host.currentDrafts.boardTargets.map(_.actions.map(_.actionKind)),
        Some(Vector("travel")))
      assertEquals(host.redraws, 1)
    }

  test("a targeted facedown adviser enters Targets with the pick and no board targets"):
    val (host, ui) = flow()
    ui.beginTargetedMajorAction("play-facedown-adviser")
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "search",
      Map("procedure" -> "facedown-adviser"))))
    host.answer(Right(response("search", Vector.empty))).map { _ =>
      assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierWorkflowStage.Targets))
      assertEquals(host.currentDrafts.facedownAdviser.map(_.advisers.map(_.card.cardId)),
        Some(Vector("a1", "a2")))
      assertEquals(host.currentDrafts.boardTargets, None)
      assertEquals(host.redraws, 1)
    }

  test("confirming with a command leaves Ordering and sends the folded submission without a redraw"):
    val ordering = workflow("recover", Some(recover), None, ModifierWorkflowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering)))
    ui.confirmModifiers()
    assertEquals(host.previews, Vector(MajorActionPreviewRequest(9L, "recover",
      Map.empty, Vector(invocation))))
    host.answer(Right(response("recover", Vector(modifier)))).map { _ =>
      assertEquals(host.sent, Vector(
        GameCommand.StartWalker("recover", Vector("h.a")) -> Vector.empty))
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.redraws, 0)
    }

  test("confirming without a command enters Targets"):
    val ordering = workflow("travel", None, Some("travel"), ModifierWorkflowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering)))
    ui.confirmModifiers()
    host.answer(Right(response("travel", Vector(modifier)))).map { _ =>
      assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierWorkflowStage.Targets))
      assertEquals(host.currentDrafts.modifiers.map(_.selection.selected), Some(Vector(modifier)))
      assertEquals(host.currentDrafts.boardTargets.flatMap(_.activeActionKind), Some("travel"))
      assertEquals(host.sent, Vector.empty)
      assertEquals(host.redraws, 1)
    }

  test("a failed confirm preview leaves Failed and fails"):
    val ordering = workflow("recover", Some(recover), None, ModifierWorkflowStage.Ordering)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    ui.confirmModifiers()
    val error = GameClientFailure.NetworkFailure("down")
    host.answer(Left(error)).map { _ =>
      assertEquals(host.currentDrafts.modifiers, None)
      assertEquals(host.currentDrafts.facedownAdviser, None)
      assertEquals(host.currentDrafts.boardTargets, bound.boardTargets)
      assertEquals(host.failures, Vector(error))
    }

  test("confirming with nothing in flight does nothing"):
    val (host, ui) = flow()
    ui.confirmModifiers()
    assertEquals(host.previews, Vector.empty)
    assertEquals(host.redraws, 0)

  test("a target command in Targets leaves Completed and sends the folded submission"):
    val targets = workflow("travel", None, Some("travel"), ModifierWorkflowStage.Targets)
    val (host, ui) = flow(bound.copy(modifiers = Some(targets)))
    val command = GameCommand.StartWalker("travel", Vector.empty)
    ui.submitTargetCommand(command)
    assertEquals(host.sent, Vector(
      GameCommand.StartWalker("travel", Vector("h.a")) -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)
    assertEquals(host.currentDrafts.boardTargets, None)
    assertEquals(host.previews, Vector.empty)

  test("a target command outside Targets behaves as a plain submit"):
    val (host, ui) = flow()
    ui.submitTargetCommand(GameCommand.BeginRest)
    assertEquals(host.sent, Vector(GameCommand.BeginRest -> Vector.empty))
    assertEquals(host.previews, Vector.empty)

  test("cancelling from either stage leaves Cancelled with the restored board targets"):
    val restored = BoardTargetSelectionState.restore(context, Vector(travel))
    val targets = workflow("travel", None, Some("travel"), ModifierWorkflowStage.Targets)
    val start = bound.copy(modifiers = Some(targets),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor),
      boardTargets = bound.boardTargets.map(_.copy(selectedKeys = Set("site:site:woods"))))
    val (first, ui) = flow(start)
    ui.cancelModifiers()
    assertEquals(first.currentDrafts.modifiers, None)
    assertEquals(first.currentDrafts.facedownAdviser, None)
    assertEquals(first.currentDrafts.boardTargets, Some(restored))
    assertEquals(first.redraws, 1)
    val (second, again) = flow(start)
    again.cancelTargetAction()
    assertEquals(second.currentDrafts, first.currentDrafts)
    assertEquals(second.redraws, 1)

  test("backing out of Ordering leaves only the workflow"):
    val ordering = workflow("recover", Some(recover), None, ModifierWorkflowStage.Ordering)
    val pick = FacedownAdviserDraft.initial(context, minor)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering), facedownAdviser = pick))
    ui.backFromModifiers()
    assertEquals(host.currentDrafts.modifiers, None)
    assertEquals(host.currentDrafts.facedownAdviser, pick)
    assertEquals(host.redraws, 1)

  test("backing out of Targets returns to Ordering, and is a no-op with no flow"):
    val targets = workflow("travel", None, Some("travel"), ModifierWorkflowStage.Targets)
    val (host, ui) = flow(bound.copy(modifiers = Some(targets),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    ui.backFromTargets()
    assertEquals(host.currentDrafts.modifiers.map(_.stage), Some(ModifierWorkflowStage.Ordering))
    assertEquals(host.currentDrafts.facedownAdviser, None)
    assertEquals(host.currentDrafts.boardTargets, None)
    assertEquals(host.redraws, 1)
    val (idle, none) = flow()
    none.backFromTargets()
    assertEquals(idle.currentDrafts, bound)
    assertEquals(idle.redraws, 0)

  test("toggle, move and choose apply their step and redraw"):
    val ordering = workflow("recover", Some(recover), None,
      ModifierWorkflowStage.Ordering, chosen = false)
    val (host, ui) = flow(bound.copy(modifiers = Some(ordering),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)))
    ui.toggleModifier(modifier)
    assertEquals(host.currentDrafts.modifiers.map(_.selection.selected), Some(Vector(modifier)))
    ui.moveModifier(modifier, 1)
    assertEquals(host.currentDrafts.modifiers.map(_.selection.selected), Some(Vector(modifier)))
    ui.chooseFacedownAdviser("a2")
    assertEquals(host.currentDrafts.facedownAdviser.flatMap(_.selectedCardId), Some("a2"))
    assertEquals(host.redraws, 3)

  test("stage replaces one slot and redraws"):
    val (host, ui) = flow()
    val activated = host.currentDrafts.boardTargets.get.copy(selectedKeys = Set("site:site:woods"))
    ui.stage(Draft.BoardTargets(activated))
    assertEquals(host.currentDrafts.boardTargets, Some(activated))
    assertEquals(host.redraws, 1)

  test("a board selection update stages the targets and a submit completes the command"):
    val targets = workflow("travel", None, Some("travel"), ModifierWorkflowStage.Targets)
    val (host, ui) = flow(bound.copy(modifiers = Some(targets)))
    val activated = host.currentDrafts.boardTargets.get.copy(selectedKeys = Set("site:site:woods"))
    ui.handleSelection(BoardSelectionResult.Updated(activated))
    assertEquals(host.currentDrafts.boardTargets, Some(activated))
    assertEquals(host.redraws, 1)
    ui.handleSelection(BoardSelectionResult.Submit(travel,
      Vector(BoardTargetRef.Site("site:woods"))))
    assertEquals(host.sent, Vector(GameCommand.StartWalker("travel", Vector("h.a"),
      Vector(oathdigital.protocol.WalkerStartArgWire("site", "site:woods"))) -> Vector.empty))
    assertEquals(host.currentDrafts.modifiers, None)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierFlowSuite"`
Expected: compile error, `FlowHost` not found.

- [ ] **Step 3: Write `ModifierFlow.scala`**

```scala
package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, _}
import ServerUiSupport.commandForSelection

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** What the session gives the modifier flow: the projection and identity it
  * reads, the draft set it steps, and the four effects it causes. `preview`
  * and `send` add the game and seat themselves. Member names follow
  * `SessionControls` rather than the session's own locals, because the
  * anonymous implementation in `ServerModeUi.start` would otherwise shadow
  * the local it forwards to.
  */
private[frontend] trait FlowHost:
  def displayedProjection: Option[GameProjection]
  def currentGameId: String
  def currentPlayerId: String
  def currentDrafts: SessionDrafts
  /** Writes without a redraw; the flow redraws where it did before. */
  def replaceDrafts(value: SessionDrafts): Unit
  def redraw(): Unit
  /** Records the failure and redraws. */
  def fail(error: GameClientFailure): Unit
  def preview(request: MajorActionPreviewRequest)
      : Future[Either[GameClientFailure, MajorActionPreviewResponse]]
  /** `submitTransport`: sends under the session's identity and position. */
  def send(command: GameCommand, modifiers: Vector[ModifierInvocation]): Unit

/** The modifier flow (CONTEXT.md): previewing a major action, ordering the
  * modifiers it offers, and picking its targets. Every `ActionControls`
  * member ends here; the flow reads the draft set from its host, applies a
  * `FlowStep` or a `FlowExit`, and redraws or sends.
  */
private[frontend] final class ModifierFlow(host: FlowHost) extends ActionControls:
  private def drafts: SessionDrafts = host.currentDrafts

  private def selectionContext(sequence: Long, action: String) =
    ModifierSelectionContext(host.currentGameId, host.currentPlayerId, sequence, action)

  private def step(value: FlowStep): Unit =
    host.replaceDrafts(drafts.step(value))
    host.redraw()

  private def exit(value: FlowExit): Unit =
    host.replaceDrafts(drafts.leave(value))
    host.redraw()

  private def submit(command: GameCommand): Unit = ModifierWorkflow.action(command) match
    case None => host.send(command, Vector.empty)
    case Some((action, parameters)) => host.displayedProjection.foreach { current =>
      val request = MajorActionPreviewRequest(current.nextSequence, action, parameters)
      host.preview(request).foreach:
        case Right(response) if response.modifiers.isEmpty =>
          host.send(command, Vector.empty)
        case Right(response) =>
          step(FlowStep.Ordering(ModifierWorkflow.fromPreview(Some(command), None,
            parameters, response, drafts.modifiers.map(_.selection),
            selectionContext(current.nextSequence, action))))
        case Left(error) => host.fail(error)
    }

  private def enterTargets(workflow: ModifierWorkflow,
      response: MajorActionPreviewResponse): Unit = for
    current <- host.displayedProjection
    context <- drafts.context
    actionKind <- workflow.actionKind
  do
    val entry: TargetsEntry = if actionKind == "play-facedown-adviser" then
      TargetsEntry.Facedown(current.minorActions.flatMap(
        FacedownAdviserDraft.initial(context, _)))
    else ModifierWorkflow.targetAction(actionKind, response,
      current.boardTargetActions).fold[TargetsEntry](TargetsEntry.NoTargets)(action =>
      TargetsEntry.Board(BoardTargetSelectionState.reconcile(None, context,
        Vector(action)).activate(actionKind)))
    step(FlowStep.Targets(workflow.showTargets(response), entry))

  private def startTargetedFlow(actionKind: String): Unit = for
    current <- host.displayedProjection
    (action, parameters) <- ModifierWorkflow.targeted(actionKind)
  do
    host.replaceDrafts(drafts.leave(FlowExit.Restarted))
    val request = MajorActionPreviewRequest(current.nextSequence, action, parameters)
    host.preview(request).foreach:
      case Right(response) =>
        val workflow = ModifierWorkflow.fromPreview(None, Some(actionKind),
          parameters, response, drafts.modifiers.map(_.selection),
          selectionContext(current.nextSequence, action))
        if workflow.ordering then step(FlowStep.Ordering(workflow))
        else enterTargets(workflow, response)
      case Left(error) => host.fail(error)

  private def confirmModifierSelection(): Unit = drafts.modifiers.foreach { workflow =>
    val request = MajorActionPreviewRequest(workflow.selection.context.sequence,
      workflow.selection.context.action, workflow.baseParameters,
      workflow.selection.invocations)
    host.preview(request).foreach:
      case Right(response) => workflow.command match
        case Some(command) =>
          host.replaceDrafts(drafts.leave(FlowExit.OrderingLeft))
          val (submitted, modifiers) = ModifierWorkflow.submission(command,
            workflow.selection.invocations)
          host.send(submitted, modifiers)
        case None => enterTargets(workflow, response)
      case Left(error) =>
        host.replaceDrafts(drafts.leave(FlowExit.Failed))
        host.fail(error)
  }

  private def completeTargetCommand(command: GameCommand): Unit =
    drafts.modifiers.filter(_.stage == ModifierWorkflowStage.Targets) match
      case Some(workflow) =>
        host.replaceDrafts(drafts.leave(FlowExit.Completed))
        val (submitted, modifiers) = ModifierWorkflow.submission(command,
          workflow.selection.invocations)
        host.send(submitted, modifiers)
      case None => submit(command)

  private def restoredTargets: Option[BoardTargetSelectionState] = for
    current <- host.displayedProjection
    context <- drafts.context
  yield BoardTargetSelectionState.restore(context, current.boardTargetActions)

  def stage(draft: Draft): Unit =
    host.replaceDrafts(drafts.staged(draft))
    host.redraw()
  def submitCommand(command: GameCommand): Unit = submit(command)
  def handleSelection(result: BoardSelectionResult): Unit = result match
    case BoardSelectionResult.Updated(state) => stage(Draft.BoardTargets(state))
    case BoardSelectionResult.Submit(action, targets) =>
      commandForSelection(action, targets, host.currentPlayerId)
        .foreach(completeTargetCommand)

  def chooseFacedownAdviser(cardId: String): Unit = step(FlowStep.ChooseFacedown(cardId))
  def toggleModifier(value: PreviewModifier): Unit = step(FlowStep.Toggle(value))
  def moveModifier(value: PreviewModifier, delta: Int): Unit =
    step(FlowStep.Move(value, delta))
  def confirmModifiers(): Unit = confirmModifierSelection()
  def backFromModifiers(): Unit = exit(FlowExit.OrderingLeft)
  def cancelModifiers(): Unit = exit(FlowExit.Cancelled(restoredTargets))
  def beginTargetedMajorAction(actionKind: String): Unit = startTargetedFlow(actionKind)
  // Guarded as before: nothing happens when no flow is in flight.
  def backFromTargets(): Unit = drafts.modifiers.foreach(_ => exit(FlowExit.TargetsLeft))
  def cancelTargetAction(): Unit = exit(FlowExit.Cancelled(restoredTargets))
  def submitTargetCommand(command: GameCommand): Unit = completeTargetCommand(command)
```

- [ ] **Step 4: Run the suite to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ModifierFlowSuite"`
Expected: 19 tests pass. If `fold[TargetsEntry]` or the tuple pattern in the `for` fails to compile, fix the type ascription; do not change the logic.

- [ ] **Step 5: Wire `ServerModeUi` to the module**

In `ServerModeUi.scala`, delete `submit`, `activatePreviewTargets`, `startTargetedFlow`, `confirmModifierSelection`, `completeTargetCommand`, `restoredTargets`, `handleBoardSelection` and the whole `lazy val ui: ActionControls = new ActionControls: ... ` block. Keep `submitTransport` and `lazy val session`. Where `ui` was, add:

```scala
    lazy val host: FlowHost = new FlowHost:
      def displayedProjection = projection
      def currentGameId = gameId
      def currentPlayerId = selectedPlayer
      def currentDrafts = drafts
      def replaceDrafts(value: SessionDrafts) = drafts = value
      def redraw() = render()
      def fail(error: GameClientFailure) = { failure = Some(error); render() }
      def preview(request: MajorActionPreviewRequest) =
        client.preview(gameId, selectedPlayer, request)
      def send(command: GameCommand, modifiers: Vector[ModifierInvocation]) =
        submitTransport(command, modifiers)

    lazy val ui: ActionControls = new ModifierFlow(host)
```

`render` and `submitTransport` are defined above this point in the closure, and `ui` is only called from `render`, which runs after `start` finishes building. The `import ServerUiSupport._` stays (`element`, `text`, `button`, `queryParameter`, `viewerPresentation`, `playerDisplayName` still use it).

- [ ] **Step 6: Run the frontend suite and the greps**

Run: `./sbtw "frontend/test"`
Expected: all green, including every `ServerModeUiSuite` test.

Run these, expecting the counts shown:

```bash
grep -c "client.preview" frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala
```
Expected: `1`.

```bash
grep -rln "copy(modifiers\|copy(facedownAdviser" frontend/src/main/scala/oathdigital/frontend/
```
Expected: only `SessionDrafts.scala`.

```bash
grep -rn "ModifierSelectionContext(" frontend/src/main/scala/oathdigital/frontend/ | grep -v "case class"
```
Expected: one line, in `ModifierFlow.scala`.

```bash
wc -l frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala frontend/src/main/scala/oathdigital/frontend/ModifierFlow.scala
```
Expected: `ServerModeUi.scala` about 270, `ModifierFlow.scala` under 160.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ModifierFlow.scala frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala frontend/src/test/scala/oathdigital/frontend/ModifierFlowSuite.scala
git commit -m "refactor(frontend): move the modifier flow behind ActionControls as ModifierFlow

The seven flow functions and the anonymous ActionControls leave
ServerModeUi.start unchanged in logic and become ModifierFlow over a
FlowHost seam. The session keeps the draft set, render, submitTransport and
every reset; the flow gets a pure suite of its own.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: The jsdom wiring test, the glossary sentence, the spec status

**Files:**
- Modify: `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala` (add one test after "a seat change clears the walker drafts before the interim render")
- Modify: `CONTEXT.md` ("Modifier flow" entry, currently lines 76-82)
- Modify: `docs/superpowers/specs/2026-09-25-modifier-flow-module-design.md:3` (status line)

**Interfaces:**
- Consumes: `ModifierFlow` wired as `ui` (Task 3); `TestBrowser` (`click`, `byClass`, `settle`, `text`, `close`); `GameProjectionCodec.encode`; the dev `HttpGameClient` URLs `/api/dev/first-games/{id}?playerId=`, `.../preview?playerId=`, `.../commands?playerId=`, `.../events?limit=25`.
- Produces: nothing later tasks use.

- [ ] **Step 1: Write the failing jsdom test**

Add to `ServerModeUiSuite` after the seat-change test. The suite already imports `PlayerColor`, `Future` and the queue execution context.

```scala
  /** Spec, verification: the only test that proves the FlowHost wiring. A
    * Recover whose preview offers one modifier renders the ordering panel;
    * toggling and confirming previews again with the invocation, then posts
    * the StartWalker with the handler folded into its own modifiers.
    */
  test("a previewed major action orders its modifier and posts the ordered command"):
    val browser = new TestBrowser("?gameId=g&playerId=red")
    val projection = oathdigital.protocol.projection.GameProjectionCodec.encode(
      GameProjection("g", 1L, "act-action-selection", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
        Vector.empty, Vector.empty, Vector("beginRecover"), ready = true,
        completed = false, actionSelectionOpen = true))
    val preview = """{"nextSequence":1,"action":"recover","modifiers":[{"sourceKey":"adviser:p:denizen:a","handlerId":"h.a","description":"Old Oak"}],"ignoredRules":[],"targets":[]}"""
    val requests = scala.collection.mutable.ArrayBuffer.empty[(String, String, Option[String])]
    val transport = new JsonTransport:
      def request(method: String, url: String, body: Option[String]): Future[Either[GameClientFailure, TransportResponse]] =
        requests += ((method, url, body))
        val json = if url.contains("/events") then """{"events":[]}"""
          else if url.contains("/preview") then preview
          else projection
        Future.successful(Right(TransportResponse(200, json)))
    Main.start(browser.mount, "/", trustedAlpha = false, transport)
    browser.settle.flatMap { _ =>
      browser.click("recover-action")
      browser.settle
    }.flatMap { _ =>
      assert(browser.byClass("modifier-confirm").nonEmpty, browser.text)
      browser.click("modifier-toggle")
      browser.settle
    }.flatMap { _ =>
      assert(browser.byClass("modifier-ordinal").nonEmpty, browser.text)
      browser.click("modifier-confirm")
      browser.settle
    }.map { _ =>
      val posts = requests.filter(_._1 == "POST").toVector
      assertEquals(posts.map(_._2), Vector(
        "/api/dev/first-games/g/preview?playerId=red",
        "/api/dev/first-games/g/preview?playerId=red",
        "/api/dev/first-games/g/commands?playerId=red"))
      assert(posts(1)._3.exists(_.contains("\"handlerId\":\"h.a\"")), posts(1)._3)
      val command = posts(2)._3.get
      assert(command.contains("\"action\":\"recover\""), command)
      assert(command.contains("\"modifiers\":[\"h.a\"]"), command)
      assert(!command.contains("orderedModifiers"), command)
      assert(browser.byClass("modifier-confirm").isEmpty, browser.text)
    }.andThen { case _ => browser.close() }
```

- [ ] **Step 2: Run the test to verify it passes against the wired module**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.ServerModeUiSuite"`
Expected: PASS. This test is written after the move, so it passes first time; its value is that it fails if `host.preview`, `host.send` or `host.redraw` is miswired. To see it fail, temporarily make `redraw()` in the host a no-op: the `modifier-confirm` assertion fails. Revert before committing.

- [ ] **Step 3: Add the glossary sentence**

In `CONTEXT.md`, the "Modifier flow" entry reads:

```
**Modifier flow**:
The steps a viewer walks between choosing a major action and sending it:
previewing the action, ordering the modifiers it offers, and picking its
targets. A viewer is in at most one at a time, and leaves it by sending,
cancelling, backing out, or a failed preview.
_Avoid_: preview flow, targeting flow, modifier stage (a stage is one step of
the flow)
```

Insert one sentence before `_Avoid_`, so the body becomes:

```
The steps a viewer walks between choosing a major action and sending it:
previewing the action, ordering the modifiers it offers, and picking its
targets. A viewer is in at most one at a time, and leaves it by sending,
cancelling, backing out, or a failed preview. A flow step keeps the viewer
in the flow; a flow exit takes them out of it.
```

If another session has already edited this entry, keep their wording and add the sentence.

- [ ] **Step 4: Update the spec status line**

Change line 3 of `docs/superpowers/specs/2026-09-25-modifier-flow-module-design.md` from
`> Status: designed 2026-09-25, not yet planned.` to
`> Status: implemented 2026-09-25 (four commits, this plan).` Keep the rest of the paragraph.

- [ ] **Step 5: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"` then `python3 scripts/check-architecture.py`
Expected: all green; architecture check passes.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala CONTEXT.md docs/superpowers/specs/2026-09-25-modifier-flow-module-design.md
git commit -m "test(frontend): drive the modifier flow through the preview endpoint, and record the slice

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```
