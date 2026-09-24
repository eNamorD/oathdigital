package oathdigital.frontend

import org.scalajs.dom

/** The generic panels for a parked choose-many and choose-amount decision.
  * Both render only what the projected query declares and name no action.
  */
private[frontend] object WalkerSelectionPanels {
  import ServerUiSupport.{ViewerPresentation, button, element, text}

  def render(value: GameProjection, presentation: ViewerPresentation,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit =
    value.walkerDecision.filter(_ => presentation.showGameplayControls)
        .flatMap(decision => decision.query.map(decision -> _))
        .foreach { case (decision, query) =>
      ui.currentWalkerSelection.filter(_.decisionId == decision.decisionId)
          .foreach {
        case draft: WalkerChooseManyDraft =>
          renderMany(query, draft, canControl, panel, ui)
        case draft: WalkerAmountDraft =>
          renderAmount(decision, query, draft, canControl, panel, ui)
      }
    }

  private def renderMany(query: DecisionQueryState, draft: WalkerChooseManyDraft,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit = {
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
    panel.appendChild(text("p", "walker-many-instruction",
      if (query.minimum == query.maximum) s"Choose ${query.minimum.getOrElse(0)}."
      else s"Choose ${query.minimum.getOrElse(0)} to ${query.maximum.getOrElse(0)}."))
    val rows = element("div", "walker-many-options")
    query.options.foreach { option =>
      val item = WalkerPartitionDraft.itemId(option)
      val toggle = button(option.label, "walker-many-option")
      toggle.setAttribute("data-option-id", item)
      toggle.setAttribute("aria-pressed", draft.selected.contains(item).toString)
      toggle.disabled = !canControl
      toggle.onclick = _ => {
        ui.currentWalkerSelection = Some(draft.toggle(item))
        ui.rerender()
      }
      rows.appendChild(toggle)
    }
    panel.appendChild(rows)
    val confirm = button(WalkerPanelSupport.partitionConfirmLabel(query),
      "walker-many-confirm")
    confirm.disabled = !canControl || !draft.canConfirm
    confirm.onclick = _ => draft.command.foreach(ui.submitCommand)
    panel.appendChild(confirm)
  }

  /** A dropdown over the range. The change handler updates the draft without a
    * rerender, so the open control keeps focus; the confirm handler reads the
    * latest draft at click time.
    */
  private def renderAmount(decision: WalkerDecisionState,
      query: DecisionQueryState, draft: WalkerAmountDraft,
      canControl: Boolean, panel: dom.Element, ui: ServerUiView): Unit = {
    // The roll first, then what it came to, then the question about it: the
    // sacrifice question is only answerable by reading the attack.
    WalkerPanelSupport.rollFeedback(decision, panel)
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
    val select = dom.document.createElement("select")
      .asInstanceOf[dom.html.Select]
    select.className = "walker-amount"
    select.setAttribute("aria-label", WalkerPanelSupport.decisionHeading(query))
    (query.minimum.getOrElse(0) to query.maximum.getOrElse(0)).foreach { n =>
      val choice = dom.document.createElement("option")
        .asInstanceOf[dom.html.Option]
      choice.value = n.toString
      choice.textContent = n.toString
      select.appendChild(choice)
    }
    select.value = draft.amount.toString
    select.disabled = !canControl
    select.onchange = _ => select.value.toIntOption.foreach(value =>
      ui.currentWalkerSelection = Some(draft.choose(value)))
    panel.appendChild(select)
    val confirm = button(query.confirmLabel.getOrElse("Confirm"),
      "walker-amount-confirm")
    confirm.disabled = !canControl || !draft.canConfirm
    confirm.onclick = _ => ui.currentWalkerSelection.collect {
      case latest: WalkerAmountDraft => latest
    }.flatMap(_.command).foreach(ui.submitCommand)
    panel.appendChild(confirm)
  }
}
