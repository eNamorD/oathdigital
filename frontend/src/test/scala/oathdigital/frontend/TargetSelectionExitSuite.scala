package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{MajorActionPreviewResponse, PreviewModifier}
import org.scalajs.dom

/** A target selection offers one way out per destination: Cancel returns
  * to the action list, and Back appears only when there is a modifier stage
  * to go back to. Without one, "Back to actions" did what Cancel does.
  */
class TargetSelectionExitSuite extends munit.FunSuite:
  private val context = BoardSelectionContext("game", "red", 1L)
  private val travel = BoardTargetAction("travel", "Choose a destination", 1, 1,
    autoActivate = false, Vector(BoardTargetCandidate(
      BoardTargetRef.Site("site:woods"), "Deep Woods", Vector.empty)))
  private val projection = GameProjection("game", 1L, "act", Some("red"),
    Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
    Vector.empty, Vector.empty, Vector.empty, ready = true, completed = false,
    actionSelectionOpen = true, boardTargetActions = Vector(travel))
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None, playerId = "red")

  private def panel(modifiers: Vector[PreviewModifier]): dom.Element =
    val draft = ModifierFlowDraft(None, Some("travel"), Map.empty,
      MajorActionPreviewResponse(1L, "travel", modifiers, Vector.empty,
        Vector.empty),
      ModifierSelectionState.reconcile(None,
        ModifierSelectionContext("game", "red", 1L, "travel"), modifiers, "p"),
      ModifierFlowStage.Targets)
    val drafts = SessionDrafts.empty.copy(modifiers = Some(draft),
      boardTargets = Some(BoardTargetSelectionState(context, Vector(travel),
        Some("travel"), Set.empty)))
    ActionDecisionRenderer.actionsPanel(projection, presentation,
      ParkedDecision.Routed(None, None), canControl = true, drafts,
      new RecordingControls()).element

  private def labels(root: dom.Element, selector: String): Vector[String] =
    val found = root.querySelectorAll(selector)
    (0 until found.length).map(found(_).textContent).toVector

  test("a target selection with no modifier stage offers Cancel alone"):
    val root = panel(Vector.empty)
    assertEquals(labels(root, ".cancel-board-selection"), Vector("Cancel"))
    assertEquals(labels(root, ".back-board-selection"), Vector.empty)

  test("a target selection after a modifier stage also goes back to it"):
    val root = panel(Vector(PreviewModifier("card:horse", "brass-horse",
      "Brass Horse")))
    assertEquals(labels(root, ".cancel-board-selection"), Vector("Cancel"))
    assertEquals(labels(root, ".back-board-selection"),
      Vector("Back to modifiers"))
