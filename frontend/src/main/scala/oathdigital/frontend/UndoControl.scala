package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand}
import org.scalajs.dom
import ServerUiSupport._

/** The Undo control (undo design, "Controls"). The server offers the step
  * only to the player who may take it back, so the control shows whenever
  * the projection carries an offer: in any phase, and while the walker waits
  * on another player. The label is always "Undo"; the hover note names what
  * the press would take back. There is no keyboard shortcut. */
private[frontend] object UndoControl:
  def render(value: GameProjection, canControl: Boolean,
      submit: GameCommand => Unit): Option[dom.html.Button] =
    value.undo.map { offer =>
      val control = button("Undo", "undo-action")
      control.title = offer.note
      control.disabled = !canControl
      control.onclick = _ => submit(GameCommand.Undo)
      control
    }
