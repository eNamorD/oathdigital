package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** The asked ruler's prompt and the requester's waiting line (consent
  * design, user story steps 3 to 6). */
private[frontend] object ConsentPanels:
  private val answers = Vector("Never Allow" -> "never", "No" -> "no",
    "Yes" -> "yes", "Always Allow" -> "always")

  /** "Give Blue consent to ignore Narrow Pass and select Deep Woods?", or
    * for a payer, "Blue wants to send you to Coast. Consent to pay Toll
    * Roads and travel to Coast?" (catalog batch 8, slice 15). */
  def prompt(value: GameProjection, asked: ConsentPromptState): String =
    val requester = ConsentAsking.name(value, asked.requesterPlayerId)
    val powers = ConsentAsking.listed(asked.powers)
    if asked.paying then
      s"$requester wants to send you to ${asked.target}. Consent to pay " +
        s"$powers and travel to ${asked.target}?"
    else s"Give $requester consent to ignore $powers and select " +
      s"${asked.target}?"

  /** "Waiting for Red's consent…", or "Waiting for Blue's or Red's
    * consent…" while any one of them will do. */
  def waiting(value: GameProjection, wait: ConsentWaitingState): String =
    val names = wait.granterPlayerIds.map(id =>
      s"${ConsentAsking.name(value, id)}'s")
    s"Waiting for ${names.mkString(if wait.either then " or " else " and ")}" +
      " consent…"

  /** The prompt, when the viewer is asked. */
  def render(value: GameProjection, canControl: Boolean, panel: dom.Element,
      controls: TableControls): Unit =
    value.consentPrompt.foreach { asked =>
      val section = element("section", "consent-prompt")
      section.appendChild(text("p", "consent-question", prompt(value, asked)))
      val row = element("div", "consent-answers")
      answers.foreach { (label, choice) =>
        val answer = button(label, s"consent-answer consent-answer-$choice")
        answer.disabled = !canControl
        answer.onclick = _ =>
          controls.submitCommand(GameCommand.AnswerConsent(choice))
        row.appendChild(answer)
      }
      section.appendChild(row)
      section.appendChild(text("p", "consent-notice",
        "You can adjust your consent preferences by clicking on the " +
          s"${ConsentAsking.listed(asked.powers)} power."))
      panel.appendChild(section)
    }
