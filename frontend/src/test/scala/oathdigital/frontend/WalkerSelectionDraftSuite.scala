package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => Intent}

class WalkerSelectionDraftSuite extends munit.FunSuite {
  private def site(id: String) = DecisionOptionState("site", id, id)
  private val many = DecisionQueryState("choose-many",
    Vector(site("a"), site("b"), site("c")), heading = Some("Choose sites"),
    count = Some(2))
  private val amount = DecisionQueryState("choose-amount", Vector.empty,
    heading = Some("Place more than 2 favor"), confirmLabel = Some("Take banner"),
    minimum = Some(3), maximum = Some(6))
  private val context = BoardSelectionContext("game", "red", 9)
  private def parked(id: String, query: DecisionQueryState) =
    Some(WalkerDecisionState("challenge", id, "decide", query = Some(query)))

  test("a choose-many draft opens empty, toggles, and never exceeds its count") {
    val Some(draft: WalkerChooseManyDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("challenge.ribbon-site", many)): @unchecked
    assertEquals(draft.selected, Vector.empty)
    assert(!draft.canConfirm)
    val two = draft.toggle("site:a").toggle("site:c")
    assertEquals(two.selected, Vector("site:a", "site:c"))
    assert(two.canConfirm)
    assertEquals(two.toggle("site:b"), two)
    assertEquals(two.toggle("site:a").selected, Vector("site:c"))
  }

  test("a choose-many draft submits its selection in declared option order") {
    val Some(draft: WalkerChooseManyDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("challenge.ribbon-site", many)): @unchecked
    assertEquals(draft.toggle("site:c").command, None)
    assertEquals(draft.toggle("site:c").toggle("site:a").command,
      Some(Intent.ResolveWalker("challenge.ribbon-site",
        DecisionAnswerWire.ChooseManyWire(Vector(DecisionOptionWire("site", "a"),
          DecisionOptionWire("site", "c"))))))
  }

  test("a choose-amount draft opens at the minimum and clamps to its range") {
    val Some(draft: WalkerAmountDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("challenge.amount", amount)): @unchecked
    assertEquals(draft.amount, 3)
    assertEquals(draft.choose(9).amount, 6)
    assertEquals(draft.choose(1).amount, 3)
    assertEquals(draft.choose(5).command, Some(Intent.ResolveWalker(
      "challenge.amount", DecisionAnswerWire.ChooseAmountWire(5))))
  }

  test("reconcile keeps a draft for the same decision and drops it otherwise") {
    val Some(first: WalkerChooseManyDraft) = WalkerSelectionDraft.reconcile(None,
      context, parked("challenge.ribbon-site", many)): @unchecked
    val edited = first.toggle("site:a")
    assertEquals(WalkerSelectionDraft.reconcile(Some(edited), context,
      parked("challenge.ribbon-site", many)), Some(edited))
    assertEquals(WalkerSelectionDraft.reconcile(Some(edited), context.copy(
      sequence = 10), parked("challenge.ribbon-site", many)),
      Some(first.copy(context = context.copy(sequence = 10))))
    assertEquals(WalkerSelectionDraft.reconcile(Some(edited), context,
      parked("challenge.amount", amount)).map(_.getClass),
      Some(classOf[WalkerAmountDraft]))
    assertEquals(WalkerSelectionDraft.reconcile(Some(edited), context, None), None)
  }
}
