package oathdigital.frontend

import org.scalajs.dom

/** Recover's controls.
  *
  * The walker rolls as it walks and parks only on what it has to ask, so
  * one answer buys the next two dice and rolls them. The label says both,
  * because both happen on the click.
  */
class RecoverPanelSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def render(decision: WalkerDecisionState,
      supply: Int = 5): dom.Element = {
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderRecoverPanel(
      GameProjection("game", 1L, "act", Some("red"), Vector.empty, Vector.empty,
        Vector.empty, Vector.empty, ready = true, completed = false,
        activePlayerResources = Some(ActivePlayerResources(0, 0, 0, 0, 0, supply)),
        walkerDecision = Some(decision)),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None, None),
      canControl = true, panel, new RecordingView("game", "red"))
    panel
  }

  private val choice = WalkerDecisionState("recover",
    WalkerPanelSupport.recoverChoiceDecisionId, "decide",
    query = Some(DecisionQueryState("choose-one",
      Vector(DecisionOptionState("button", "continue", "Continue"),
        DecisionOptionState("button", "stop", "Stop")),
      Vector.empty, heading = Some("Recover"))))

  test("the roll button says it rolls") {
    val panel = render(WalkerDecisionState("recover", "recover.roll", "roll",
      pool = Some("recover")))
    assertEquals(all(panel, ".recover-roll").map(_.textContent),
      Vector("Roll the dice"))
  }

  /** One click buys the dice and throws them, so the label names both the
    * cost and the roll rather than promising a second button.
    */
  test("continuing says it buys dice and rolls them") {
    val panel = render(choice)
    assertEquals(all(panel, ".recover-add").map(_.textContent),
      Vector("Roll two more dice (1 Supply)"))
    assertEquals(all(panel, ".recover-stop").map(_.textContent),
      Vector("Stop Recover"))
  }

  test("without the supply to buy them, the control is refused") {
    assert(all(render(choice, supply = 0), ".recover-add").head
      .asInstanceOf[dom.html.Button].disabled)
    assert(!all(render(choice), ".recover-add").head
      .asInstanceOf[dom.html.Button].disabled)
  }
}
