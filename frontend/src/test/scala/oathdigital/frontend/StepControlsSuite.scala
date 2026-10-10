package oathdigital.frontend

import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import oathdigital.protocol.projection.StepOfferProjection
import org.scalajs.dom

/** The Undo and Redo controls (undo design, "Controls"): each shows only
  * when the projection offers the viewer that step, names it on hover, and
  * sends its intent. Both share one row under the status line. */
class StepControlsSuite extends munit.FunSuite:
  private val players = Vector(
    GamePlayer("red", "Red", "Exile", PlayerColor.Red),
    GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue))
  private val undoOffer = StepOfferProjection("Undo: Travelled to Buried Giant")
  private val redoOffer = StepOfferProjection("Redo: Travelled to Great Spire")

  private def projectionOffering(undo: Option[StepOfferProjection],
      redo: Option[StepOfferProjection] = None): GameProjection =
    TestProjections.actingProjection(players = players)
      .copy(undo = undo, redo = redo)

  /** The panel as `presentation` sees it, with `controls` recording what
    * it submits. */
  private def panelFor(value: GameProjection,
      presentation: ServerUiSupport.ViewerPresentation,
      controls: RecordingControls, canControl: Boolean = true): dom.Element =
    ActionDecisionRenderer.actionsPanel(value, presentation,
      ParkedDecision.Routed(None, None), canControl, SessionDrafts.empty,
      controls)

  private def buttonsMatching(panel: dom.Element, selector: String)
      : Vector[dom.html.Button] =
    panel.querySelectorAll(selector).toVector
      .map(_.asInstanceOf[dom.html.Button])

  private def undoButtons(panel: dom.Element) =
    buttonsMatching(panel, ".undo-action")
  private def redoButtons(panel: dom.Element) =
    buttonsMatching(panel, ".redo-action")

  private val waitingOnBlue = ServerUiSupport.ViewerPresentation(
    showGameplayControls = false, waitingForPlayerId = Some("blue"),
    waitingForDisplayName = Some("Blue"), playerId = "red")

  test("an offered step shows one Undo button whose hover note names it"):
    val panel = panelFor(projectionOffering(Some(undoOffer)),
      ActionPanels.actingViewer("red"), new RecordingControls())
    val buttons = undoButtons(panel)
    assertEquals(buttons.map(_.textContent), Vector("Undo"))
    assertEquals(buttons.head.title, "Undo: Travelled to Buried Giant")
    assert(!buttons.head.disabled)
    assertEquals(redoButtons(panel), Vector.empty)

  test("pressing Undo sends the Undo intent"):
    val controls = new RecordingControls()
    val panel = panelFor(projectionOffering(Some(undoOffer)),
      ActionPanels.actingViewer("red"), controls)
    undoButtons(panel).head.click()
    assertEquals(controls.submitted, Vector(Intent.Undo))

  test("an offered redo shows one Redo button whose hover note names it"):
    val panel = panelFor(projectionOffering(None, Some(redoOffer)),
      ActionPanels.actingViewer("red"), new RecordingControls())
    val buttons = redoButtons(panel)
    assertEquals(buttons.map(_.textContent), Vector("Redo"))
    assertEquals(buttons.head.title, "Redo: Travelled to Great Spire")
    assert(!buttons.head.disabled)
    assertEquals(undoButtons(panel), Vector.empty)

  test("pressing Redo sends the Redo intent"):
    val controls = new RecordingControls()
    val panel = panelFor(projectionOffering(None, Some(redoOffer)),
      ActionPanels.actingViewer("red"), controls)
    redoButtons(panel).head.click()
    assertEquals(controls.submitted, Vector(Intent.Redo))

  test("no offer, no row"):
    val panel = panelFor(projectionOffering(None),
      ActionPanels.actingViewer("red"), new RecordingControls())
    assert(panel.querySelector(".step-controls") == null, "no row expected")

  test("the actor waiting on another player's answer may still undo"):
    val panel = panelFor(projectionOffering(Some(undoOffer)), waitingOnBlue,
      new RecordingControls())
    assertEquals(undoButtons(panel).map(_.textContent), Vector("Undo"))

  test("Undo and Redo share one row directly under the status line"):
    val panel = panelFor(projectionOffering(Some(undoOffer), Some(redoOffer)),
      waitingOnBlue, new RecordingControls())
    val row = panel.querySelector(".status").nextElementSibling
    assertEquals(row.getAttribute("class"), "step-controls")
    assertEquals((0 until row.children.length).map(row.children(_).textContent)
      .toVector, Vector("Undo", "Redo"))

  test("while the connection is down both buttons are disabled"):
    val panel = panelFor(projectionOffering(Some(undoOffer), Some(redoOffer)),
      ActionPanels.actingViewer("red"), new RecordingControls(),
      canControl = false)
    assert(undoButtons(panel).head.disabled)
    assert(redoButtons(panel).head.disabled)
