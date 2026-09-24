package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => GameCommand}

/** A draft answer to a parked choose-many or choose-amount decision. Like
  * [[WalkerDistributeDraft]], everything it knows comes from the projected
  * query, so the submitted answer names only projected options or a
  * projected range.
  */
private[frontend] sealed trait WalkerSelectionDraft {
  def context: BoardSelectionContext
  def decisionId: String
  def query: DecisionQueryState
  def canConfirm: Boolean
  def command: Option[GameCommand.ResolveWalker]
}

private[frontend] final case class WalkerChooseManyDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, selected: Vector[String])
    extends WalkerSelectionDraft {
  private def minimum: Int = query.minimum.getOrElse(0)
  private def maximum: Int = query.maximum.getOrElse(0)

  /** Adds an unselected option while fewer than `maximum` are selected, and
    * removes a selected one; adding past the maximum changes nothing.
    */
  def toggle(item: String): WalkerChooseManyDraft =
    if (selected.contains(item)) copy(selected = selected.filterNot(_ == item))
    else if (selected.size < maximum &&
      query.options.exists(WalkerPartitionDraft.itemId(_) == item))
      copy(selected = selected :+ item)
    else this

  def canConfirm: Boolean = selected.size >= minimum && selected.size <= maximum

  def command: Option[GameCommand.ResolveWalker] = Option.when(canConfirm)(
    GameCommand.ResolveWalker(decisionId, DecisionAnswerWire.ChooseManyWire(
      query.options.filter(option =>
        selected.contains(WalkerPartitionDraft.itemId(option)))
        .map(option => DecisionOptionWire(option.kind, option.id)))))
}

private[frontend] final case class WalkerAmountDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, amount: Int) extends WalkerSelectionDraft {
  private def minimum: Int = query.minimum.getOrElse(0)
  private def maximum: Int = query.maximum.getOrElse(0)

  def choose(value: Int): WalkerAmountDraft =
    copy(amount = math.max(minimum, math.min(maximum, value)))

  def canConfirm: Boolean = amount >= minimum && amount <= maximum

  def command: Option[GameCommand.ResolveWalker] = Option.when(canConfirm)(
    GameCommand.ResolveWalker(decisionId,
      DecisionAnswerWire.ChooseAmountWire(amount)))
}

private[frontend] object WalkerSelectionDraft {
  /** Adopts whichever parked decision projects a choose-many or
    * choose-amount query, and drops the draft when the decision, the query or
    * the board context changes.
    */
  def reconcile(previous: Option[WalkerSelectionDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[WalkerSelectionDraft] =
    decision.flatMap(parked => parked.query
        .filter(query => query.form == "choose-many" ||
          query.form == "choose-amount")
        .map(parked.decisionId -> _))
      .map { case (decisionId, query) =>
        previous.filter(draft => draft.context == context &&
            draft.decisionId == decisionId && draft.query == query)
          .getOrElse(
            if (query.form == "choose-many")
              WalkerChooseManyDraft(context, decisionId, query, Vector.empty)
            else {
              // Where the question says to open, clamped to its own range so
              // a suggestion can never seed an illegal amount.
              val least = query.minimum.getOrElse(0)
              val most = query.maximum.getOrElse(least)
              WalkerAmountDraft(context, decisionId, query,
                query.suggested.fold(least)(value =>
                  math.max(least, math.min(most, value))))
            })
      }
}
