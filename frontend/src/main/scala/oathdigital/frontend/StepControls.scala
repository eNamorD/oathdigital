package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import oathdigital.protocol.projection.StepOfferProjection
import org.scalajs.dom
import ServerUiSupport._

/** The Undo and Redo controls (undo design, "Controls"). The server offers
  * each step only to the player who may press it, so a control shows
  * whenever the projection carries its offer: in any phase, and while the
  * walker waits on another player. The labels are always "Undo" and "Redo";
  * each hover note names what the press would change. There is no keyboard
  * shortcut. */
private[frontend] object StepControls:
  def render(value: GameProjection, canControl: Boolean,
      submit: GameCommand => Unit): Option[dom.Element] =
    val controls = Vector(
      value.undo.map(control("Undo", "undo-action", _, GameCommand.Undo,
        canControl, submit)),
      value.redo.map(control("Redo", "redo-action", _, GameCommand.Redo,
        canControl, submit))).flatten
    Option.when(controls.nonEmpty) {
      val row = element("div", "step-controls")
      controls.foreach(row.appendChild)
      row
    }

  private def control(label: String, className: String,
      offer: StepOfferProjection, intent: GameCommand, canControl: Boolean,
      submit: GameCommand => Unit): dom.html.Button =
    val pressed = button(label, className)
    pressed.title = offer.note
    pressed.disabled = !canControl
    pressed.onclick = _ => submit(intent)
    pressed
