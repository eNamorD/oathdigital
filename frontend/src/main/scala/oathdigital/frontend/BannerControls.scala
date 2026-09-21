package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import ServerUiSupport._

/** The Act-phase start controls for Challenge and Place Banner Resource. Each
  * is offered by the engine only while its dry run starts, so this layer
  * decides nothing: it draws what `legalControls` names. The banner and the
  * amount are chosen at the parked decisions, not here.
  */
private[frontend] object BannerControls {
  private final case class Control(control: String, kind: String,
      label: String, command: GameCommand)

  private val controls = Vector(
    Control("beginChallenge", "challenge", "Challenge (1 Supply)",
      GameCommand.StartWalker("challenge", Vector.empty)),
    Control("placeBannerResource", "place-banner-resource",
      "Place banner resources (0 Supply)",
      GameCommand.StartWalker("place-banner-resource", Vector.empty)))

  def render(value: GameProjection, canControl: Boolean, groups: ActionSections,
      submit: GameCommand => Unit): Unit =
    controls.filter(control => value.legalControls.contains(control.control))
      .foreach { control =>
        val node = button(control.label, s"act-action ${control.kind}-action")
        node.disabled = !canControl
        node.onclick = _ => submit(control.command)
        groups.appendKind(control.kind, node)
      }
}
