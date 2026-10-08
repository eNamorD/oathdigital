package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport._

/** The action owed to the viewer once the previous one ended (follow-up
  * actions design, "Projection and UI"): a start button, disabled with why
  * on hover when the action cannot start, and Skip. Drawn in place of the
  * action buttons; only the entry's player is offered the controls. The
  * start goes through the modifier flow, so modifiers are offered as for a
  * normal start.
  */
private[frontend] object FollowUpControls:
  def render(value: GameProjection, canControl: Boolean, panel: dom.Element,
      submit: GameCommand => Unit): Unit =
    value.followUp.filter(_ => value.legalControls.contains("beginFollowUp"))
      .foreach { followUp =>
        val start = button(s"${followUp.actionLabel} for no Supply · " +
          followUp.source, "act-action follow-up-action")
        start.disabled = !canControl || !followUp.startable
        followUp.reason.foreach(reason => start.setAttribute("title", reason))
        start.onclick = _ =>
          submit(GameCommand.StartWalker(followUp.action, Vector.empty))
        panel.appendChild(start)
        followUp.reason.foreach(reason =>
          panel.appendChild(text("p", "walker-choice-reason", reason)))
        val skip = button("Skip", "act-action follow-up-skip")
        skip.disabled = !canControl ||
          !value.legalControls.contains("skipFollowUp")
        skip.onclick = _ => submit(GameCommand.SkipFollowUp)
        panel.appendChild(skip)
        if followUp.queuedAfter.nonEmpty then
          panel.appendChild(text("p", "informational",
            s"Then: ${followUp.queuedAfter.mkString(", ")}"))
      }
