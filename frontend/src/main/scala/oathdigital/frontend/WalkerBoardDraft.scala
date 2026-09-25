package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, GameIntent => GameCommand}

/** A board draft: the option a viewer has picked on the board for a parked
  * choose-one decision and not yet confirmed. Like [[WalkerSelectionDraft]],
  * everything it knows comes from the projected query, so the submitted
  * answer names only a projected option. There is no empty draft: a viewer
  * who has picked nothing has `None`.
  */
private[frontend] final case class WalkerBoardDraft(
    context: BoardSelectionContext, decisionId: String,
    query: DecisionQueryState, option: DecisionOptionState):
  def command: GameCommand.ResolveWalker =
    GameCommand.ResolveWalker(decisionId,
      DecisionAnswerWire.ChooseOneWire(option.kind, option.id))

private[frontend] object WalkerBoardDraft:
  /** Keeps a draft while the projection still asks the same question in the
    * same board context, and drops it otherwise. A refresh that changes
    * nothing keeps the pick; a new sequence, decision or option set is a
    * different question.
    */
  def reconcile(previous: Option[WalkerBoardDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[WalkerBoardDraft] =
    previous.filter(draft => draft.context == context &&
      decision.exists(parked => parked.decisionId == draft.decisionId &&
        parked.query.contains(draft.query)))

  /** The draft after a click on `option`: picked when nothing or another
    * option was drafted, cleared when the same option was.
    */
  def toggle(previous: Option[WalkerBoardDraft],
      context: BoardSelectionContext, decisionId: String,
      query: DecisionQueryState, option: DecisionOptionState)
      : Option[WalkerBoardDraft] =
    if previous.exists(_.option == option) then None
    else Some(WalkerBoardDraft(context, decisionId, query, option))
