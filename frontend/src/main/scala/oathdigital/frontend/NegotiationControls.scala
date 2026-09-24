package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import ServerUiSupport._

/** The Act-phase start control for Negotiation. The engine offers it only
  * while its dry run starts, so this layer decides nothing: it draws what
  * `legalControls` names. The negotiators are chosen at the parked decision.
  */
private[frontend] object NegotiationControls {
  def render(value: GameProjection, canControl: Boolean, groups: ActionSections,
      submit: GameCommand => Unit): Unit =
    if (value.legalControls.contains("beginNegotiation")) {
      val node = button("Negotiate", "act-action negotiation-action")
      node.disabled = !canControl
      node.onclick = _ => submit(GameCommand.StartWalker("negotiation", Vector.empty))
      groups.appendKind("negotiation", node)
    }
}
