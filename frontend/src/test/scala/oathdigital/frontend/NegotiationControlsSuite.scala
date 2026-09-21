package oathdigital.frontend

import oathdigital.protocol.GameIntent
import org.scalajs.dom

/** The Negotiation start control at the DOM. */
class NegotiationControlsSuite extends munit.FunSuite {
  private def projection(controls: Vector[String]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, controls, ready = true,
      completed = false, actionSelectionOpen = true)

  private def render(controls: Vector[String], canControl: Boolean = true)
      : (Vector[dom.html.Button], Vector[GameIntent]) = {
    var submitted = Vector.empty[GameIntent]
    val groups = new ServerUiSupport.ActionSections
    NegotiationControls.render(projection(controls), canControl, groups,
      command => submitted :+= command)
    val panel = dom.document.createElement("div")
    groups.appendTo(panel)
    val buttons = panel.querySelectorAll(".act-action").toVector
      .map(_.asInstanceOf[dom.html.Button])
    buttons.foreach(_.click())
    (buttons, submitted)
  }

  test("the control renders only when offered and starts Negotiation") {
    val (buttons, submitted) = render(Vector("beginNegotiation"))
    assertEquals(buttons.map(_.textContent), Vector("Negotiate (0 Supply)"))
    assertEquals(submitted, Vector[GameIntent](
      GameIntent.StartWalker("negotiation", Vector.empty)))
    assertEquals(render(Vector.empty)._1, Vector.empty)
    assert(render(Vector("beginNegotiation"), canControl = false)._1
      .forall(_.disabled))
  }

  test("Negotiation does not start through the modifier workflow") {
    assertEquals(ModifierWorkflow.action(
      GameIntent.StartWalker("negotiation", Vector.empty)), None)
  }
}
