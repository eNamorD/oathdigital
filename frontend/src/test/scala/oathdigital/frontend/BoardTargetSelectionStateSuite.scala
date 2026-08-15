package oathdigital.frontend

class BoardTargetSelectionStateSuite extends munit.FunSuite {
  private val siteA = BoardTargetCandidate(BoardTargetRef.Site("a"), "A",
    Vector("2 Supply"))
  private val siteB = BoardTargetCandidate(BoardTargetRef.Site("b"), "B",
    Vector.empty)
  private val context = BoardSelectionContext("game", "red", 4)

  test("automatic setup selection cannot cancel and single choice submits") {
    val action = BoardTargetAction("place-pawn", "Choose", 1, 1,
      autoActivate = true, Vector(siteA))
    val state = BoardTargetSelectionState.reconcile(None, context, Vector(action))
    assertEquals(state.activeActionKind, Some("place-pawn"))
    assertEquals(state.cancel, state)
    assertEquals(state.choose(siteA.target),
      BoardSelectionResult.Submit(action, Vector(siteA.target)))
  }

  test("optional modes activate cancel and ignore unauthorized targets") {
    val action = BoardTargetAction("travel", "Travel", 1, 1,
      autoActivate = false, Vector(siteA))
    val initial = BoardTargetSelectionState.reconcile(None, context, Vector(action))
    assertEquals(initial.activeAction, None)
    val active = initial.activate("travel")
    assertEquals(active.activeActionKind, Some("travel"))
    assertEquals(active.cancel.activeAction, None)
    assertEquals(active.choose(siteB.target), BoardSelectionResult.Updated(active))
  }

  test("multi target selection toggles caps and confirms in candidate order") {
    val third = BoardTargetCandidate(BoardTargetRef.Site("c"), "C", Vector.empty)
    val action = BoardTargetAction("campaign-sites", "Targets", 1, 2,
      autoActivate = false, Vector(siteA, siteB, third))
    val active = BoardTargetSelectionState.reconcile(None, context,
      Vector(action)).activate(action.actionKind)
    val one = active.choose(siteB.target).asInstanceOf[
      BoardSelectionResult.Updated].state
    val two = one.keyboardChoose("Enter", siteA.target).asInstanceOf[
      BoardSelectionResult.Updated].state
    val capped = two.keyboardChoose(" ", third.target).asInstanceOf[
      BoardSelectionResult.Updated].state
    assertEquals(capped, two)
    assert(two.canConfirm)
    assertEquals(two.confirm.map(_.targets),
      Some(Vector(siteA.target, siteB.target)))
  }

  test("Campaign keeps its mandatory site selected and forms all chosen targets") {
    val action = BoardTargetAction("campaign-conquest", "Targets", 1, 2,
      autoActivate = false, Vector(siteA, siteB),
      Some(BoardTargetFormation(0, 3, 3, 2)), Vector(siteA.target))
    val active = BoardTargetSelectionState.reconcile(None, context,
      Vector(action)).activate(action.actionKind)
    assert(active.selected(siteA.target))
    val stillRequired = active.choose(siteA.target).asInstanceOf[
      BoardSelectionResult.Updated].state
    assert(stillRequired.selected(siteA.target))
    val selected = stillRequired.choose(siteB.target).asInstanceOf[
      BoardSelectionResult.Updated].state
    val formation = selected.confirmResult.get.asInstanceOf[
      BoardSelectionResult.Form].state
    assertEquals(formation.targets, Vector(siteA.target, siteB.target))
  }

  test("state clears on sequence player game candidate or action changes") {
    val action = BoardTargetAction("travel", "Travel", 1, 2,
      autoActivate = false, Vector(siteA, siteB))
    val selected = BoardTargetSelectionState.reconcile(None, context,
      Vector(action)).activate("travel").choose(siteA.target)
      .asInstanceOf[BoardSelectionResult.Updated].state
    assertEquals(BoardTargetSelectionState.reconcile(Some(selected), context,
      Vector(action)), selected)
    Vector(
      context.copy(sequence = 5), context.copy(playerId = "blue"),
      context.copy(gameId = "other")
    ).foreach(next => assertEquals(BoardTargetSelectionState.reconcile(
      Some(selected), next, Vector(action)).selectedKeys, Set.empty[String]))
    assertEquals(BoardTargetSelectionState.reconcile(Some(selected), context,
      Vector(action.copy(candidates = Vector(siteA)))).selectedKeys,
      Set.empty[String])
  }

  test("adviser relic and site-card target refs have stable distinct keys") {
    val refs = Vector[BoardTargetRef](
      BoardTargetRef.SiteCard("site", "denizen", "d1"),
      BoardTargetRef.SiteCard("site", "edifice", "d1"),
      BoardTargetRef.PlayerAdviser("red", "d1"),
      BoardTargetRef.PlayerRelic("red", "d1"))
    assertEquals(refs.map(_.stableKey).distinct.size, refs.size)
  }

  test("formation follows target choice and enforces projected force bounds") {
    val action = BoardTargetAction("campaign-conquest", "Campaign", 1, 1,
      autoActivate = false, Vector(siteA),
      Some(BoardTargetFormation(1, 4, 4, 2)))
    val active = BoardTargetSelectionState.reconcile(None, context,
      Vector(action)).activate(action.actionKind)
    val formation = active.choose(siteA.target)
      .asInstanceOf[BoardSelectionResult.Form].state
    assertEquals(formation.force, 4)
    assertEquals(formation.decrement.force, 3)
    assertEquals(formation.choose(2).force, 2)
    assertEquals(formation.choose(0), formation)
    assertEquals(formation.increment, formation)
    assertEquals(formation.remainingWarbands, 0)
    assertEquals(formation.attackDiceBeforePlans, 4)
    assertEquals(formation.supplyCost, 2)
  }

  test("empty formation selects confirms and reports all warbands remaining") {
    val action = BoardTargetAction("campaign-conquest", "Campaign", 1, 1,
      false, Vector(siteA), Some(BoardTargetFormation(0, 0, 0, 2)))
    val formation = BoardTargetSelectionState.reconcile(None, context,
      Vector(action)).activate(action.actionKind).choose(siteA.target)
      .asInstanceOf[BoardSelectionResult.Form].state
    assertEquals(formation.force, 0)
    assertEquals(formation.decrement.force, 0)
    assertEquals(formation.increment.force, 0)
    assertEquals(formation.remainingWarbands, 0)
    assertEquals(formation.attackDiceBeforePlans, 0)
  }

  test("formation clears on context candidates or projected facts changes") {
    val action = BoardTargetAction("campaign-conquest", "Campaign", 1, 1,
      false, Vector(siteA), Some(BoardTargetFormation(1, 4, 4, 2)))
    val formation = BoardTargetFormationState(context, action, siteA.target, 2)
    assertEquals(BoardTargetFormationState.reconcile(Some(formation), context,
      Vector(action)), Some(formation))
    assertEquals(BoardTargetFormationState.reconcile(Some(formation),
      context.copy(sequence = 5), Vector(action)), None)
    assertEquals(BoardTargetFormationState.reconcile(Some(formation), context,
      Vector(action.copy(candidates = Vector(siteB)))), None)
    assertEquals(BoardTargetFormationState.reconcile(Some(formation), context,
      Vector(action.copy(formation = Some(BoardTargetFormation(1, 3, 3, 2))))), None)
  }
}
