package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, WalkerStartArgWire}
import ServerUiSupport._

/** The Act-phase start controls for Muster and Trade. Each is offered by the
  * engine only while a source survives its preview, so this layer decides
  * nothing: it draws what `legalControls` names. The card is chosen at the
  * parked decision, not here.
  */
private[frontend] object EconomyControls {
  private final case class Control(control: String, kind: String,
      label: String, command: GameCommand)

  // `kind` reuses the board-target action kinds so the action panel groups
  // and orders these under the same Muster and Trade families as before.
  private val controls = Vector(
    Control("beginMuster", "muster", "Muster (1 Supply)",
      GameCommand.StartWalker("muster", Vector.empty)),
    Control("beginTradeFavor", "trade-favor", "Trade for favor (1 Supply)",
      GameCommand.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "favor")))),
    Control("beginTradeSecret", "trade-secret", "Trade for secrets (1 Supply)",
      GameCommand.StartWalker("trade", Vector.empty,
        Vector(WalkerStartArgWire("button", "secret")))))

  def render(value: GameProjection, canControl: Boolean,
      groups: ActionSections, submit: GameCommand => Unit): Unit =
    controls.filter(control => value.legalControls.contains(control.control))
      .foreach { control =>
        val node = button(control.label, s"act-action ${control.kind}-action")
        node.disabled = !canControl
        node.onclick = _ => submit(control.command)
        groups.appendKind(control.kind, node)
      }
}
