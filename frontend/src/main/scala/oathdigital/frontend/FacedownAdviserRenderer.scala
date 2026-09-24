package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

private[frontend] object FacedownAdviserRenderer {
  def render(draft: FacedownAdviserDraft, ui: ServerUiView): dom.Element = {
    import ui._
    val panel = element("section", "facedown-adviser-draft")
    panel.appendChild(text("h2", "", "Play facedown adviser"))
    val choosing = draft.advisers.size > 1
    if (choosing)
      panel.appendChild(text("p", "decision-instruction",
        "Choose the facedown adviser to resolve."))
    // The cards are the player's own, so they are drawn face-up whichever way
    // they lie on the board. Picking is a button of its own: a click on the
    // card means "show me this" everywhere else in the game.
    val options = element("div", "card-choices")
    draft.advisers.foreach { adviser =>
      val choice = element("div", "card-choice")
      choice.appendChild(CardFace.render(
        adviser.card.copy(orientation = Some("face-up"))))
      if (choosing) {
        val choose = button("Choose", "facedown-adviser-choice")
        choose.setAttribute("aria-label", s"Choose ${adviser.card.name}")
        choose.setAttribute("aria-pressed",
          draft.selectedCardId.contains(adviser.card.cardId).toString)
        choose.onclick = _ => chooseFacedownAdviser(adviser.card.cardId)
        choice.appendChild(choose)
      }
      options.appendChild(choice)
    }
    panel.appendChild(options)
    draft.selected.foreach { _ =>
      val start = button("Choose placement", "facedown-adviser-start")
      start.disabled = !canControl
      start.onclick = _ => draft.command.foreach(submitTargetCommand)
      panel.appendChild(start)
    }
    val cancel = button("Cancel action", "modifier-cancel")
    cancel.onclick = _ => cancelTargetAction(); panel.appendChild(cancel)
    panel
  }
}
