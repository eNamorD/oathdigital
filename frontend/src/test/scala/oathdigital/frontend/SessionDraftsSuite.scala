package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.PreviewModifier
import oathdigital.testkit.Unwrap.present

/** The draft set as a value: what `reconcile` keeps and drops, what `staged`
  * touches, and the exact slots each `FlowExit` clears (spec, "leave").
  */
class SessionDraftsSuite extends munit.FunSuite:
  private val context = ForgeFixture.context
  private val travel = BoardTargetAction("travel", "Choose a destination", 1, 1,
    autoActivate = true, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  private val advisers = Vector("a1", "a2").map(id => MinorAdviser(
    CardDetails(id, "denizen", s"Old Oak $id", orientation = Some("face-down"))))
  private val minor = MinorActionsState(advisers, canPeekSiteRelics = false,
    Vector.empty, None, 0, 0)
  private def projection(
      parked: Option[WalkerDecisionState] = Some(ForgeFixture.parked))
      : GameProjection =
    TestProjections.actingProjection(nextSequence = 9L, players = Vector(
      GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
      .copy(boardTargetActions = Vector(travel), walkerDecision = parked,
        minorActions = Some(minor))
  private val travelTargetsDraft = ModifierFlowDrafts.travelTargetsDraft(9L)
  private val targets = BoardTargetSelectionState(context, Vector(travel),
    Some("travel"), Set.empty)

  /** Every slot filled, all bound to `context`. The facedown pick is opened
    * by the modifier flow (`FacedownAdviserDraft.initial`), never by
    * `reconcile`, so it is built here the same way.
    */
  private def full: SessionDrafts =
    SessionDrafts.empty.reconcile(context, projection()).copy(
      boardTargets = Some(targets), modifiers = Some(travelTargetsDraft),
      facedownAdviser = FacedownAdviserDraft.initial(context, minor)
        .map(_.choose("a1")))

  test("reconcile fills every slot the projection asks for and sets the context"):
    val drafts = SessionDrafts.empty.reconcile(context, projection())
    assertEquals(drafts.context, Some(context))
    assert(drafts.boardTargets.exists(_.actions == Vector(travel)))
    assert(drafts.partition.exists(_.decisionId == "forge-9"))
    assertEquals(drafts.distribute, None)
    assertEquals(drafts.selection, None)
    assertEquals(drafts.board, None)
    assertEquals(drafts.modifiers, None)
    // Only the modifier flow opens the facedown pick; reconcile carries it.
    assertEquals(drafts.facedownAdviser, None)

  test("reconcile keeps every slot under the same context and drops them under another"):
    val kept = full.reconcile(context, projection())
    assertEquals(kept.modifiers, Some(travelTargetsDraft))
    assertEquals(kept.facedownAdviser.flatMap(_.selectedCardId), Some("a1"))
    assertEquals(kept.boardTargets, Some(targets))
    val moved = full.reconcile(context.copy(sequence = 10), projection())
    assertEquals(moved.modifiers, None)
    assertEquals(moved.facedownAdviser.flatMap(_.selectedCardId), None)
    assertEquals(moved.boardTargets.flatMap(_.activeActionKind), Some("travel"))
    assertEquals(moved.partition.map(_.context), Some(context.copy(sequence = 10)))

  test("staged replaces exactly its own slot"):
    val base = full
    val partition = present(base.partition, "a partition draft")
      .move("denizen:denizen:3", "pay-favor")
    assertEquals(base.staged(Draft.Partition(partition)),
      base.copy(partition = Some(partition)))
    val board = BoardSelectionDraft(context, "forge-9", ForgeFixture.query,
      Vector("site:site:a"))
    assertEquals(base.staged(Draft.Board(Some(board))), base.copy(board = Some(board)))
    assertEquals(base.staged(Draft.Board(Some(board))).staged(Draft.Board(None)),
      base.copy(board = None))
    val activated = targets.copy(selectedKeys =
      Set(BoardTargetRef.Site("site:woods").stableKey))
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

  test("TargetsLeft steps the flow draft back and clears the pick and the targets"):
    val ordering = travelTargetsDraft.copy(
      preview = travelTargetsDraft.preview.copy(modifiers =
        Vector(PreviewModifier("adviser:p:denizen:a", "h.a", "A"))))
    val left = full.copy(modifiers = Some(ordering)).leave(FlowExit.TargetsLeft)
    assertEquals(left.modifiers.map(_.stage), Some(ModifierFlowStage.Ordering))
    assertEquals(left.facedownAdviser, None)
    assertEquals(left.boardTargets, None)
    // A flow draft that never had a modifier stage has nowhere to go back to.
    assertEquals(full.leave(FlowExit.TargetsLeft).modifiers, None)

  test("OrderingLeft clears only the flow draft"):
    val left = full.leave(FlowExit.OrderingLeft)
    assertEquals(left, full.copy(modifiers = None))
    assert(left.facedownAdviser.nonEmpty)
    assert(left.boardTargets.nonEmpty)
    assert(left.partition.nonEmpty)

  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val ordering = travelTargetsDraft.copy(
    preview = travelTargetsDraft.preview.copy(modifiers = Vector(modifier)),
    selection = travelTargetsDraft.selection
      .copy(candidates = Vector(modifier)),
    stage = ModifierFlowStage.Ordering)

  test("Ordering sets only the flow draft"):
    val stepped = full.copy(modifiers = None).step(FlowStep.Ordering(ordering))
    assertEquals(stepped, full.copy(modifiers = Some(ordering)))

  test("Targets with a facedown entry sets the pick and clears the board targets"):
    val pick = FacedownAdviserDraft.initial(context, minor)
    val stepped = full.step(
      FlowStep.Targets(travelTargetsDraft, TargetsEntry.Facedown(pick)))
    assertEquals(stepped.modifiers, Some(travelTargetsDraft))
    assertEquals(stepped.facedownAdviser, pick)
    assertEquals(stepped.boardTargets, None)
    assertEquals(stepped.partition, full.partition)

  test("Targets with a board entry sets the targets and keeps the pick"):
    val activated = targets.copy(selectedKeys =
      Set(BoardTargetRef.Site("site:woods").stableKey))
    val stepped = full.step(
      FlowStep.Targets(travelTargetsDraft, TargetsEntry.Board(activated)))
    assertEquals(stepped.modifiers, Some(travelTargetsDraft))
    assertEquals(stepped.boardTargets, Some(activated))
    assertEquals(stepped.facedownAdviser, full.facedownAdviser)
    assertEquals(stepped.partition, full.partition)

  test("Targets with no entry sets only the flow draft"):
    val stepped = full.copy(modifiers = None)
      .step(FlowStep.Targets(travelTargetsDraft, TargetsEntry.NoTargets))
    assertEquals(stepped, full.copy(modifiers = Some(travelTargetsDraft)))

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
    // No flow draft: the step is a no-op.
    assertEquals(full.copy(modifiers = None).step(FlowStep.Toggle(modifier)),
      full.copy(modifiers = None))

  test("ChooseFacedown maps the pick and nothing else"):
    val stepped = full.step(FlowStep.ChooseFacedown("a2"))
    assertEquals(stepped.facedownAdviser.flatMap(_.selectedCardId), Some("a2"))
    assertEquals(stepped.copy(facedownAdviser = None), full.copy(facedownAdviser = None))
    assertEquals(full.copy(facedownAdviser = None).step(FlowStep.ChooseFacedown("a2")),
      full.copy(facedownAdviser = None))
