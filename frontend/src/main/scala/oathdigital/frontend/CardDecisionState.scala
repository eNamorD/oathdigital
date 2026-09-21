package oathdigital.frontend

/** Starting-adviser selection. Search now uses walker partition decisions. */
final case class CardDecisionState(
    decisionId: String,
    cards: Vector[CardDetails],
    partition: PartitionDecisionState
) {
  import CardDecisionState.{discardKey, keepKey}

  def keep: Vector[CardDetails] = cardsIn(keepKey)
  def discard: Vector[CardDetails] = cardsIn(discardKey)
  def allIds: Vector[String] = partition.placed

  def arrangementValid(expected: Vector[CardDetails]): Boolean =
    partition.canConfirm && allIds.size == expected.size &&
      allIds.toSet == expected.map(_.cardId).toSet

  def moveToKeep(cardId: String): CardDecisionState =
    copy(partition = partition.moveTo(cardId, keepKey))

  def moveToDiscard(cardId: String): CardDecisionState =
    copy(partition = partition.moveTo(cardId, discardKey))

  private def cardsIn(key: String): Vector[CardDetails] =
    partition.itemsIn(key).flatMap(id => cards.find(_.cardId == id))
}

object CardDecisionState {
  private[frontend] val keepKey = "keep"
  private[frontend] val discardKey = "discard"

  def initial(decision: PendingCardDecision): CardDecisionState =
    CardDecisionState(decision.decisionId, decision.cards,
      PartitionDecisionState.allIn(
        Vector(PartitionSection(keepKey, "Keep", decision.keepMinimum,
            Some(decision.keepMaximum)),
          PartitionSection(discardKey, "Discard", 0)),
        decision.cards.map(_.cardId), discardKey))
}
