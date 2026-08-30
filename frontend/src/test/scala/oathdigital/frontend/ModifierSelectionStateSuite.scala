package oathdigital.frontend

import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse,
  PreviewModifier, PreviewTarget, ActorlessCommandCodec, ActorlessCommandRequest,
  ModifierInvocation}

class ModifierSelectionStateSuite extends munit.FunSuite {
  private val context = ModifierSelectionContext("g", "p", 4, "trade")
  private val first = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val second = PreviewModifier("site-card:s:denizen:b", "h.b", "B")

  test("selection preserves click order supports badges reorder toggle and keyboard") {
    val empty = ModifierSelectionState.reconcile(None, context,
      Vector(first, second), "preview-1")
    val chosen = empty.toggle(second).keyboard(first, "Enter")
    assertEquals(chosen.selected, Vector(second, first))
    assertEquals(chosen.ordinal(first), Some(2))
    assertEquals(chosen.moveEarlier(first).selected, Vector(first, second))
    assertEquals(chosen.keyboard(second, "ArrowUp").selected, Vector(second, first))
    assertEquals(chosen.toggle(second).selected, Vector(first))
  }

  test("game viewer sequence candidates and preview changes clear the draft") {
    val selected = ModifierSelectionState.reconcile(None, context,
      Vector(first), "preview-1").toggle(first)
    assertEquals(ModifierSelectionState.reconcile(Some(selected), context,
      Vector(first), "preview-1").selected, Vector(first))
    assert(ModifierSelectionState.reconcile(Some(selected), context.copy(sequence = 5),
      Vector(first), "preview-1").selected.isEmpty)
    assert(ModifierSelectionState.reconcile(Some(selected), context,
      Vector(first, second), "preview-1").selected.isEmpty)
    assert(ModifierSelectionState.reconcile(Some(selected), context,
      Vector(first), "preview-2").selected.isEmpty)
  }

  test("targeted actions preview before commands while direct actions retain their stage") {
    assertEquals(Vector("travel", "campaign-conquest", "campaign-raid", "muster",
      "trade-favor", "trade-secret", "play-facedown-adviser")
      .flatMap(ModifierWorkflow.targeted).map(_._1),
      Vector("travel", "campaign", "campaign", "muster", "trade", "trade", "search"))
    val commands = Vector[GameIntent](
      GameIntent.BeginSearch(oathdigital.protocol.SearchSource("world", None)),
      GameIntent.BeginForge,
      GameIntent.BeginRecover,
      GameIntent.ResolveFacedownAdviser(oathdigital.protocol.WorldCard("denizen", "d1"), None))
    assertEquals(commands.flatMap(ModifierWorkflow.action).map(_._1),
      Vector("search", "forge", "recover", "search"))
    assertEquals(ModifierWorkflow.action(GameIntent.Travel("site:a")), None)
    assertEquals(ModifierWorkflow.action(GameIntent.BeginRest), None)
  }

  test("modifier confirmation exposes preview-authorized targets without submitting") {
    val action = BoardTargetAction("travel", "Travel", 1, 1, false,
      Vector(BoardTargetCandidate(BoardTargetRef.Site("a"), "A", Vector.empty),
        BoardTargetCandidate(BoardTargetRef.Site("b"), "B", Vector.empty)))
    val response = MajorActionPreviewResponse(4, "travel", Vector(first), Vector.empty,
      Vector(PreviewTarget("site:b", 2, "B")))
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "travel"), response.modifiers, "preview")
    val ordering = ModifierWorkflow(None, Some("travel"), Map.empty, response,
      selection, ModifierWorkflowStage.Ordering)
    assert(ordering.ordering)
    val targets = ordering.showTargets(response)
    assert(!targets.ordering)
    val authorized = ModifierWorkflow.targetAction("travel", response, Vector(action)).get
    assertEquals(authorized.candidates.map(_.target), Vector(BoardTargetRef.Site("b")))
    assert(authorized.explicitConfirm)
  }

  test("zero modifiers skip ordering and Economy still requires explicit confirmation") {
    val target = BoardTargetCandidate(
      BoardTargetRef.SiteCard("site", "denizen", "d1"), "D1", Vector.empty)
    val action = BoardTargetAction("trade-secret", "Trade", 1, 1, false,
      Vector(target))
    val response = MajorActionPreviewResponse(4, "trade", Vector.empty, Vector.empty,
      Vector(PreviewTarget("denizen:d1", 1, "D1")))
    val selection = ModifierSelectionState.reconcile(None, context,
      Vector.empty, "empty")
    val workflow = ModifierWorkflow(None, Some("trade-secret"),
      Map("resource" -> "secret"), response, selection,
      ModifierWorkflowStage.Targets)
    assert(!workflow.ordering)
    val authorized = ModifierWorkflow.targetAction("trade-secret", response,
      Vector(action)).get
    val chosen = BoardTargetSelectionState.reconcile(None,
      BoardSelectionContext("g", "p", 4), Vector(authorized))
      .activate("trade-secret").choose(target.target)
    assert(chosen.isInstanceOf[BoardSelectionResult.Updated])
    assert(chosen.asInstanceOf[BoardSelectionResult.Updated].state.canConfirm)
  }

  test("target Back restores ordering only when present and stale context clears flow") {
    val response = MajorActionPreviewResponse(4, "trade", Vector(first),
      Vector.empty, Vector.empty)
    val selected = ModifierSelectionState.reconcile(None, context,
      Vector(first), "preview").toggle(first)
    val targets = ModifierWorkflow(None, Some("trade-favor"),
      Map("resource" -> "favor"), response, selected,
      ModifierWorkflowStage.Targets)
    assert(targets.backFromTargets.exists(_.ordering))
    assertEquals(targets.cancel, None)
    assertEquals(targets.copy(preview = response.copy(modifiers = Vector.empty))
      .backFromTargets, None)
    assertEquals(ModifierWorkflow.reconcile(Some(targets), "g", "p", 4),
      Some(targets))
    assertEquals(ModifierWorkflow.reconcile(Some(targets), "g", "p", 5), None)
    assertEquals(ModifierWorkflow.reconcile(Some(targets), "g", "other", 4), None)
  }

  test("empty-site Recover uses the generic ordered Catacombs modifier flow") {
    val catacombs = PreviewModifier("site-card:site:a:denizen:201",
      "denizen.catacombs", "Catacombs")
    val response = MajorActionPreviewResponse(4, "recover", Vector(catacombs),
      Vector.empty, Vector.empty)
    assertEquals(ModifierWorkflow.action(GameIntent.BeginRecover),
      Some("recover" -> Map.empty[String, String]))
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "recover"), response.modifiers, "catacombs-preview")
      .toggle(catacombs)
    val workflow = ModifierWorkflow(Some(GameIntent.BeginRecover), Some("recover"),
      Map.empty, response, selection, ModifierWorkflowStage.Ordering)
    assert(workflow.ordering)
    val targets = workflow.showTargets(response)
    assert(targets.backFromTargets.exists(_.ordering))
    assertEquals(ModifierWorkflow.reconcile(Some(targets), "g", "p", 5), None)
    val invocation = ModifierInvocation("site-card", "201", Some("site:a"),
      catacombs.handlerId)
    val encoded = ActorlessCommandCodec.encode(ActorlessCommandRequest(4,
      GameIntent.BeginRecover, Vector(invocation)))
    val decoded = ActorlessCommandCodec.decode(encoded).toOption.get
    assertEquals(decoded.intent, GameIntent.BeginRecover)
    assertEquals(decoded.orderedModifiers, Vector(invocation))
  }
}
