package oathdigital.frontend

import oathdigital.protocol.{GameIntent, WalkerStartArgWire}
import org.scalajs.dom

/** The Muster and Trade start controls at the DOM, under jsdom. */
class EconomyControlsSuite extends munit.FunSuite {
  private def projection(controls: Vector[String]): GameProjection =
    GameProjection("game", 9L, "act-action-selection", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, controls, ready = true,
      completed = false, actionSelectionOpen = true)

  private def render(controls: Vector[String], canControl: Boolean = true)
      : (Vector[dom.html.Button], Vector[GameIntent]) = {
    var submitted = Vector.empty[GameIntent]
    val groups = new ServerUiSupport.ActionSections
    EconomyControls.render(projection(controls), canControl, groups,
      command => submitted :+= command)
    val panel = dom.document.createElement("div")
    groups.appendTo(panel)
    val buttons = panel.querySelectorAll(".act-action").toVector
      .map(_.asInstanceOf[dom.html.Button])
    buttons.foreach(_.click())
    (buttons, submitted)
  }

  test("only the offered controls render, in Muster then Trade order") {
    val (buttons, _) = render(Vector("beginTradeSecret", "beginMuster"))
    assertEquals(buttons.map(_.textContent),
      Vector("Muster (1 Supply)", "Trade for secrets (1 Supply)"))
  }

  test("each control starts its action, with Trade's resource as the start selection") {
    val (_, submitted) = render(
      Vector("beginMuster", "beginTradeFavor", "beginTradeSecret"))
    assertEquals(submitted, Vector[GameIntent](
      GameIntent.StartWalker("muster", Vector.empty),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "favor"))),
      GameIntent.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "secret")))))
  }

  test("no control renders when none is offered, and all are disabled without control") {
    assertEquals(render(Vector.empty)._1, Vector.empty)
    val (buttons, _) = render(Vector("beginMuster"), canControl = false)
    assert(buttons.forall(_.disabled))
  }
}
