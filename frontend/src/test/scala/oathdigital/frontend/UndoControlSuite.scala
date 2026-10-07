package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.protocol.projection.StepOfferProjection
import org.scalajs.dom

/** The Undo control (undo design, "Controls"): it shows only when the
  * projection offers the viewer a step to take back, names that step on
  * hover, and sends the Undo intent. */
class UndoControlSuite extends munit.FunSuite:
  private val players = Vector(
    GamePlayer("red", "Red", "Exile", PlayerColor.Red),
    GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue))
  private val offer = StepOfferProjection("Undo: Travelled to Buried Giant")

  private def projectionOffering(undo: Option[StepOfferProjection])
      : GameProjection =
    TestProjections.actingProjection(players = players).copy(undo = undo)

  /** The panel as `presentation` sees it, with `controls` recording what
    * it submits. */
  private def panelFor(value: GameProjection,
      presentation: ServerUiSupport.ViewerPresentation,
      controls: RecordingControls, canControl: Boolean = true): dom.Element =
    ActionDecisionRenderer.actionsPanel(value, presentation,
      ParkedDecision.Routed(None, None), canControl, SessionDrafts.empty,
      controls).element

  private def undoButtons(panel: dom.Element): Vector[dom.html.Button] =
    panel.querySelectorAll(".undo-action").toVector
      .map(_.asInstanceOf[dom.html.Button])

  private val waitingOnBlue = ServerUiSupport.ViewerPresentation(
    showGameplayControls = false, waitingForPlayerId = Some("blue"),
    waitingForDisplayName = Some("Blue"), playerId = "red")

  test("an offered step shows one Undo button whose hover note names it"):
    val panel = panelFor(projectionOffering(Some(offer)),
      ActionPanels.actingViewer("red"), new RecordingControls())
    val buttons = undoButtons(panel)
    assertEquals(buttons.map(_.textContent), Vector("Undo"))
    assertEquals(buttons.head.title, "Undo: Travelled to Buried Giant")
    assert(!buttons.head.disabled)

  test("pressing Undo sends the Undo intent"):
    val controls = new RecordingControls()
    val panel = panelFor(projectionOffering(Some(offer)),
      ActionPanels.actingViewer("red"), controls)
    undoButtons(panel).head.click()
    assertEquals(controls.submitted, Vector(Intent.Undo))

  test("no offer, no Undo button"):
    val panel = panelFor(projectionOffering(None),
      ActionPanels.actingViewer("red"), new RecordingControls())
    assertEquals(undoButtons(panel), Vector.empty)

  test("the actor waiting on another player's answer may still undo"):
    val panel = panelFor(projectionOffering(Some(offer)), waitingOnBlue,
      new RecordingControls())
    assertEquals(undoButtons(panel).map(_.textContent), Vector("Undo"))

  test("Undo sits directly under the status line"):
    val panel = panelFor(projectionOffering(Some(offer)), waitingOnBlue,
      new RecordingControls())
    assertEquals(panel.querySelector(".status").nextElementSibling
      .getAttribute("class"), "undo-action")

  test("while the connection is down the Undo button is disabled"):
    val panel = panelFor(projectionOffering(Some(offer)),
      ActionPanels.actingViewer("red"), new RecordingControls(),
      canControl = false)
    assert(undoButtons(panel).head.disabled)
