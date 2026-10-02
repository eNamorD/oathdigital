package oathdigital.frontend

import org.scalajs.dom

/** The generic panels for a parked choose-many and choose-amount decision.
  * Both render only what the projected query declares and name no action.
  */
private[frontend] object WalkerSelectionPanels:
  import ServerUiSupport.{button, element, text}

  /** The draft is the session's, paired with the decision by id; the query's
    * own case says which draft shape to expect, and a draft of another shape
    * (which `WalkerSelectionDraft.reconcile` never builds) renders nothing.
    */
  def render(surface: ParkedDecision.Surface.Selection,
      selection: Option[WalkerSelectionDraft], canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val draft = selection.filter(_.decisionId == surface.decision.decisionId)
    surface.query match
      case many: DecisionQueryState.ChooseMany => draft
        .collect { case value: WalkerChooseManyDraft => value }
        .foreach(renderMany(many, _, canControl, panel, controls))
      case amount: DecisionQueryState.ChooseAmount => draft
        .collect { case value: WalkerAmountDraft => value }
        .foreach(renderAmount(surface.decision, amount, _, canControl,
          panel, controls))

  private def renderMany(query: DecisionQueryState.ChooseMany,
      draft: WalkerChooseManyDraft,
      canControl: Boolean, panel: dom.Element, controls: TableControls): Unit =
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
    panel.appendChild(text("p", "walker-many-instruction",
      if query.minOptions == query.maxOptions then s"Choose ${query.minOptions}."
      else if query.minOptions == 0 && query.maxOptions >= query.options.size then
        "Choose any number."
      else if query.minOptions == 0 then s"Choose up to ${query.maxOptions}."
      else s"Choose ${query.minOptions} to ${query.maxOptions}."))
    val rows = element("div", "walker-many-options")
    query.options.foreach { option =>
      val item = WalkerPartitionDraft.itemId(option)
      val toggle = button(option.label, "walker-many-option")
      // A bank shows its suit symbol here too, as it does in a choose-one.
      if option.kind == "favor-bank" then
        toggle.insertBefore(RulesTextRenderer.glyph(s"suit-${option.id}"),
          toggle.firstChild)
      toggle.setAttribute("data-option-id", item)
      toggle.setAttribute("aria-pressed", draft.selected.contains(item).toString)
      toggle.disabled = !canControl
      toggle.onclick = _ => controls.stage(Draft.Selection(draft.toggle(item)))
      rows.appendChild(toggle)
    }
    panel.appendChild(rows)
    // A choose-many declares no confirm label, so the old
    // `confirmLabel.getOrElse("Confirm")` was always this literal.
    val confirm = button("Confirm", "walker-many-confirm")
    confirm.disabled = !canControl || !draft.canConfirm
    confirm.onclick = _ => draft.command.foreach(controls.submitCommand)
    panel.appendChild(confirm)

  /** A field the player types the amount into, with a list of the range
    * beside it (`AmountCombobox`). Nothing is staged on change: the field
    * holds the amount, and Confirm reads it and submits.
    */
  private def renderAmount(decision: WalkerDecisionState,
      query: DecisionQueryState.ChooseAmount, draft: WalkerAmountDraft,
      canControl: Boolean, panel: dom.Element, controls: TableControls): Unit =
    // The roll first, then what it came to, then the question about it: the
    // sacrifice question is only answerable by reading the attack.
    WalkerPanelSupport.rollFeedback(decision, panel)
    val heading = WalkerPanelSupport.decisionHeading(query)
    panel.appendChild(text("h2", "", heading))
    panel.appendChild(text("p", "walker-amount-instruction",
      if query.minAmount == query.maxAmount then s"Enter ${query.minAmount}."
      else s"Enter ${query.minAmount} to ${query.maxAmount}."))
    val confirm = button(query.confirmLabel, "walker-amount-confirm")
    val amount = AmountCombobox(
      "walker-amount-" + decision.decisionId.replaceAll("[^A-Za-z0-9_-]", "-"),
      heading, query.minAmount, query.maxAmount, draft.amount, canControl,
      changed = value => confirm.disabled = !canControl || value.isEmpty,
      submit = () => if !confirm.disabled then confirm.click())
    confirm.disabled = !canControl || !draft.canConfirm || amount.value.isEmpty
    confirm.onclick = _ => amount.value.flatMap(value => draft.choose(value).command)
      .foreach(controls.submitCommand)
    val row = element("div", "walker-amount-row")
    row.appendChild(amount.root)
    row.appendChild(confirm)
    panel.appendChild(row)
