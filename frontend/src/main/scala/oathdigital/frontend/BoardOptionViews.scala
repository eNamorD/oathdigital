package oathdigital.frontend

/** How a board option is drawn; `classes` are its CSS classes. */
private[frontend] enum OptionState(val classes: Vector[String]):
  case Available extends OptionState(Vector("board-option"))
  case Selected extends OptionState(
    Vector("board-option", "board-option-selected"))
  case Locked extends OptionState(
    Vector("board-option", "board-option-locked"))
  case Unavailable extends OptionState(
    Vector("board-option", "board-option-unavailable"))
  case Askable extends OptionState(
    Vector("board-option", "board-option-askable"))

/** One option as the table draws it, and why it may not be chosen. */
private[frontend] final case class OptionView(option: DecisionOptionState,
    state: OptionState, reason: Option[String])

/** The parked board decision's options as the table and the panel draw
  * them (board decisions design, "Selection"). Pure: the projected query and
  * the draft decide everything.
  */
private[frontend] object BoardOptionViews:
  /** The option's identity on the page: its `data-option-ref`, and what a
    * draft records.
    */
  def key(kind: String, id: String): String = s"$kind:$id"
  def key(option: DecisionOptionState): String = key(option.kind, option.id)

  /** Where the table draws `option`, as the key the renderers hand
    * `BoardOptions`: an adviser play is drawn on its adviser slot, whichever
    * facing it names, and every other option on its own key.
    */
  def home(option: DecisionOptionState): String = option.kind match
    case "adviser-play" =>
      key("adviser-slot", BoardHomes.adviserSlot(option.id))
    case _ => key(option)

  /** The options of `query` drawn on `option`'s home, `option` included, in
    * the query's order. */
  def atHome(query: DecisionQueryState,
      option: DecisionOptionState): Vector[DecisionOptionState] =
    options(query).filter(home(_) == home(option))

  /** An option's word among the options on its home: its variant, or its
    * label when it has none. */
  def variant(option: DecisionOptionState): String =
    option.variant.getOrElse(option.label)

  /** What an open home asks before Confirm, from its options' words. */
  def variantPrompt(options: Vector[DecisionOptionState]): String =
    s"Choose one: ${options.map(variant).mkString(" or ")}."

  /** Every option the query shows, locked and unavailable ones included. */
  def options(query: DecisionQueryState): Vector[DecisionOptionState] =
    query match
      case one: DecisionQueryState.ChooseOne => one.options
      case many: DecisionQueryState.ChooseMany => many.options
      case _ => Vector.empty

  /** Each home's view: locked and unavailable as projected, then the draft's
    * picks, then what the draft's limits block. A choose-one's answers so
    * far (a battle-plan window's played plans) show locked. A home that
    * several options share is one view ([[shared]]).
    */
  def of(decision: WalkerDecisionState, query: DecisionQueryState,
      draft: Option[BoardSelectionDraft]): Map[String, OptionView] =
    val current = draft.filter(_.decisionId == decision.decisionId)
    val picks = current.fold(Vector.empty[String])(_.keys)
    val played = query match
      case _: DecisionQueryState.ChooseOne => decision.answeredOptions
        .map(option => OptionView(option, OptionState.Locked, None))
      case _ => Vector.empty
    val offered = options(query).map(view(query, picks, _))
      .groupBy(found => home(found.option)).map((at, views) =>
        at -> (if views.size == 1 then views.head
          else shared(views, current.flatMap(_.home).contains(at))))
    played.map(found => home(found.option) -> found).toMap ++ offered

  /** The one view of a home that several options share. It carries the
    * first available option, so a click opens the home
    * ([[BoardSelectionDraft.click]]). Selected while the draft has the home
    * open or holds one of its options; available while any option is;
    * otherwise unavailable with the first reason.
    */
  private def shared(views: Vector[OptionView], open: Boolean): OptionView =
    val lead = views.find(_.state == OptionState.Available)
      .getOrElse(views.head)
    if open || views.exists(_.state == OptionState.Selected) then
      lead.copy(state = OptionState.Selected, reason = None)
    else if lead.state == OptionState.Available then lead
    else views.head.copy(state = OptionState.Unavailable,
      reason = views.flatMap(_.reason).headOption)

  private def view(query: DecisionQueryState, picks: Vector[String],
      option: DecisionOptionState): OptionView = option.state match
    case "locked" => OptionView(option, OptionState.Locked, None)
    case "unavailable" =>
      OptionView(option, OptionState.Unavailable, option.reason)
    case "askable" => OptionView(option, OptionState.Askable, option.reason)
    case _ if picks.contains(key(option)) =>
      OptionView(option, OptionState.Selected, None)
    // Choosable, with its warning on hover.
    case "warning" => blockedBy(query, picks, option).fold(
      OptionView(option, OptionState.Available, option.reason))(reason =>
        OptionView(option, OptionState.Unavailable, Some(reason)))
    case _ => blockedBy(query, picks, option).fold(
      OptionView(option, OptionState.Available, None))(reason =>
        OptionView(option, OptionState.Unavailable, Some(reason)))

  /** Why adding `option` to `picks` would pass the maximum or a tally's
    * limit. A choose-one replaces its pick, so nothing blocks it.
    */
  def blockedBy(query: DecisionQueryState, picks: Vector[String],
      option: DecisionOptionState): Option[String] = query match
    case many: DecisionQueryState.ChooseMany
        if !picks.contains(key(option)) =>
      if picks.size >= many.maxOptions then
        Some(s"Choose at most ${many.maxOptions}.")
      else overLimit(many, picks :+ key(option))
    case _ => None

  /** The first tally whose limit `picks` pass, as "Needs 5 warbands; limit
    * 4".
    */
  private def overLimit(many: DecisionQueryState.ChooseMany,
      picks: Vector[String]): Option[String] =
    many.tallies.iterator.flatMap(tally => tally.limit.flatMap { limit =>
      val sum = total(many, tally, picks)
      Option.when(sum > limit)(s"Needs $sum ${tally.unit}; limit $limit")
    }).nextOption()

  /** A tally's total over `picks` and the locked options. */
  def total(many: DecisionQueryState.ChooseMany, tally: DecisionTallyState,
      picks: Vector[String]): Int =
    val counted = picks.toSet ++
      many.options.filter(_.state == "locked").map(key)
    tally.values.filter(value => counted(key(value.kind, value.id)))
      .map(_.value).sum

  /** Why Confirm may not submit `picks` yet, or `None` when it may. */
  def blocker(query: DecisionQueryState, picks: Vector[String])
      : Option[String] = query match
    case _: DecisionQueryState.ChooseOne =>
      Option.when(picks.isEmpty)("Choose an option first.")
    case many: DecisionQueryState.ChooseMany =>
      if picks.size < many.minOptions then
        Some(s"Choose at least ${many.minOptions}.")
      else overLimit(many, picks)
    case _ => Some("This decision is not answered on the board.")
