package oathdigital.frontend

import org.scalajs.dom

/** The generic choose-one panel, at the DOM: every projected option is one
  * control, and the consequences the engine annotated on it show beside it.
  * Runs under jsdom, like `PartitionPanelRenderSuite`.
  */
class WalkerChoicePanelRenderSuite extends munit.FunSuite {
  private val oak = DecisionOptionState("denizen", "d1", "Old Oak", None,
    Vector("1 Supply", "+2 warbands"))
  private val pub = DecisionOptionState("denizen", "d2", "Rowdy Pub")
  private val query = DecisionQueryState("choose-one", Vector(oak, pub),
    heading = Some("Choose a card to Muster from"))
  private val parked = WalkerDecisionState("muster", "muster.source", "decide",
    query = Some(query))
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)

  private def render(ui: RecordingView): dom.Element = {
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, ui)
    panel
  }

  private def all(root: dom.Element, selector: String): Vector[dom.Element] =
    root.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  test("each projected option is one control and its details show beside it") {
    val panel = render(new RecordingView("game", "red"))
    assertEquals(all(panel, ".walker-choice").map(_.textContent),
      Vector("Old Oak", "Rowdy Pub"))
    assertEquals(all(panel, ".walker-choice-details").map(_.textContent),
      Vector("1 Supply · +2 warbands"))
  }

  test("choosing an option submits the generic answer for its kind and id") {
    val ui = new RecordingView("game", "red")
    val panel = render(ui)
    all(panel, ".walker-choice").head.asInstanceOf[dom.html.Button].click()
    assertEquals(ui.submitted,
      Vector(WalkerPanelSupport.resolveChooseOneCommand(parked, oak)))
  }
}
