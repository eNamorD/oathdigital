package oathdigital.frontend

import org.scalajs.dom

/** Ending the Act refills the Supply track, so the button that ends it says
  * how much it returns. A Rest that returns nothing says nothing: a full
  * track is already reading 7/7 two panes away.
  */
class RestButtonSuite extends munit.FunSuite {
  private def label(gain: Option[Int]): Option[String] =
    ActionDecisionRenderer.actionsPanel(
      GameProjection("game", 1L, "act", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColorToken.Red)),
        Vector.empty, Vector.empty, Vector("beginRest"), ready = true,
        completed = false, actionSelectionOpen = true,
        restSupplyGain = gain),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None,
        None),
      new RecordingView("game", "red"))
      .querySelectorAll(".rest-action").toVector
      .map(_.asInstanceOf[dom.Element].textContent).headOption

  test("the button says what Rest returns") {
    assertEquals(label(Some(3)), Some("End Act and Rest (regain 3 Supply)"))
    assertEquals(label(Some(1)), Some("End Act and Rest (regain 1 Supply)"))
  }

  test("a Rest that returns nothing promises nothing") {
    assertEquals(label(Some(0)), Some("End Act and Rest"))
    assertEquals(label(None), Some("End Act and Rest"))
  }
}
