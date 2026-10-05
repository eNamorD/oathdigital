package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** The Action panel while a player picks which facedown adviser to play.
  * The advisers are lit on the player's board (`BoardOptions.facedown`);
  * the panel plays the pick by name, or cancels.
  */
private[frontend] object FacedownAdviserRenderer:
  def render(draft: FacedownAdviserDraft, canControl: Boolean,
      controls: ActionControls): dom.Element =
    val panel = element("section", "facedown-adviser-draft")
    panel.appendChild(text("h2", "", "Play facedown adviser"))
    draft.selected match
      case Some(adviser) =>
        val start = button(s"Play ${adviser.card.name}",
          "facedown-adviser-start")
        start.disabled = !canControl
        start.onclick = _ => draft.command.foreach(controls.submitTargetCommand)
        panel.appendChild(start)
      case None => panel.appendChild(text("p", "decision-instruction",
        "Choose a facedown adviser on your board."))
    val cancel = button("Cancel action", "modifier-cancel")
    cancel.onclick = _ => controls.cancelTargetAction()
    panel.appendChild(cancel)
    panel
