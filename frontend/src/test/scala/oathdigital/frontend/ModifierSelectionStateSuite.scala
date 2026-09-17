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
      GameIntent.StartWalker("recover", Vector.empty),
      GameIntent.StartWalker("forge", Vector.empty),
      // Travel joined the walker at batch-1 Task 5. It is the first action
      // that is both walker-registered and board-targeted, so it reaches this
      // path carrying a destination its two predecessors have no equivalent
      // of -- and it must still be offered its modifiers, not skipped.
      GameIntent.StartWalker("travel", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("site", "site:a"))),
      GameIntent.StartWalker("search", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "search:world"))),
      GameIntent.StartWalker("play-facedown-adviser", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("denizen", "d1"))),
      GameIntent.ResolveFacedownAdviser(oathdigital.protocol.WorldCard("denizen", "d1"), None))
    assertEquals(commands.flatMap(ModifierWorkflow.action).map(_._1),
      Vector("search", "recover", "forge", "travel", "search", "search",
        "search"))
    assertEquals(ModifierWorkflow.action(GameIntent.BeginRest), None)
    // An UNREGISTERED walker action must not be swept into the same
    // modifier-offering path: only the keys the engine registers on the
    // walker ("recover" and, since batch-1 Task 3, "forge") map to a
    // preview the server will answer.
    assertEquals(ModifierWorkflow.action(GameIntent.StartWalker("teleport", Vector.empty)),
      None)
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

  test("facedown adviser keeps ordered Search modifiers before owned target draft") {
    val response = MajorActionPreviewResponse(4, "search", Vector(first),
      Vector.empty, Vector.empty)
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "search"), response.modifiers, "facedown-preview")
      .toggle(first)
    val ordering = ModifierWorkflow(None, Some("play-facedown-adviser"),
      Map("procedure" -> "facedown-adviser"), response, selection,
      ModifierWorkflowStage.Ordering)
    assertEquals(ordering.selection.selected, Vector(first))
    val targets = ordering.showTargets(response)
    assertEquals(targets.actionKind, Some("play-facedown-adviser"))
    assertEquals(targets.baseParameters,
      Map("procedure" -> "facedown-adviser"))
    assertEquals(targets.backFromTargets.map(_.selection.selected),
      Some(Vector(first)))
    assertEquals(targets.cancel, None)
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

  test("Recover uses the generic ordered modifier flow but submits as a " +
      "walker command carrying Catacombs in its own modifiers field") {
    val catacombs = PreviewModifier("site-card:site:a:denizen:201",
      "denizen.catacombs", "Catacombs")
    val response = MajorActionPreviewResponse(4, "recover", Vector(catacombs),
      Vector.empty, Vector.empty)
    val startRecover = GameIntent.StartWalker("recover", Vector.empty)
    assertEquals(ModifierWorkflow.action(startRecover),
      Some("recover" -> Map.empty[String, String]))
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "recover"), response.modifiers, "catacombs-preview")
      .toggle(catacombs)
    val workflow = ModifierWorkflow(Some(startRecover), Some("recover"),
      Map.empty, response, selection, ModifierWorkflowStage.Ordering)
    assert(workflow.ordering)
    val targets = workflow.showTargets(response)
    assert(targets.backFromTargets.exists(_.ordering))
    assertEquals(ModifierWorkflow.reconcile(Some(targets), "g", "p", 5), None)
    val invocation = ModifierInvocation("site-card", "201", Some("site:a"),
      catacombs.handlerId)
    assertEquals(selection.invocations, Vector(invocation))
    // The Catacombs id lands inside StartWalker's own `modifiers`, not in a
    // WithModifiers wrapper -- GameApplicationService.majorAction does not
    // recognize StartWalker, so the legacy wrapper would reject it outright.
    val (submitted, outerModifiers) = ModifierWorkflow.submission(startRecover,
      selection.invocations)
    assertEquals(submitted, GameIntent.StartWalker("recover", Vector("denizen.catacombs")))
    assertEquals(outerModifiers, Vector.empty[ModifierInvocation])
    val encoded = ActorlessCommandCodec.encode(ActorlessCommandRequest(4,
      submitted, outerModifiers))
    val decoded = ActorlessCommandCodec.decode(encoded).toOption.get
    assertEquals(decoded.intent, submitted)
    assertEquals(decoded.orderedModifiers, Vector.empty[ModifierInvocation])
  }

  test("submission leaves non-walker commands on the legacy ordered-modifiers " +
      "channel untouched") {
    val invocation = ModifierInvocation("site-card", "201", Some("site:a"),
      "denizen.some-power")
    val (submitted, outerModifiers) = ModifierWorkflow.submission(
      GameIntent.BeginSearch(oathdigital.protocol.SearchSource("world", None)),
      Vector(invocation))
    assertEquals(submitted,
      GameIntent.BeginSearch(oathdigital.protocol.SearchSource("world", None)))
    assertEquals(outerModifiers, Vector(invocation))
  }

  test("Search and facedown-adviser walker starts retain their card arguments") {
    val invocation = ModifierInvocation("site-card", "201", Some("site:a"),
      "denizen.some-power")
    Vector(
      GameIntent.StartWalker("search", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "search:world"))),
      GameIntent.StartWalker("play-facedown-adviser", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("denizen", "D1"))))
      .foreach { intent =>
        val (submitted, outer) = ModifierWorkflow.submission(intent,
          Vector(invocation))
        assertEquals(submitted.asInstanceOf[GameIntent.StartWalker].startArgs,
          intent.asInstanceOf[GameIntent.StartWalker].startArgs)
        assertEquals(submitted.asInstanceOf[GameIntent.StartWalker].modifiers,
          Vector("denizen.some-power"))
        assertEquals(outer, Vector.empty)
      }
  }
}
