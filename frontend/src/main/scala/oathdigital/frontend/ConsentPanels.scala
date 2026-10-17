package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport.{button, element, text}

/** The asked ruler's prompt and the requester's waiting line (consent
  * design, user story steps 3 to 6). */
private[frontend] object ConsentPanels:
  private val answers = Vector("Never Allow" -> "never", "No" -> "no",
    "Yes" -> "yes", "Always Allow" -> "always")
  /** An automatic power's answers (catalog batch 8, item 10b). */
  private val deciding = Vector("Always deactivate" -> "never",
    "Deactivate" -> "no", "Let it work" -> "yes",
    "Always let it work" -> "always")

  /** "Give Blue consent to ignore Narrow Pass and select Deep Woods?"; for
    * a payer, "Blue wants to send you to Coast. Consent to pay Toll Roads
    * and travel to Coast?" (catalog batch 8, slice 15); and for an
    * automatic power, "Use City Wall to deactivate Blue's Tyrant?"
    * (item 10b). */
  def prompt(value: GameProjection, asked: ConsentPromptState): String =
    val requester = ConsentAsking.name(value, asked.requesterPlayerId)
    val powers = ConsentAsking.listed(asked.powers)
    asked.use.fold(asking(asked, requester, powers))(card =>
      s"Use $powers to deactivate $requester's $card?")

  private def asking(asked: ConsentPromptState, requester: String,
      powers: String): String =
    if asked.paying then
      s"$requester wants to send you to ${asked.target}. Consent to pay " +
        s"$powers and travel to ${asked.target}?"
    else s"Give $requester consent to ignore $powers and select " +
      s"${asked.target}?"

  /** "Waiting for Red's consent…", or "Waiting for Blue's or Red's
    * consent…" while any one of them will do; for an automatic power,
    * "Waiting for Red's decision on Tyrant…" (catalog batch 8, item
    * 10b). */
  def waiting(value: GameProjection, wait: ConsentWaitingState): String =
    val names = wait.granterPlayerIds.map(id =>
      s"${ConsentAsking.name(value, id)}'s")
    val awaited = wait.use.fold(" consent…")(card => s" decision on $card…")
    s"Waiting for ${names.mkString(if wait.either then " or " else " and ")}" +
      awaited

  /** The prompt, when the viewer is asked. */
  def render(value: GameProjection, canControl: Boolean, panel: dom.Element,
      controls: TableControls): Unit =
    value.consentPrompt.foreach { asked =>
      val section = element("section", "consent-prompt")
      section.appendChild(text("p", "consent-question", prompt(value, asked)))
      val row = element("div", "consent-answers")
      val choices = if asked.use.nonEmpty then deciding else answers
      choices.foreach { (label, choice) =>
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
