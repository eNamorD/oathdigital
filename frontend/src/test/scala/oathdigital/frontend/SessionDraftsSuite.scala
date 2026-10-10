package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.testkit.Unwrap.present

/** The draft set as a value: what `reconcile` keeps and drops, and what
  * `staged` touches.
  */
class SessionDraftsSuite extends munit.FunSuite:
  private val context = ForgeFixture.context
  private def projection(
      parked: Option[WalkerDecisionState] = Some(ForgeFixture.parked))
      : GameProjection =
    TestProjections.actingProjection(nextSequence = 9L, players = Vector(
      GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
      .copy(walkerDecision = parked)

  /** Every slot the projection fills, all bound to `context`. */
  private def full: SessionDrafts =
    SessionDrafts.empty.reconcile(context, projection())

  test("reconcile fills every slot the projection asks for and sets the context"):
    val drafts = SessionDrafts.empty.reconcile(context, projection())
    assertEquals(drafts.context, Some(context))
    assertEquals(drafts.partition.map(_.decisionId), Some("forge-9"))
    assertEquals(drafts.distribute, None)
    assertEquals(drafts.selection, None)
    assertEquals(drafts.board, None)

  test("reconcile keeps every slot under the same context and drops them under another"):
    val moved = present(full.partition, "a partition draft")
      .move("denizen:denizen:3", "pay-favor")
    val staged = full.staged(Draft.Partition(moved))
    assertEquals(staged.reconcile(context, projection()).partition,
      Some(moved))
    val fresh = staged.reconcile(context.copy(sequence = 10), projection())
    assertEquals(fresh.partition.map(_.context),
      Some(context.copy(sequence = 10)))
    assertNotEquals(fresh.partition, Some(moved))

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
