package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, DistributeAmountWire,
  GameIntent => GameCommand}

/** One slot's identity and bounds, as the stepper sees them. */
private[frontend] final case class DistributeSlotBounds(item: String,
    minimum: Int, maximum: Int)

/** The stepper interaction, with no idea what it is distributing.
  *
  * Every move is clamped twice: to the slot's own bounds, and, when raising,
  * to what is left of the total. So no sequence of clicks builds an
  * over-allocated draft, and `canConfirm` is exactly `remaining == 0`.
  */
private[frontend] final case class DistributeDecisionState(
    slots: Vector[DistributeSlotBounds],
    total: Int,
    amounts: Map[String, Int]
) {
  def amount(item: String): Int = amounts.getOrElse(item, 0)

  def remaining: Int = total - slots.map(slot => amount(slot.item)).sum

  def increment(item: String): DistributeDecisionState = raise(item, 1)

  /** Raises a slot by as much as both its maximum and the remainder allow. */
  def fill(item: String): DistributeDecisionState = raise(item, Int.MaxValue)

  def decrement(item: String): DistributeDecisionState = lower(item, 1)

  /** Lowers a slot to its minimum. */
  def drain(item: String): DistributeDecisionState = lower(item, Int.MaxValue)

  def canConfirm: Boolean = remaining == 0

  private def raise(item: String, by: Int): DistributeDecisionState =
    slot(item).fold(this) { bounds =>
      val step = math.min(by, math.min(remaining,
        bounds.maximum - amount(item)))
      if (step <= 0) this else set(item, amount(item) + step)
    }

  private def lower(item: String, by: Int): DistributeDecisionState =
    slot(item).fold(this) { bounds =>
      val step = math.min(by, amount(item) - bounds.minimum)
      if (step <= 0) this else set(item, amount(item) - step)
    }

  private def slot(item: String): Option[DistributeSlotBounds] =
    slots.find(_.item == item)

  private def set(item: String, value: Int): DistributeDecisionState =
    copy(amounts = amounts.updated(item, value))
}

private[frontend] object DistributeDecisionState {
  /** Opens at `suggested` when the query carries one, else at the minimums. */
  def opened(slots: Vector[DistributeSlotBounds], total: Int,
      suggested: Option[Vector[Int]]): DistributeDecisionState =
    DistributeDecisionState(slots, total, slots.map(_.item).zip(
      suggested.filter(_.size == slots.size)
        .getOrElse(slots.map(_.minimum))).toMap)
}

/** A draft answer to a parked walker distribution. Like
  * [[WalkerPartitionDraft]], everything it knows comes from the projected
  * query, so the submitted `DistributeWire` names each projected slot once.
  */
private[frontend] final case class WalkerDistributeDraft(
    context: BoardSelectionContext,
    decisionId: String,
    query: DecisionQueryState,
    state: DistributeDecisionState
) {
  def increment(item: String): WalkerDistributeDraft = copy(state = state.increment(item))
  def decrement(item: String): WalkerDistributeDraft = copy(state = state.decrement(item))
  def fill(item: String): WalkerDistributeDraft = copy(state = state.fill(item))
  def drain(item: String): WalkerDistributeDraft = copy(state = state.drain(item))
  def canConfirm: Boolean = state.canConfirm

  def command: Option[GameCommand.ResolveWalker] =
    Option.when(canConfirm)(GameCommand.ResolveWalker(decisionId,
      DecisionAnswerWire.DistributeWire(query.slots.map(slot =>
        DistributeAmountWire(slot.option.kind, slot.option.id,
          state.amount(WalkerPartitionDraft.itemId(slot.option)))))))
}

private[frontend] object WalkerDistributeDraft {
  /** Adopts whichever parked decision projects a distribute query, and drops
    * the draft when the decision, the query or the board context changes.
    */
  def reconcile(previous: Option[WalkerDistributeDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[WalkerDistributeDraft] =
    decision.flatMap(parked => parked.query.filter(_.form == "distribute")
        .map(parked.decisionId -> _))
      .map { case (decisionId, query) =>
        previous.filter(draft => draft.context == context &&
            draft.decisionId == decisionId && draft.query == query)
          .getOrElse(WalkerDistributeDraft(context, decisionId, query,
            DistributeDecisionState.opened(query.slots.map(slot =>
              DistributeSlotBounds(WalkerPartitionDraft.itemId(slot.option),
                slot.minimum, slot.maximum)), query.total.getOrElse(0),
              Option.when(query.slots.nonEmpty &&
                query.slots.forall(_.suggested.nonEmpty))(
                query.slots.flatMap(_.suggested)))))
      }
}
