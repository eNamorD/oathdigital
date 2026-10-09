package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** The asked ruler's prompt and the requester's waiting line (consent
  * design, user story steps 3 to 6). */
private[frontend] object ConsentPanels:
  private val answers = Vector("Never Allow" -> "never", "No" -> "no",
    "Yes" -> "yes", "Always Allow" -> "always")

  /** "Give Blue consent to ignore Narrow Pass and select Deep Woods?" */
  def prompt(value: GameProjection, asked: ConsentPromptState): String =
    s"Give ${ConsentAsking.name(value, asked.requesterPlayerId)} consent to " +
      s"ignore ${ConsentAsking.listed(asked.powers)} and select " +
      s"${asked.target}?"

  /** "Waiting for Red's consent…" */
  def waiting(value: GameProjection, wait: ConsentWaitingState): String =
    val names = wait.granterPlayerIds.map(id =>
      s"${ConsentAsking.name(value, id)}'s")
    s"Waiting for ${names.mkString(" and ")} consent…"

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
