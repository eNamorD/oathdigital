package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.PreviewModifier
import oathdigital.testkit.Unwrap.present

/** The draft set as a value: what `reconcile` keeps and drops, what `staged`
  * touches, and what each `FlowExit` and `FlowStep` does to the flow draft
  * (spec, "leave").
  */
class SessionDraftsSuite extends munit.FunSuite:
  private val context = ForgeFixture.context
  private val advisers = Vector("a1", "a2").map(id => MinorAdviser(
    CardDetails(id, "denizen", s"Old Oak $id", orientation = Some("face-down"))))
  private val minor = MinorActionsState(advisers, canPeekSiteRelics = false,
    Vector.empty, None, 0, 0)
  private def projection(
      parked: Option[WalkerDecisionState] = Some(ForgeFixture.parked))
      : GameProjection =
    TestProjections.actingProjection(nextSequence = 9L, players = Vector(
      GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
      .copy(walkerDecision = parked, minorActions = Some(minor))
  private val modifier = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val ordering = ModifierFlowDrafts.orderingDraft(9L, Vector(modifier))

  /** Every slot filled, all bound to `context`. */
  private def full: SessionDrafts =
    SessionDrafts.empty.reconcile(context, projection()).copy(
      modifiers = Some(ordering))

  test("reconcile fills every slot the projection asks for and sets the context"):
    val drafts = SessionDrafts.empty.reconcile(context, projection())
    assertEquals(drafts.context, Some(context))
    assert(drafts.partition.exists(_.decisionId == "forge-9"))
    assertEquals(drafts.distribute, None)
    assertEquals(drafts.selection, None)
    assertEquals(drafts.board, None)
    assertEquals(drafts.modifiers, None)

  test("reconcile keeps every slot under the same context and drops them under another"):
    val kept = full.reconcile(context, projection())
    assertEquals(kept.modifiers, Some(ordering))
    val moved = full.reconcile(context.copy(sequence = 10), projection())
    assertEquals(moved.modifiers, None)
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

  test("every exit clears only the flow draft"):
    Vector(FlowExit.Failed, FlowExit.Cancelled, FlowExit.OrderingLeft).foreach:
      exit =>
        val left = full.leave(exit)
        assertEquals(left, full.copy(modifiers = None), exit.toString)
        assert(left.partition.nonEmpty)

  test("Ordering sets only the flow draft"):
    val stepped = full.copy(modifiers = None).step(FlowStep.Ordering(ordering))
    assertEquals(stepped, full.copy(modifiers = Some(ordering)))

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
