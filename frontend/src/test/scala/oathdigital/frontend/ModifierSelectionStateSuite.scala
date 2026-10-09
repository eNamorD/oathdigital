package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent, MajorActionPreviewResponse,
  PreviewModifier, ModifierInvocation}
import TestProjections.actingProjection

class ModifierSelectionStateSuite extends munit.FunSuite:
  private val context = ModifierSelectionContext("g", "p", 4, "trade")
  private val first = PreviewModifier("adviser:p:denizen:a", "h.a", "A")
  private val second = PreviewModifier("site-card:s:denizen:b", "h.b", "B")

  /** Reaches the modifier-ordering branch of
    * `ActionDecisionRenderer.actionsPanel`.
    */
  private def renderOrderingPanel(modifiers: Vector[PreviewModifier])
      : org.scalajs.dom.Element =
    val response = MajorActionPreviewResponse(4L, "travel", modifiers,
      Vector.empty)
    val selection = ModifierSelectionState.reconcile(None,
      context.copy(action = "travel"), modifiers, "ordering-preview")
    val draft = ModifierFlowDraft(
      command = GameIntent.StartWalker("travel", Vector.empty),
      baseParameters = Map.empty, preview = response, selection = selection)
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
    assertEquals(ModifierSelectionState.reconcile(Some(selected),
      context.copy(sequence = 5), Vector(first), "preview-1").selected,
      Vector.empty)
    assertEquals(ModifierSelectionState.reconcile(Some(selected), context,
      Vector(first, second), "preview-1").selected, Vector.empty)
    assertEquals(ModifierSelectionState.reconcile(Some(selected), context,
      Vector(first), "preview-2").selected, Vector.empty)

  test("a walker start names a preview action only when the engine " +
      "registers it"):
    val commands = Vector[GameIntent](
      GameIntent.StartWalker("recover", Vector.empty),
      GameIntent.StartWalker("forge", Vector.empty),
      GameIntent.StartWalker("muster", Vector.empty),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "favor"))),
      GameIntent.StartWalker("travel", Vector.empty),
      GameIntent.StartWalker("search", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "search:world"))),
      GameIntent.StartWalker("play-facedown-adviser", Vector.empty),
      GameIntent.StartWalker("place-banner-resource", Vector.empty))
    // A registered key names its action whatever start arguments it carries
    // (Trade carries its own). Search and play-facedown-adviser choose their
    // modifiers inside the walk, and place-banner-resource is not
    // registered, so none of them names one.
    assertEquals(commands.flatMap(ModifierFlowDraft.action).map(_._1),
      Vector("recover", "forge", "muster", "trade", "travel"))
    assertEquals(ModifierFlowDraft.action(GameIntent.BeginRest), None)
    // An unregistered walker key is not swept in: the server would reject a
    // preview for an action it does not know.
    assertEquals(ModifierFlowDraft.action(GameIntent.StartWalker("teleport", Vector.empty)),
      None)

  test("a stale context clears the flow draft"):
    val response = MajorActionPreviewResponse(4, "travel", Vector(first),
      Vector.empty)
    val selected = ModifierSelectionState.reconcile(None, context,
      Vector(first), "preview").toggle(first)
    val draft = ModifierFlowDraft(
      command = GameIntent.StartWalker("travel", Vector.empty),
      baseParameters = Map.empty[String, String], preview = response,
      selection = selected)
    assertEquals(ModifierFlowDraft.reconcile(Some(draft),
      BoardSelectionContext("g", "p", 4)), Some(draft))
    assertEquals(ModifierFlowDraft.reconcile(Some(draft),
      BoardSelectionContext("g", "p", 5)), None)
    assertEquals(ModifierFlowDraft.reconcile(Some(draft),
      BoardSelectionContext("g", "other", 4)), None)

  private val startRecover = GameIntent.StartWalker("recover", Vector.empty)

  /** Recover's preview offering Catacombs, a modifier that comes from a card
    * at site `site:a`. `catacombs` is a readable stand-in id for this
    * fixture, not the catalog's id.
    */
  private val catacombs = PreviewModifier(
    "site-card:site:a:denizen:catacombs", "denizen.catacombs", "Catacombs")

  private val recoverPreview = MajorActionPreviewResponse(4, "recover",
    Vector(catacombs), Vector.empty)

  private def catacombsSelected: ModifierSelectionState =
    ModifierSelectionState.reconcile(None, context.copy(action = "recover"),
      recoverPreview.modifiers, "catacombs-preview").toggle(catacombs)

  test("Recover uses the generic ordered modifier flow but submits as a " +
      "walker command carrying Catacombs in its own modifiers field"):
    assertEquals(ModifierFlowDraft.action(startRecover),
      Some("recover" -> Map.empty[String, String]))
    val selection = catacombsSelected
    val invocation = ModifierInvocation("site-card", "catacombs",
      Some("site:a"), catacombs.handlerId)
    assertEquals(selection.invocations, Vector(invocation))
    // The Catacombs id lands inside StartWalker's own `modifiers`, not in a
    // WithModifiers wrapper -- GameApplicationService.majorAction does not
    // recognize StartWalker, so the legacy wrapper would reject it outright.
    val submitted = ModifierFlowDraft.submission(startRecover,
      selection.invocations)
    assertEquals(submitted, GameIntent.StartWalker("recover", Vector("denizen.catacombs")))

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

  test("a Trade walker start retains its start argument"):
    val invocation = ModifierInvocation("site-card", "catacombs",
      Some("site:a"), "denizen.some-power")
    Vector(
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("button", "favor"))))
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
