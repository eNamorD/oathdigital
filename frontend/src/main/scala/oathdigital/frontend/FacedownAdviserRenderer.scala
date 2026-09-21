package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

private[frontend] object FacedownAdviserRenderer {
  def render(draft: FacedownAdviserDraft, ui: ServerUiView): dom.Element = {
    import ui._
    val panel = element("section", "facedown-adviser-draft")
    panel.appendChild(text("h2", "", "Play facedown adviser"))
    if (draft.advisers.size > 1) {
      panel.appendChild(text("p", "decision-instruction",
        "Choose the facedown adviser to resolve."))
      draft.advisers.foreach { adviser =>
        val choose = button(adviser.card.name, "facedown-adviser-choice")
        choose.setAttribute("aria-pressed",
          draft.selectedCardId.contains(adviser.card.cardId).toString)
        choose.onclick = _ => chooseFacedownAdviser(adviser.card.cardId)
        panel.appendChild(choose)
      }
    }
    draft.selected.foreach { adviser =>
      panel.appendChild(cardDetailsPopover(adviser.card))
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
