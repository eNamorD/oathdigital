package oathdigital.frontend

import org.scalajs.dom

/** The Action panel of a board decision (board decisions design, "Action
  * panel"). Top to bottom: the prompt, the summary line, a row per locked
  * and picked option with its tally values, the tally totals, the options
  * with no place on the table, the open home's toggle, the Decide buttons under an unlabelled rule,
  * then a rule labelled "Decision" over Clear and Confirm. The table draws
  * the options themselves, through `BoardOptions`.
  */
private[frontend] object BoardDecisionPanel:
  import ServerUiSupport.{button, element, text}

  def render(surface: ParkedDecision.Surface.BoardDecision,
      value: GameProjection, drafts: SessionDrafts, canControl: Boolean,
      panel: dom.Element, controls: TableControls): Unit =
    val decision = surface.decision
    val query = surface.query
    val draft = drafts.board.filter(_.decisionId == decision.decisionId)
    val picks = draft.fold(Vector.empty[String])(_.keys)
    val views = BoardOptionViews.of(decision, query, draft)
    val shown = BoardOptionViews.options(query)
    val stage = BoardOptions.stager(surface, drafts, canControl, controls)
    // An optional choose-many confirms the empty selection, so Confirm needs
    // a draft even before the first pick.
    val current = draft.orElse(drafts.context.map(context =>
      BoardSelectionDraft(context, decision.decisionId, query, Vector.empty)))
    val blocked = current.fold(BoardOptionViews.blocker(query, picks))(
      _.blocker)
    def label(option: DecisionOptionState): String =
      if option.kind == "player" then value.players
        .find(_.playerId == option.id).map(_.displayName).getOrElse(option.label)
      else option.label

    WalkerPanelSupport.rollFeedback(decision, panel)
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
    panel.appendChild(text("p", "board-decision-summary", query match
      case many: DecisionQueryState.ChooseMany =>
        val count = picks.size + many.options.count(_.state == "locked")
        many.summary.fold(s"$count chosen")(_.of(count))
      case _ => draft.flatMap(open => open.picked.headOption.map(label)
        .orElse(Option.when(open.home.nonEmpty)(
          BoardOptionViews.variantPrompt(open.homeOptions))))
        .getOrElse("Choose on the board.")))

    // Decided already: a choose-many's locked options, a choose-one's answers
    // so far. Then what this draft holds.
    val locked = (query match
      case _: DecisionQueryState.ChooseOne => decision.answeredOptions
      case _ => Vector.empty) ++ shown.filter(_.state == "locked")
    val chosen = draft.fold(Vector.empty[DecisionOptionState])(_.picked)
    if locked.nonEmpty || chosen.nonEmpty then
      val rows = element("ul", "board-decision-rows")
      locked.foreach(option => rows.appendChild(row(query, option,
        label(option), locked = true)))
      chosen.foreach(option => rows.appendChild(row(query, option,
        label(option), locked = false)))
      panel.appendChild(rows)

    // A home several options share asks which one in the panel: the
    // table draws the home once, and its options differ by one word.
    draft.filter(_.home.nonEmpty).foreach { open =>
      val toggle = element("div", "board-decision-variants")
      toggle.setAttribute("role", "group")
      open.homeOptions.foreach { option =>
        val choice = button(BoardOptionViews.variant(option),
          "board-decision-variant")
        choice.setAttribute("aria-pressed",
          open.keys.contains(BoardOptionViews.key(option)).toString)
        choice.disabled = !canControl || !option.choosable
        option.reason.foreach(choice.setAttribute("title", _))
        choice.onclick = _ => controls.stage(Draft.Board(Some(
          open.choose(option))))
        toggle.appendChild(choice)
        option.reason.foreach(reason => toggle.appendChild(
          text("span", "board-decision-variant-reason", reason)))
      }
      panel.appendChild(toggle)
    }

    query match
      case many: DecisionQueryState.ChooseMany => many.tallies.foreach { tally =>
        val sum = BoardOptionViews.total(many, tally, picks)
        val over = tally.limit.exists(sum > _)
        val line = element("p", if over then
          "board-decision-total board-decision-total-over"
          else "board-decision-total")
        line.appendChild(text("span", "", tally.label))
        line.appendChild(text("b", "",
          tally.limit.fold(sum.toString)(limit => s"$sum / $limit")))
        panel.appendChild(line)
      }
      case _ => ()

    // A choose-one's buttons answer at once; every other option the table
    // does not draw is picked here, like a board option.
    val decides = query match
      case _: DecisionQueryState.ChooseOne => shown.filter(_.kind == "button")
      case _ => Vector.empty
    val loose = shown.filter(option => !decides.contains(option) &&
      option.state != "locked" && !BoardHomes.has(value, option))
    // An askable option asks its granters instead of staging (consent
    // design, "Frontend").
    val ask = ConsentAsking.asker(decision.decisionId, value, canControl,
      controls)
    loose.foreach { option =>
      val key = BoardOptionViews.key(option)
      val found = views.get(key)
      val askable = found.exists(_.state == OptionState.Askable)
      val toggle = button(label(option), "board-decision-loose-option")
      if askable then toggle.classList.add("board-option-askable")
      toggle.setAttribute("data-option-ref", key)
      toggle.setAttribute("aria-pressed",
        found.exists(_.state == OptionState.Selected).toString)
      toggle.disabled = !canControl ||
        found.exists(_.state == OptionState.Unavailable)
      found.flatMap(_.reason).foreach(toggle.setAttribute("title", _))
      toggle.onclick = _ => if askable then ask(option, toggle) else stage(option)
      BoardOptions.hoverSync(toggle, key)
      panel.appendChild(toggle)
    }
    if decides.nonEmpty then
      panel.appendChild(element("hr", "board-decision-rule"))
      decides.foreach { option =>
        val decide = button(option.label, "board-decision-decide")
        decide.disabled = !canControl
        // An askable button asks its granters first (consent design,
        // "Frontend"), as the Conspiracy's Play does under Secret Police.
        val askable = option.state == "askable"
        if askable then decide.classList.add("board-option-askable")
        decide.onclick = _ =>
          if askable then ask(option, decide)
          else controls.submitCommand(
            WalkerPanelSupport.resolveChooseOneCommand(decision, option))
        panel.appendChild(decide)
        WalkerPanelSupport.withholds(decide, option, panel)
      }

    panel.appendChild(text("p", "board-decision-rule-labelled", "Decision"))
    if picks.nonEmpty || draft.exists(_.home.nonEmpty) then
      val clear = button("Clear", "board-decision-clear")
      clear.disabled = !canControl
      clear.onclick = _ => controls.stage(Draft.Board(draft.map(_.cleared)))
      panel.appendChild(clear)
    val confirm = button("Confirm", "board-decision-confirm")
    confirm.disabled = !canControl || blocked.nonEmpty || current.isEmpty
    confirm.onclick = _ =>
      current.flatMap(_.command).foreach(controls.submitCommand)
    panel.appendChild(confirm)
    blocked.foreach(reason =>
      panel.appendChild(text("p", "board-decision-reason", reason)))

  /** One locked or picked option: its name, then its tally values and what
    * the engine annotated on it, such as a battle plan's price.
    */
  private def row(query: DecisionQueryState, option: DecisionOptionState,
      name: String, locked: Boolean): dom.Element =
    val key = BoardOptionViews.key(option)
    val home = BoardOptionViews.home(option)
    val item = element("li", if locked then
      "board-decision-row board-decision-row-locked"
      else "board-decision-row")
    item.setAttribute("data-option-ref", home)
    item.appendChild(text("span", "",
      if locked then s"Locked: $name" else name))
    val values = (query match
      case many: DecisionQueryState.ChooseMany => many.tallies.flatMap(tally =>
        tally.values.find(value => BoardOptionViews.key(value.kind, value.id) ==
          key).map(value => s"${value.value} ${tally.unit}"))
      case _ => Vector.empty) ++ option.details
    if values.nonEmpty then item.appendChild(text("span",
      "board-decision-row-values", values.mkString(" · ")))
    BoardOptions.hoverSync(item, home)
    item
