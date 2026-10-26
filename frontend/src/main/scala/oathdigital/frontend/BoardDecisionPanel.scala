package oathdigital.frontend

import org.scalajs.dom

/** The Action panel of a board decision (board decisions design, "Action
  * panel"). Top to bottom: the prompt, the summary line (a choose-one's
  * only until it has a pick, which its confirm button names), a repeated
  * choose-one's played picks under a rule labelled "Played", numbered, with
  * what each paid, the server's status facts, a row per
  * locked and picked option with its tally values (a choose-one's pick only
  * when it carries details the button does not), the tally totals, the options
  * with no place on the table, the open home's toggle, the Decide buttons under an unlabelled rule,
  * then a rule labelled "Decision" over Clear and the confirm button, which a
  * decision that picks at once leaves out. The table draws
  * the options themselves, through `BoardOptions`. A choose-one with no
  * option on the table to choose or ask about is a Continue screen: it says
  * nothing can be chosen and shows only its Decide buttons.
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

    // Nothing on the table to choose or ask about: the Decide buttons, such
    // as an empty choice's Continue, are the only answers.
    val boardChoices = query match
      case one: DecisionQueryState.ChooseOne => one.options.exists(option =>
        option.kind != "button" && option.state != "unavailable" &&
          option.state != "locked")
      case _ => true

    WalkerPanelSupport.rollFeedback(decision, panel)
    panel.appendChild(text("h2", "", WalkerPanelSupport.decisionHeading(query)))
    // The summary reads the answer so far. With nothing picked it is only
    // the instruction, set as a helper line so it does not compete with the
    // prompt above it. A choose-one's pick is named by the confirm button,
    // so once there is one the summary steps aside rather than repeat it.
    val pickedOne = query match
      case _: DecisionQueryState.ChooseOne =>
        draft.flatMap(_.picked.headOption)
      case _ => None
    val summary = query match
      case many: DecisionQueryState.ChooseMany =>
        val count = picks.size + many.options.count(_.state == "locked")
        Some(many.summary.fold(s"$count chosen")(_.of(count)))
      case _ => draft.filter(_.home.nonEmpty).map(open =>
        BoardOptionViews.variantPrompt(open.homeOptions))
    if pickedOne.isEmpty then
      panel.appendChild(summary.fold(text("p",
        "board-decision-summary board-decision-summary-empty",
        if boardChoices then "Choose on the board."
        else "Nothing can be chosen."))(text("p", "board-decision-summary", _)))

    // A repeated choose-one's answers so far, under their own rule and
    // numbered in play order, each with what it paid; then the facts the
    // server shows beside the decision, such as a Campaign's forces and dice
    // and what the picks paid in all.
    val played = query match
      case _: DecisionQueryState.ChooseOne => decision.answeredOptions
      case _ => Vector.empty
    if played.nonEmpty then
      panel.appendChild(text("p", "board-decision-rule-labelled", "Played"))
      val rows = element("ul", "board-decision-rows")
      played.zipWithIndex.foreach((option, index) => rows.appendChild(row(
        query, option, s"${index + 1}. ${label(option)}",
        "board-decision-row-played")))
      panel.appendChild(rows)
    WalkerPanelSupport.statusFacts(decision.status, panel)

    // Decided already: a choose-many's locked options. Then what this draft
    // holds.
    val locked = shown.filter(_.state == "locked")
    // A choose-one's confirm button already names its pick, so the pick's
    // row shows only when it carries more than the name.
    val chosen = draft.fold(Vector.empty[DecisionOptionState])(_.picked)
      .filter(option => query match
        case _: DecisionQueryState.ChooseOne => option.details.nonEmpty
        case _ => true)
    if locked.nonEmpty || chosen.nonEmpty then
      val rows = element("ul", "board-decision-rows")
      locked.foreach(option => rows.appendChild(row(query, option,
        s"Locked: ${label(option)}", "board-decision-row-locked")))
      chosen.foreach(option => rows.appendChild(row(query, option,
        label(option), "")))
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
        choice.disabled = !canControl
        choice.onclick = _ => controls.stage(Draft.Board(Some(
          open.choose(option))))
        option.reason match
          case Some(reason) if !option.choosable =>
            HoverTip.withhold(choice, reason)
          case Some(reason) => HoverTip.set(choice, reason)
          case None => if !option.choosable then choice.disabled = true
        toggle.appendChild(choice)
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
      toggle.disabled = !canControl
      toggle.onclick = _ => if askable then ask(option, toggle) else stage(option)
      val unavailable = found.exists(_.state == OptionState.Unavailable)
      found.flatMap(_.reason) match
        case Some(reason) if unavailable => HoverTip.withhold(toggle, reason)
        case Some(reason) => HoverTip.set(toggle, reason)
        case None => if unavailable then toggle.disabled = true
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
        WalkerPanelSupport.withholds(decide, option)
      }

    // A decision that picks at once has no draft to clear or confirm.
    if boardChoices && !BoardOptions.picksAtOnce(surface) then
      panel.appendChild(text("p", "board-decision-rule-labelled", "Decision"))
      if picks.nonEmpty || draft.exists(_.home.nonEmpty) then
        val clear = button("Clear", "board-decision-clear")
        clear.disabled = !canControl
        clear.onclick = _ => controls.stage(Draft.Board(draft.map(_.cleared)))
        panel.appendChild(clear)
      // The button says what pressing it does: a choose-many counts its
      // selection, a choose-one names its pick after the query's verb,
      // "Travel to Deep Woods", or after "Confirm" where it declares none.
      // A pick that replaces a card is already worded as the whole action,
      // "Discard Tavern Songs; replace it with Wayward Spirit", so it stands
      // alone.
      val confirm = button(query match
        case many: DecisionQueryState.ChooseMany =>
          WalkerPanelSupport.chooseManyConfirmLabel(many, picks.size)
        case one: DecisionQueryState.ChooseOne =>
          val verb = one.confirmVerb.getOrElse("Confirm")
          pickedOne.fold("Confirm")(option =>
            if option.replaces.nonEmpty then label(option)
            else s"$verb ${label(option)}"), "board-decision-confirm")
      confirm.disabled = !canControl || blocked.nonEmpty || current.isEmpty
      confirm.onclick = _ =>
        current.flatMap(_.command).foreach(controls.submitCommand)
      panel.appendChild(confirm)
      blocked.foreach(reason =>
        panel.appendChild(text("p", "board-decision-reason", reason)))

  /** One played, locked or picked option: its name as shown, then its tally
    * values and what the engine annotated on it, such as a battle plan's
    * price or what a played one paid. `kind` is the row's extra class.
    */
  private def row(query: DecisionQueryState, option: DecisionOptionState,
      name: String, kind: String): dom.Element =
    val key = BoardOptionViews.key(option)
    val home = BoardOptionViews.home(option)
    val item = element("li", s"board-decision-row $kind".trim)
    item.setAttribute("data-option-ref", home)
    item.appendChild(text("span", "", name))
    val values = (query match
      case many: DecisionQueryState.ChooseMany => many.tallies.flatMap(tally =>
        tally.values.find(value => BoardOptionViews.key(value.kind, value.id) ==
          key).map(value => s"${value.value} ${tally.unit}"))
      case _ => Vector.empty) ++ option.details
    if values.nonEmpty then item.appendChild(text("span",
      "board-decision-row-values", values.mkString(" · ")))
    BoardOptions.hoverSync(item, home)
    item
