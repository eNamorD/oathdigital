package oathdigital.frontend

import oathdigital.protocol.GameIntent
import org.scalajs.dom

/** The Challenge and Place Banner Resource start controls at the DOM. */
class BannerControlsSuite extends munit.FunSuite {
  private def projection(controls: Vector[String]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, controls, ready = true,
      completed = false, actionSelectionOpen = true)

  private def render(controls: Vector[String], canControl: Boolean = true)
      : (Vector[dom.html.Button], Vector[GameIntent]) = {
    var submitted = Vector.empty[GameIntent]
    val groups = new ServerUiSupport.ActionSections
    BannerControls.render(projection(controls), canControl, groups,
      command => submitted :+= command)
    val panel = dom.document.createElement("div")
    groups.appendTo(panel)
    val buttons = panel.querySelectorAll(".act-action").toVector
      .map(_.asInstanceOf[dom.html.Button])
    buttons.foreach(_.click())
    (buttons, submitted)
  }

  test("only the offered controls render and each starts its action") {
    val (buttons, submitted) = render(Vector("placeBannerResource", "beginChallenge"))
    assertEquals(buttons.map(_.textContent),
      Vector("Challenge (1 Supply)", "Place banner resources"))
    assertEquals(submitted, Vector[GameIntent](
      GameIntent.StartWalker("challenge", Vector.empty),
      GameIntent.StartWalker("place-banner-resource", Vector.empty)))
  }

  test("nothing renders when nothing is offered, and controls disable without control") {
    assertEquals(render(Vector.empty)._1, Vector.empty)
    assert(render(Vector("beginChallenge"), canControl = false)._1.forall(_.disabled))
  }

  test("Challenge starts through the modifier workflow and Place Banner Resource does not") {
    assertEquals(ModifierWorkflow.action(
      GameIntent.StartWalker("challenge", Vector.empty)),
      Some("challenge" -> Map.empty[String, String]))
    assertEquals(ModifierWorkflow.action(
      GameIntent.StartWalker("place-banner-resource", Vector.empty)), None)
  }
}
