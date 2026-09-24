package oathdigital.frontend

import oathdigital.model.PlayerColor

import org.scalajs.dom

/** Cards waiting in the temporary hand are drawn in the action panel, so the
  * player who drew them can read them before anything asks them a question.
  */
class TemporaryHandPreviewSuite extends munit.FunSuite {
  private def panel(preview: Vector[CardDetails]): dom.Element =
    ActionDecisionRenderer.actionsPanel(
      GameProjection("game", 1L, "act", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
        Vector.empty, Vector.empty, Vector.empty, ready = true,
        completed = false, temporaryHandPreview = preview),
      ServerUiSupport.ViewerPresentation(showGameplayControls = true, None,
        None),
      new RecordingView("game", "red"))

  private val card = CardDetails("denizen:a", "denizen", "Hearth Guard",
    orientation = Some("face-up"))

  test("every card held in the hand is drawn") {
    val drawn = panel(Vector(card, card.copy(cardId = "denizen:b")))
      .querySelectorAll(".temporary-hand-preview .card-face").toVector
      .map(_.asInstanceOf[dom.Element].getAttribute("data-card-id"))
    assertEquals(drawn, Vector("denizen:a", "denizen:b"))
  }

  test("an empty hand draws no section at all") {
    assertEquals(panel(Vector.empty)
      .querySelectorAll(".temporary-hand-preview").length, 0)
  }
}
