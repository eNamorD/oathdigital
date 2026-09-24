package oathdigital.frontend

import org.scalajs.dom

/** Recover's two controls, which are two steps and say so.
  *
  * The walker parks at a Roll and then, on a failed roll, at a
  * continue/stop Decide, so buying more dice and rolling them are separate
  * commands. The labels name that split rather than hiding it: the answer
  * buys the dice, the button rolls them.
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

  /** The answer buys dice and stops there -- the roll is the next click, so
    * the label promises dice rather than a result.
    */
  test("continuing says what it buys, not that it rolls") {
    val panel = render(choice)
    assertEquals(all(panel, ".recover-add").map(_.textContent),
      Vector("Add two dice (1 Supply)"))
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
