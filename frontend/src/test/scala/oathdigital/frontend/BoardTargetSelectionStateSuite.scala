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

  test("restoring projected actions after preview cancellation permits another action") {
    val travel = BoardTargetAction("travel", "Travel", 1, 1,
      autoActivate = false, Vector(siteA))
    val other = BoardTargetCandidate(BoardTargetRef.Site("c"), "C",
      Vector("1 Supply"))
    val challenge = BoardTargetAction("challenge", "Challenge", 1, 1,
      autoActivate = false, Vector(other))
    val previewSelection = BoardTargetSelectionState.reconcile(None, context,
      Vector(travel)).activate("travel")

    val restored = BoardTargetSelectionState.restore(context,
      Vector(travel, challenge)).activate("challenge")

    assertEquals(previewSelection.cancel.activeAction, None)
    assertEquals(restored.activeAction, Some(challenge))
    assertEquals(restored.choose(other.target),
      BoardSelectionResult.Submit(challenge, Vector(other.target)))
  }

  test("explicit-confirm target mode requires confirmation and supports cancel") {
    val action = BoardTargetAction("travel", "Travel", 1, 1,
      autoActivate = false, Vector(siteA), explicitConfirm = true)
    val active = BoardTargetSelectionState.reconcile(None, context,
      Vector(action)).activate(action.actionKind)
    val selected = active.choose(siteA.target)
      .asInstanceOf[BoardSelectionResult.Updated].state
    assert(selected.canConfirm)
    assertEquals(selected.confirm.map(_.targets), Some(Vector(siteA.target)))
    assertEquals(selected.cancel.activeAction, None)
  }

  test("state clears on sequence player game candidate or action changes") {
    val action = BoardTargetAction("travel", "Travel", 1, 1,
      autoActivate = false, Vector(siteA, siteB), explicitConfirm = true)
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
      Vector(action.copy(maximum = 1, candidates = Vector(siteA)))).selectedKeys,
      Set.empty[String])
  }
}
