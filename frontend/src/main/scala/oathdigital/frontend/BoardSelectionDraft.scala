package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => GameCommand}

/** What a viewer has picked for a parked board decision and not yet
  * confirmed: option keys ([[BoardOptionViews.key]]) in the order picked,
  * and the home the viewer opened when several options share it (an
  * adviser slot's faceup and facedown plays). Like the other walker drafts
  * it knows only the projected query, so its answer names only projected
  * options. A choose-one holds at most one key.
  */
private[frontend] final case class BoardSelectionDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, keys: Vector[String],
    home: Option[String] = None):

  /** After a click on `option`. On a choose-one, a home that several
    * options share opens ([[open]]). Otherwise a picked option is dropped, a
    * choose-one replaces its pick and closes any open home, and a
    * choose-many adds it. An option that may not be chosen, or that the
    * limits block, changes nothing.
    */
  def click(option: DecisionOptionState): BoardSelectionDraft =
    val key = BoardOptionViews.key(option)
    val siblings = BoardOptionViews.atHome(query, option)
    query match
      case _: DecisionQueryState.ChooseOne if siblings.size > 1 =>
        open(BoardOptionViews.home(option), siblings)
      case _ =>
        if keys.contains(key) then
          copy(keys = keys.filterNot(_ == key), home = None)
        else if option.state != "available" ||
            BoardOptionViews.blockedBy(query, keys, option).nonEmpty then this
        else query match
          case _: DecisionQueryState.ChooseOne =>
            copy(keys = Vector(key), home = None)
          case _ => copy(keys = keys :+ key)

  /** A click on a shared home: an open home closes; a closed one opens,
    * with its one available option picked or with no pick when there are
    * more. A home with nothing available stays closed.
    */
  private def open(at: String,
      siblings: Vector[DecisionOptionState]): BoardSelectionDraft =
    if home.contains(at) then copy(keys = Vector.empty, home = None)
    else siblings.filter(_.state == "available") match
      case Vector() => this
      case Vector(only) =>
        copy(keys = Vector(BoardOptionViews.key(only)), home = Some(at))
      case _ => copy(keys = Vector.empty, home = Some(at))

  /** The pick among the open home's options, from the panel's toggle. An
    * option of another home, or one that may not be chosen, changes nothing.
    */
  def choose(option: DecisionOptionState): BoardSelectionDraft =
    if option.state == "available" &&
        home.contains(BoardOptionViews.home(option)) then
      copy(keys = Vector(BoardOptionViews.key(option)))
    else this

  /** The open home's options, in the query's order. */
  def homeOptions: Vector[DecisionOptionState] = home.toVector.flatMap(at =>
    BoardOptionViews.options(query).filter(BoardOptionViews.home(_) == at))

  def cleared: BoardSelectionDraft = copy(keys = Vector.empty, home = None)

  /** The picked options, in the order picked. */
  def picked: Vector[DecisionOptionState] =
    val shown = BoardOptionViews.options(query)
    keys.flatMap(key => shown.find(BoardOptionViews.key(_) == key))

  def blocker: Option[String] =
    if home.nonEmpty && keys.isEmpty then
      Some(BoardOptionViews.variantPrompt(homeOptions))
    else BoardOptionViews.blocker(query, keys)

  def command: Option[GameCommand.ResolveWalker] =
    if blocker.nonEmpty then None
    else query match
      case _: DecisionQueryState.ChooseOne => picked.headOption.map(option =>
        GameCommand.ResolveWalker(decisionId,
          DecisionAnswerWire.ChooseOneWire(option.kind, option.id)))
      case _: DecisionQueryState.ChooseMany => Some(GameCommand.ResolveWalker(
        decisionId, DecisionAnswerWire.ChooseManyWire(picked.map(option =>
          DecisionOptionWire(option.kind, option.id)))))
      case _ => None

private[frontend] object BoardSelectionDraft:
  /** Keeps a draft while the projection asks the same question in the same
    * context, and drops it otherwise: a new sequence, decision or option set
    * is a different question.
    */
  def reconcile(previous: Option[BoardSelectionDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[BoardSelectionDraft] =
    previous.filter(draft => draft.context == context &&
      decision.exists(parked => parked.decisionId == draft.decisionId &&
        parked.query.contains(draft.query)))

  /** The facedown-adviser pick's decision id, spelled as the engine's
    * `CardPlayProcedure.facedownAdviserDecisionId`. */
  val facedownAdviserDecisionId = "cardplay.facedown-adviser"

  /** The draft a fresh position opens `decision` with: the facedown-adviser
    * pick with one adviser to play has it picked, as the pick before the
    * walker did. Every other decision opens with nothing picked.
    */
  def preselected(context: BoardSelectionContext,
      decision: Option[WalkerDecisionState]): Option[BoardSelectionDraft] =
    for
      parked <- decision.filter(_.decisionId == facedownAdviserDecisionId)
      query <- parked.query.collect { case one: DecisionQueryState.ChooseOne =>
        one }
      available = query.options.filter(_.state == "available")
      only <- Option.when(available.size == 1)(available.head)
    yield BoardSelectionDraft(context, parked.decisionId, query,
      Vector(BoardOptionViews.key(only)))

  /** The draft after a click on `option`, starting a fresh one when
    * `previous` is empty or answers another question.
    */
  def click(previous: Option[BoardSelectionDraft],
      context: BoardSelectionContext, decisionId: String,
      query: DecisionQueryState, option: DecisionOptionState)
      : BoardSelectionDraft =
    previous.filter(draft => draft.context == context &&
        draft.decisionId == decisionId && draft.query == query)
      .getOrElse(BoardSelectionDraft(context, decisionId, query, Vector.empty))
      .click(option)
