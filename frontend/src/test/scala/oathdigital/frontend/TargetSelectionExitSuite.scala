package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.PreviewModifier
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
  private val projection = TestProjections.actingProjection(
    players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)))
    .copy(actionSelectionOpen = true, boardTargetActions = Vector(travel))

  /** The action panel while red chooses a destination for a travel action
    * (the Targets stage of the modifier flow) whose preview offered
    * `modifiers`.
    */
  private def targetSelectionPanelAfterPreview(
      modifiers: Vector[PreviewModifier]): dom.Element =
    val drafts = SessionDrafts.empty.copy(
      modifiers = Some(ModifierFlowDrafts.travelTargetsDraft(1L, modifiers)),
      boardTargets = Some(BoardTargetSelectionState(context, Vector(travel),
        Some("travel"), Set.empty)))
    ActionPanels.actionsPanelFor(projection, drafts)

  private def labels(root: dom.Element, selector: String): Vector[String] =
    Dom.all(root, selector).map(_.textContent)

  test("a target selection with no modifier stage offers Cancel alone"):
    val root = targetSelectionPanelAfterPreview(Vector.empty)
    assertEquals(labels(root, ".cancel-board-selection"), Vector("Cancel"))
    assertEquals(labels(root, ".back-board-selection"), Vector.empty)

  test("a target selection after a modifier stage also goes back to it"):
    val root = targetSelectionPanelAfterPreview(
      Vector(PreviewModifier("card:horse", "brass-horse", "Brass Horse")))
    assertEquals(labels(root, ".cancel-board-selection"), Vector("Cancel"))
    assertEquals(labels(root, ".back-board-selection"),
      Vector("Back to modifiers"))
