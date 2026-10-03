package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse,
  PreviewModifier, PreviewTarget, ModifierInvocation}
import oathdigital.testkit.Unwrap.present
import TestProjections.actingProjection

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
    val draft = ModifierFlowDraft(command = None, actionKind = Some("travel"),
      baseParameters = Map.empty, preview = response, selection = selection,
      stage = ModifierFlowStage.Ordering)
    ActionDecisionRenderer.actionsPanel(
      actingProjection(gameId = "g", nextSequence = 4L,
        activePlayer = Some("p"),
        players = Vector(GamePlayer("p", "P", "exile", PlayerColor.Red)))
        .copy(actionSelectionOpen = true),
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

  test("targeted actions open their own preview"):
    assertEquals(Vector("travel", "campaign-conquest", "campaign-raid",
      "play-facedown-adviser").flatMap(ModifierFlowDraft.targeted).map(_._1),
      Vector("travel", "search"))

  test("a walker start names a preview action only when the engine " +
      "registers it"):
    val commands = Vector[GameIntent](
      GameIntent.StartWalker("recover", Vector.empty),
      GameIntent.StartWalker("forge", Vector.empty),
      GameIntent.StartWalker("muster", Vector.empty),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "favor"))),
      GameIntent.StartWalker("travel", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("site", "site:a"))),
      GameIntent.StartWalker("search", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "search:world"))),
      GameIntent.StartWalker("play-facedown-adviser", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("denizen", "d1"))),
      GameIntent.StartWalker("place-banner-resource", Vector.empty))
    // A registered key names its action whatever start arguments it carries
    // (Travel carries its destination); play-facedown-adviser previews as
    // Search; place-banner-resource is not registered, so it names none.
    assertEquals(commands.flatMap(ModifierFlowDraft.action).map(_._1),
      Vector("recover", "forge", "muster", "trade", "travel", "search", "search"))
    assertEquals(ModifierFlowDraft.action(GameIntent.BeginRest), None)
    // An unregistered walker key is not swept in: the server would reject a
    // preview for an action it does not know.
    assertEquals(ModifierFlowDraft.action(GameIntent.StartWalker("teleport", Vector.empty)),
      None)

  test("after the preview, only the authorized targets are offered and a " +
      "pick needs confirmation"):
    val action = BoardTargetAction("travel", "Travel", minimum = 1,
      maximum = 1, autoActivate = false,
      Vector(BoardTargetCandidate(BoardTargetRef.Site("a"), "A", Vector.empty),
        BoardTargetCandidate(BoardTargetRef.Site("b"), "B", Vector.empty)))
    // A Site ref's key is site:<id>, so this authorizes Site("b") only.
    val response = MajorActionPreviewResponse(4, "travel", Vector(first),
      Vector.empty, Vector(PreviewTarget(key = "site:b", supplyCost = 2,
        description = "B")))
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "travel"), response.modifiers, "preview")
    val ordering = ModifierFlowDraft(command = None,
      actionKind = Some("travel"), baseParameters = Map.empty,
      preview = response, selection = selection,
      stage = ModifierFlowStage.Ordering)
    assert(ordering.ordering)
    val targets = ordering.showTargets(response)
    assert(!targets.ordering)
    val authorized = present(ModifierFlowDraft.targetAction("travel", response,
      Vector(action)), "an authorized Travel action")
    assertEquals(authorized.candidates.map(_.target), Vector(BoardTargetRef.Site("b")))
    assert(authorized.explicitConfirm)

  test("facedown adviser keeps ordered Search modifiers before owned target draft"):
    val response = MajorActionPreviewResponse(4, "search", Vector(first),
      Vector.empty, Vector.empty)
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "search"), response.modifiers, "facedown-preview")
      .toggle(first)
    val ordering = ModifierFlowDraft(command = None,
      actionKind = Some("play-facedown-adviser"),
      baseParameters = Map("procedure" -> "facedown-adviser"),
      preview = response, selection = selection,
      stage = ModifierFlowStage.Ordering)
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
    val targets = ModifierFlowDraft(command = None,
      actionKind = Some("travel"), baseParameters = Map.empty[String, String],
      preview = response, selection = selected,
      stage = ModifierFlowStage.Targets)
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

  private val startRecover = GameIntent.StartWalker("recover", Vector.empty)

  /** Recover's preview offering Catacombs, a modifier that comes from a card
    * at site `site:a`. `catacombs` is a readable stand-in id for this
    * fixture, not the catalog's id.
    */
  private val catacombs = PreviewModifier(
    "site-card:site:a:denizen:catacombs", "denizen.catacombs", "Catacombs")

  private val recoverPreview = MajorActionPreviewResponse(4, "recover",
    Vector(catacombs), Vector.empty, Vector.empty)

  private def catacombsSelected: ModifierSelectionState =
    ModifierSelectionState.reconcile(None, context.copy(action = "recover"),
      recoverPreview.modifiers, "catacombs-preview").toggle(catacombs)

  test("Recover uses the generic ordered modifier flow but submits as a " +
      "walker command carrying Catacombs in its own modifiers field"):
    assertEquals(ModifierFlowDraft.action(startRecover),
      Some("recover" -> Map.empty[String, String]))
    val selection = catacombsSelected
    val draft = ModifierFlowDraft(command = Some(startRecover),
      actionKind = Some("recover"), baseParameters = Map.empty,
      preview = recoverPreview, selection = selection,
      stage = ModifierFlowStage.Ordering)
    assert(draft.ordering)
    val invocation = ModifierInvocation("site-card", "catacombs",
      Some("site:a"), catacombs.handlerId)
    assertEquals(selection.invocations, Vector(invocation))
    // The Catacombs id lands inside StartWalker's own `modifiers`, not in a
    // WithModifiers wrapper -- GameApplicationService.majorAction does not
    // recognize StartWalker, so the legacy wrapper would reject it outright.
    val submitted = ModifierFlowDraft.submission(startRecover,
      selection.invocations)
    assertEquals(submitted, GameIntent.StartWalker("recover", Vector("denizen.catacombs")))

  test("Recover's Targets stage backs out to Ordering, and a stale context " +
      "clears the flow"):
    val draft = ModifierFlowDraft(command = Some(startRecover),
      actionKind = Some("recover"), baseParameters = Map.empty,
      preview = recoverPreview, selection = catacombsSelected,
      stage = ModifierFlowStage.Ordering)
    val targets = draft.showTargets(recoverPreview)
    assert(targets.backFromTargets.exists(_.ordering))
    assertEquals(ModifierFlowDraft.reconcile(Some(targets),
      BoardSelectionContext("g", "p", 5)), None)

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
    val invocation = ModifierInvocation("site-card", "catacombs",
      Some("site:a"), "denizen.some-power")
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

  test("a modifier option shows its printed cost, with or without a card"):
    val card = CardDetails("denizen:catacombs", "denizen", "Catacombs",
      orientation = Some("face-up"))
    val withCard = PreviewModifier("site-card:s1:catacombs",
      "denizen.catacombs", "Catacombs", Some(card), Some("recover"),
      cost = Some("1 secret"))
    val bare = PreviewModifier("game:denizen.relic-worship",
      "denizen.relic-worship", "Relic Worship", cost = Some("1 secret"))
    val free = PreviewModifier("game:denizen.rowdy-pub", "denizen.rowdy-pub",
      "Rowdy Pub")
    val costs = renderOrderingPanel(Vector(withCard, bare, free))
      .querySelectorAll(".modifier-option").toVector.map(row =>
        Option(row.querySelector(".modifier-cost")).map(_.textContent))
    assertEquals(costs, Vector(Some("Cost: 1 secret"), Some("Cost: 1 secret"),
      None))
