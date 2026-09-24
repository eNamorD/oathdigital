package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, WalkerStartArgWire}
import org.scalajs.dom

/** One button per legal phase power, in Act, Wake and Rest alike. */
private[frontend] object PhasePowerButtons {
  /** Each projected power whose `usePower` control is legal, with the
    * command its button submits.
    */
  def actions(value: GameProjection): Vector[(PhasePowerState, GameCommand)] =
    value.phasePowers.filter(power => value.legalControls.contains(
      s"usePower:${power.powerId}:${power.source.id}")).map(power =>
      power -> GameCommand.UsePower(power.powerId,
        WalkerStartArgWire(power.source.kind, power.source.id)))

  def showsFinishRest(value: GameProjection): Boolean =
    value.phase == "rest" && value.legalControls.contains("finishRest")

  /** The kind every power button is filed under. `ServerUiSupport`
    * categorises it as a minor action, which is what using a power's
    * "Action:" is.
    */
  private[frontend] val ActionKind: String = "power-action"

  def buttons(value: GameProjection, canControl: Boolean,
      submit: GameCommand => Unit): Vector[dom.html.Button] =
    actions(value).map { case (power, command) =>
      val control = dom.document.createElement("button")
        .asInstanceOf[dom.html.Button]
      control.className = "phase-power"
      control.textContent = power.name
      control.title = power.rulesText
      control.setAttribute("data-power-id", power.powerId)
      control.disabled = !canControl
      control.onclick = _ => submit(command)
      control
    }

  def render(value: GameProjection, canControl: Boolean, panel: dom.Element,
      submit: GameCommand => Unit): Unit =
    buttons(value, canControl, submit).foreach(panel.appendChild)

  /** The Act panel's route: the buttons join the listed actions instead of
    * standing loose next to the one that ends the Act.
    */
  def appendTo(value: GameProjection, canControl: Boolean,
      groups: ServerUiSupport.ActionSections,
      submit: GameCommand => Unit): Unit =
    buttons(value, canControl, submit).foreach(groups.appendKind(ActionKind, _))
}
