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

  /** Every option the query shows, locked and unavailable ones included. */
  def options(query: DecisionQueryState): Vector[DecisionOptionState] =
    query match
      case one: DecisionQueryState.ChooseOne => one.options
      case many: DecisionQueryState.ChooseMany => many.options
      case _ => Vector.empty

  /** Each option by key: locked and unavailable as projected, then the
    * draft's picks, then what the draft's limits block. A choose-one's
    * answers so far (a battle-plan window's played plans) show locked.
    */
  def of(decision: WalkerDecisionState, query: DecisionQueryState,
      draft: Option[BoardSelectionDraft]): Map[String, OptionView] =
    val picks = draft.filter(_.decisionId == decision.decisionId)
      .fold(Vector.empty[String])(_.keys)
    val played = query match
      case _: DecisionQueryState.ChooseOne => decision.answeredOptions
        .map(option => OptionView(option, OptionState.Locked, None))
      case _ => Vector.empty
    (played ++ options(query).map(view(query, picks, _)))
      .map(found => key(found.option) -> found).toMap

  private def view(query: DecisionQueryState, picks: Vector[String],
      option: DecisionOptionState): OptionView = option.state match
    case "locked" => OptionView(option, OptionState.Locked, None)
    case "unavailable" =>
      OptionView(option, OptionState.Unavailable, option.reason)
    case _ if picks.contains(key(option)) =>
      OptionView(option, OptionState.Selected, None)
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
