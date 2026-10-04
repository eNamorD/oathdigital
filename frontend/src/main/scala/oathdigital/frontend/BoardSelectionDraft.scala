package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => GameCommand}

/** What a viewer has picked for a parked board decision and not yet
  * confirmed: option keys ([[BoardOptionViews.key]]) in the order picked.
  * Like the other walker drafts it knows only the projected query, so its
  * answer names only projected options. A choose-one holds at most one key.
  */
private[frontend] final case class BoardSelectionDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, keys: Vector[String]):

  /** After a click on `option`: a picked option is dropped; otherwise a
    * choose-one replaces its pick and a choose-many adds it. An option that
    * may not be chosen, or that the limits block, changes nothing.
    */
  def click(option: DecisionOptionState): BoardSelectionDraft =
    val key = BoardOptionViews.key(option)
    if keys.contains(key) then copy(keys = keys.filterNot(_ == key))
    else if option.state != "available" ||
        BoardOptionViews.blockedBy(query, keys, option).nonEmpty then this
    else query match
      case _: DecisionQueryState.ChooseOne => copy(keys = Vector(key))
      case _ => copy(keys = keys :+ key)

  def cleared: BoardSelectionDraft = copy(keys = Vector.empty)

  /** The picked options, in the order picked. */
  def picked: Vector[DecisionOptionState] =
    val shown = BoardOptionViews.options(query)
    keys.flatMap(key => shown.find(BoardOptionViews.key(_) == key))

  def blocker: Option[String] = BoardOptionViews.blocker(query, keys)

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
