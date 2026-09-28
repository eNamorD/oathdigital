package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse,
  PreviewModifier, PreviewTarget, ActorlessCommandCodec, ActorlessCommandRequest,
  ModifierInvocation}

class ModifierSelectionStateSuite extends munit.FunSuite:
  private val context = ModifierSelectionContext("g", "p", 4, "trade")
  private val first = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val second = PreviewModifier("site-card:s:denizen:b", "h.b", "B")

  /** Reaches the `drafts.modifiers.exists(_.ordering)` branch of
    * `ActionDecisionRenderer.actionsPanel`.
    */
  private def renderOrderingPanel(modifiers: Vector[PreviewModifier])
      : org.scalajs.dom.Element =
    val response = MajorActionPreviewResponse(4L, "travel", modifiers,
      Vector.empty, Vector.empty)
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "travel"), modifiers, "ordering-preview")
    val draft = ModifierFlowDraft(None, Some("travel"), Map.empty, response,
      selection, ModifierFlowStage.Ordering)
    ActionDecisionRenderer.actionsPanel(
      GameProjection("g", 4L, "act", Some("p"),
        Vector(GamePlayer("p", "P", "exile", PlayerColor.Red)),
        Vector.empty, Vector.empty, Vector.empty, ready = true,
        completed = false, actionSelectionOpen = true),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None,
        None, playerId = "p"),
      ParkedDecision.Routed(None, None), canControl = true,
      SessionDrafts.empty.copy(modifiers = Some(draft)),
      new RecordingControls()).element

  test("selection preserves click order supports badges reorder toggle and keyboard"):
    val empty = ModifierSelectionState.reconcile(None, context,
      Vector(first, second), "preview-1")
    val chosen = empty.toggle(second).keyboard(first, "Enter")
    assertEquals(chosen.selected, Vector(second, first))
    assertEquals(chosen.ordinal(first), Some(2))
    assertEquals(chosen.moveEarlier(first).selected, Vector(first, second))
    assertEquals(chosen.keyboard(second, "ArrowUp").selected, Vector(second, first))
    assertEquals(chosen.toggle(second).selected, Vector(first))

  test("game viewer sequence candidates and preview changes clear the draft"):
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

  test("targeted actions preview before commands while direct actions retain their stage"):
    assertEquals(Vector("travel", "campaign-conquest", "campaign-raid",
      "play-facedown-adviser").flatMap(ModifierFlowDraft.targeted).map(_._1),
      Vector("travel", "search"))
    val commands = Vector[GameIntent](
      GameIntent.StartWalker("recover", Vector.empty),
      GameIntent.StartWalker("forge", Vector.empty),
      GameIntent.StartWalker("muster", Vector.empty),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "favor"))),
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
      GameIntent.StartWalker("place-banner-resource", Vector.empty))
    assertEquals(commands.flatMap(ModifierFlowDraft.action).map(_._1),
      Vector("recover", "forge", "muster", "trade", "travel", "search", "search"))
    assertEquals(ModifierFlowDraft.action(GameIntent.BeginRest), None)
    // An UNREGISTERED walker action must not be swept into the same
    // modifier-offering path: only the keys the engine registers on the
    // walker ("recover" and, since batch-1 Task 3, "forge") map to a
    // preview the server will answer.
    assertEquals(ModifierFlowDraft.action(GameIntent.StartWalker("teleport", Vector.empty)),
      None)

  test("modifier confirmation exposes preview-authorized targets without submitting"):
    val action = BoardTargetAction("travel", "Travel", 1, 1, false,
      Vector(BoardTargetCandidate(BoardTargetRef.Site("a"), "A", Vector.empty),
        BoardTargetCandidate(BoardTargetRef.Site("b"), "B", Vector.empty)))
    val response = MajorActionPreviewResponse(4, "travel", Vector(first), Vector.empty,
      Vector(PreviewTarget("site:b", 2, "B")))
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "travel"), response.modifiers, "preview")
    val ordering = ModifierFlowDraft(None, Some("travel"), Map.empty, response,
      selection, ModifierFlowStage.Ordering)
    assert(ordering.ordering)
    val targets = ordering.showTargets(response)
    assert(!targets.ordering)
    val authorized = ModifierFlowDraft.targetAction("travel", response, Vector(action)).get
    assertEquals(authorized.candidates.map(_.target), Vector(BoardTargetRef.Site("b")))
    assert(authorized.explicitConfirm)

  test("facedown adviser keeps ordered Search modifiers before owned target draft"):
    val response = MajorActionPreviewResponse(4, "search", Vector(first),
      Vector.empty, Vector.empty)
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "search"), response.modifiers, "facedown-preview")
      .toggle(first)
    val ordering = ModifierFlowDraft(None, Some("play-facedown-adviser"),
      Map("procedure" -> "facedown-adviser"), response, selection,
      ModifierFlowStage.Ordering)
    assertEquals(ordering.selection.selected, Vector(first))
    val targets = ordering.showTargets(response)
    assertEquals(targets.actionKind, Some("play-facedown-adviser"))
    assertEquals(targets.baseParameters,
      Map("procedure" -> "facedown-adviser"))
    assertEquals(targets.backFromTargets.map(_.selection.selected),
      Some(Vector(first)))
    assertEquals(targets.cancel, None)

  test("target Back restores ordering only when present and stale context clears flow"):
    val response = MajorActionPreviewResponse(4, "travel", Vector(first),
      Vector.empty, Vector.empty)
    val selected = ModifierSelectionState.reconcile(None, context,
      Vector(first), "preview").toggle(first)
    val targets = ModifierFlowDraft(None, Some("travel"),
      Map.empty[String, String], response, selected,
      ModifierFlowStage.Targets)
    assert(targets.backFromTargets.exists(_.ordering))
    assertEquals(targets.cancel, None)
    assertEquals(targets.copy(preview = response.copy(modifiers = Vector.empty))
      .backFromTargets, None)
    assertEquals(ModifierFlowDraft.reconcile(Some(targets),
      BoardSelectionContext("g", "p", 4)), Some(targets))
    assertEquals(ModifierFlowDraft.reconcile(Some(targets),
      BoardSelectionContext("g", "p", 5)), None)
    assertEquals(ModifierFlowDraft.reconcile(Some(targets),
      BoardSelectionContext("g", "other", 4)), None)

  test("Recover uses the generic ordered modifier flow but submits as a " +
      "walker command carrying Catacombs in its own modifiers field"):
    val catacombs = PreviewModifier("site-card:site:a:denizen:201",
      "denizen.catacombs", "Catacombs")
    val response = MajorActionPreviewResponse(4, "recover", Vector(catacombs),
      Vector.empty, Vector.empty)
    val startRecover = GameIntent.StartWalker("recover", Vector.empty)
    assertEquals(ModifierFlowDraft.action(startRecover),
      Some("recover" -> Map.empty[String, String]))
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "recover"), response.modifiers, "catacombs-preview")
      .toggle(catacombs)
    val draft = ModifierFlowDraft(Some(startRecover), Some("recover"),
      Map.empty, response, selection, ModifierFlowStage.Ordering)
    assert(draft.ordering)
    val targets = draft.showTargets(response)
    assert(targets.backFromTargets.exists(_.ordering))
    assertEquals(ModifierFlowDraft.reconcile(Some(targets),
      BoardSelectionContext("g", "p", 5)), None)
    val invocation = ModifierInvocation("site-card", "201", Some("site:a"),
      catacombs.handlerId)
    assertEquals(selection.invocations, Vector(invocation))
    // The Catacombs id lands inside StartWalker's own `modifiers`, not in a
    // WithModifiers wrapper -- GameApplicationService.majorAction does not
    // recognize StartWalker, so the legacy wrapper would reject it outright.
    val submitted = ModifierFlowDraft.submission(startRecover,
      selection.invocations)
    assertEquals(submitted, GameIntent.StartWalker("recover", Vector("denizen.catacombs")))
    val encoded = ActorlessCommandCodec.encode(ActorlessCommandRequest(4,
      submitted, Vector.empty))
    val decoded = ActorlessCommandCodec.decode(encoded).toOption.get
    assertEquals(decoded.intent, submitted)
    assertEquals(decoded.orderedModifiers, Vector.empty[ModifierInvocation])

  test("a selected game-rule modifier such as Rowdy Pub invokes without throwing"):
    val rowdyPub = PreviewModifier("game:denizen.rowdy-pub", "denizen.rowdy-pub",
      "Rowdy Pub")
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "muster"), Vector(rowdyPub), "muster-preview")
      .toggle(rowdyPub)
    assertEquals(selection.invocations, Vector(
      ModifierInvocation("game", "denizen.rowdy-pub", None, "denizen.rowdy-pub")))
    assertEquals(ModifierFlowDraft.submission(
      GameIntent.StartWalker("muster", Vector.empty), selection.invocations),
      GameIntent.StartWalker("muster", Vector("denizen.rowdy-pub")))

  test("Search and facedown-adviser walker starts retain their card arguments"):
    val invocation = ModifierInvocation("site-card", "201", Some("site:a"),
      "denizen.some-power")
    Vector(
      GameIntent.StartWalker("search", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "search:world"))),
      GameIntent.StartWalker("play-facedown-adviser", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("denizen", "D1"))))
      .foreach { intent =>
        val submitted = ModifierFlowDraft.submission(intent, Vector(invocation))
        assertEquals(submitted.startArgs, intent.startArgs)
        assertEquals(submitted.modifiers, Vector("denizen.some-power"))
      }

  test("a modifier option draws its card and names the action it modifies"):
    val card = CardDetails("denizen:vow-of-peace", "denizen", "Vow of Peace",
      orientation = Some("face-up"))
    val modifier = PreviewModifier("adviser:p1:denizen:vow-of-peace",
      "denizen.vow-of-peace", "Vow of Peace", Some(card), Some("travel"))
    val panel = renderOrderingPanel(Vector(modifier))
    assertEquals(panel.querySelectorAll(".modifier-option .card-face")
      .toVector.size, 1)
    assertEquals(panel.querySelector(".modifier-modifies").textContent,
      "Travel Modifier")
    assertEquals(panel.textContent.contains("denizen.vow-of-peace"), false)
