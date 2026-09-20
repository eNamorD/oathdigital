package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import ServerUiSupport._

/** The Act-phase start control for Campaign. The engine offers it only while
  * its dry run starts, so this layer decides nothing. The kind, the targets,
  * the force and every later choice are parked decisions.
  */
private[frontend] object CampaignControls {
  def render(value: GameProjection, canControl: Boolean, groups: ActionSections,
      submit: GameCommand => Unit): Unit =
    if (value.legalControls.contains("beginCampaign")) {
      val node = button("Campaign (2 Supply)", "act-action campaign-action")
      node.disabled = !canControl
      node.onclick = _ => submit(GameCommand.StartWalker("campaign", Vector.empty))
      groups.appendKind("campaign", node)
    }
}
