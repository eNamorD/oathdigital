package oathdigital.frontend

import org.scalajs.dom

/** The Distribute panel. It sits in its own file rather than in
  * `ActionDecisionRenderer`, which has no headroom. Like the partition panel,
  * it renders only what the projected query declares: a row per slot, with
  * its label, a stepper and its maximum.
  */
private[frontend] object DistributePanelRenderer {
  import ServerUiSupport.{ViewerPresentation, button, element, text}

  val AllTooltip = "Shift+click: all"

  def render(value: GameProjection, presentation: ViewerPresentation,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
        .flatMap(decision => decision.query.filter(_.form == "distribute")
          .map(decision -> _))
        .foreach { case (decision, query) =>
      // A Campaign's placement is asked after the defense roll; the roll
      // comes first, as on every panel a roll belongs beside.
      WalkerPanelSupport.rollFeedback(decision, panel)
      panel.appendChild(text("h2", "", query.heading.getOrElse("Resolve decision")))
      ui.currentWalkerDistribution.filter(_.decisionId == decision.decisionId)
          .foreach { draft =>
        val rows = element("div", "distribute-slots")
        query.slots.foreach(slot => rows.appendChild(row(slot, draft, canControl, ui)))
        panel.appendChild(rows)
        panel.appendChild(text("p", "distribute-remaining",
          s"Remaining: ${draft.state.remaining}"))
        query.minTotal.filter(min => query.maxTotal.exists(min < _)).foreach(min =>
          panel.appendChild(text("p", "distribute-minimum",
            s"At least $min must be placed")))
        val confirm = button(query.confirmLabel.getOrElse("Confirm"),
          "distribute-confirm")
        confirm.disabled = !canControl || !draft.canConfirm
        confirm.onclick = _ => draft.command.foreach(ui.submitCommand)
        panel.appendChild(confirm)
      }
    }

  private def row(slot: DecisionSlotState, draft: WalkerDistributeDraft,
      canControl: Boolean, ui: ServerUiView): dom.Element = {
    val item = WalkerPartitionDraft.itemId(slot.option)
    val node = element("div", "distribute-slot")
    node.setAttribute("data-option-id", item)
    node.appendChild(slot.option.card.fold[dom.Element](
      text("span", "distribute-label", slot.option.label))(CardFace.render))
    node.appendChild(stepper("−", "distribute-decrement", canControl, ui,
      shift => if (shift) draft.drain(item) else draft.decrement(item)))
    node.appendChild(text("span", "distribute-amount",
      draft.state.amount(item).toString))
    node.appendChild(stepper("+", "distribute-increment", canControl, ui,
      shift => if (shift) draft.fill(item) else draft.increment(item)))
    node.appendChild(text("span", "distribute-maximum", s"max ${slot.maximum}"))
    node
  }

  private def stepper(label: String, className: String, canControl: Boolean,
      ui: ServerUiView, next: Boolean => WalkerDistributeDraft): dom.Element = {
    val control = button(label, className)
    control.setAttribute("title", AllTooltip)
    control.disabled = !canControl
    control.onclick = (event: dom.MouseEvent) => {
      ui.currentWalkerDistribution = Some(next(event.shiftKey))
      ui.rerender()
    }
    control
  }
}
