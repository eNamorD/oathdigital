package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, WalkerStartArgWire}
import org.scalajs.dom

/** One button per legal phase power, in Act, Wake and Rest alike. */
object PhasePowerButtons {
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

  def render(value: GameProjection, canControl: Boolean, panel: dom.Element,
      submit: GameCommand => Unit): Unit =
    actions(value).foreach { case (power, command) =>
      val control = dom.document.createElement("button")
        .asInstanceOf[dom.html.Button]
      control.className = "phase-power"
      control.textContent = power.name
      control.title = power.rulesText
      control.setAttribute("data-power-id", power.powerId)
      control.disabled = !canControl
      control.onclick = _ => submit(command)
      panel.appendChild(control)
    }
}
