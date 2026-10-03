package oathdigital.frontend

import oathdigital.protocol.GameIntent
import org.scalajs.dom

/** What a Controls renderer drew into an action section: the buttons, and
  * the intents their clicks submitted.
  */
private[frontend] final case class RenderedControls(
    buttons: Vector[dom.html.Button], submitted: Vector[GameIntent])

private[frontend] object RenderedControls:
  /** Renders the controls `offered` with `renderer`, then clicks every
    * button it drew. `canControl` is whether the viewer may act.
    */
  def renderAndClickEach(offered: Vector[String], canControl: Boolean)(
      renderer: (GameProjection, Boolean, ServerUiSupport.ActionSections,
        GameIntent => Unit) => Unit): RenderedControls =
    var submitted = Vector.empty[GameIntent]
    val groups = new ServerUiSupport.ActionSections
    renderer(TestProjections.actingProjection(
      nextSequence = 9L, phase = "act-action-selection",
      legalControls = offered).copy(actionSelectionOpen = true),
      canControl, groups, command => submitted :+= command)
    val panel = dom.document.createElement("div")
    groups.appendTo(panel)
    val buttons = panel.querySelectorAll(".act-action").toVector
      .map(_.asInstanceOf[dom.html.Button])
    buttons.foreach(_.click())
    RenderedControls(buttons, submitted)
