package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DecisionOptionWire,
  GameIntent => GameCommand}

/** A draft answer to a parked choose-many or choose-amount decision. Like
  * [[WalkerDistributeDraft]], everything it knows comes from the projected
  * query, so the submitted answer names only projected options or a
  * projected range.
  */
private[frontend] sealed trait WalkerSelectionDraft:
  def context: BoardSelectionContext
  def decisionId: String
  def query: DecisionQueryState
  def canConfirm: Boolean
  def command: Option[GameCommand.ResolveWalker]

private[frontend] final case class WalkerChooseManyDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState.ChooseMany, selected: Vector[String])
    extends WalkerSelectionDraft:
  private def minimum: Int = query.minOptions
  private def maximum: Int = query.maxOptions

  /** Adds an unselected option while fewer than `maximum` are selected, and
    * removes a selected one; adding past the maximum changes nothing.
    */
  def toggle(item: String): WalkerChooseManyDraft =
    if selected.contains(item) then copy(selected = selected.filterNot(_ == item))
    else if selected.size < maximum &&
      query.options.exists(WalkerPartitionDraft.itemId(_) == item) then
      copy(selected = selected :+ item)
    else this

  def canConfirm: Boolean = selected.size >= minimum && selected.size <= maximum

  def command: Option[GameCommand.ResolveWalker] = Option.when(canConfirm)(
    GameCommand.ResolveWalker(decisionId, DecisionAnswerWire.ChooseManyWire(
      query.options.filter(option =>
        selected.contains(WalkerPartitionDraft.itemId(option)))
        .map(option => DecisionOptionWire(option.kind, option.id)))))

private[frontend] final case class WalkerAmountDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState.ChooseAmount, amount: Int)
    extends WalkerSelectionDraft:
  private def minimum: Int = query.minAmount
  private def maximum: Int = query.maxAmount

  def choose(value: Int): WalkerAmountDraft =
    copy(amount = math.max(minimum, math.min(maximum, value)))

  def canConfirm: Boolean = amount >= minimum && amount <= maximum

  def command: Option[GameCommand.ResolveWalker] = Option.when(canConfirm)(
    GameCommand.ResolveWalker(decisionId,
      DecisionAnswerWire.ChooseAmountWire(amount)))

private[frontend] object WalkerSelectionDraft:
  /** Adopts whichever parked decision projects a choose-many or
    * choose-amount query, and drops the draft when the decision, the query or
    * the board context changes.
    */
  def reconcile(previous: Option[WalkerSelectionDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[WalkerSelectionDraft] =
    // The annotation is load-bearing: without it the two arms below infer
    // their least upper bound, `DecisionQueryState`, and the union that makes
    // the draft constructors typecheck is lost.
    val asked: Option[(String, ParkedDecision.SelectionForm)] =
      decision.flatMap(parked => parked.query match
        case Some(many: DecisionQueryState.ChooseMany) =>
          Some(parked.decisionId -> many)
        case Some(amount: DecisionQueryState.ChooseAmount) =>
          Some(parked.decisionId -> amount)
        case _ => None)
    asked.map { case (decisionId, query) =>
      previous.filter(draft => draft.context == context &&
          draft.decisionId == decisionId && draft.query == query)
        .getOrElse(query match
          case many: DecisionQueryState.ChooseMany =>
            WalkerChooseManyDraft(context, decisionId, many, Vector.empty)
          case amount: DecisionQueryState.ChooseAmount =>
            // Where the question says to open, clamped to its own range so a
            // suggestion can never seed an illegal amount.
            WalkerAmountDraft(context, decisionId, amount,
              amount.suggested.fold(amount.minAmount)(value =>
                math.max(amount.minAmount,
                  math.min(amount.maxAmount, value)))))
    }
