package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** Ending the Act refills the Supply track, so the button that ends it says
  * how much it returns. The promise is the Supply Rest returns before the
  * track's ceiling takes its cut, so a player reading it knows how much they
  * may still spend this Act for free.
  */
class RestButtonSuite extends munit.FunSuite {
  private def label(gain: Option[Int]): Option[String] =
    ActionDecisionRenderer.actionsPanel(
      GameProjection("game", 1L, "act", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
        Vector.empty, Vector.empty, Vector("beginRest"), ready = true,
        completed = false, actionSelectionOpen = true,
        restSupplyGain = gain),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None,
        None),
      new RecordingView("game", "red"))
      .querySelectorAll(".rest-action").toVector
      .map(_.asInstanceOf[dom.Element].textContent).headOption

  test("the button says what Rest returns") {
    assertEquals(label(Some(3)), Some("End Act and Rest (+3 Supply)"))
    assertEquals(label(Some(1)), Some("End Act and Rest (+1 Supply)"))
  }

  test("a Rest that returns nothing promises nothing") {
    assertEquals(label(Some(0)), Some("End Act and Rest"))
    assertEquals(label(None), Some("End Act and Rest"))
  }
}
