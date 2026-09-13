package oathdigital.frontend

sealed trait CardDecisionStage
object CardDecisionStage {
  case object Arrange extends CardDecisionStage
  case object Resolve extends CardDecisionStage
}

/** Local-only interaction state. Only `finalResolution` becomes a command.
  *
  * The arrangement half runs on the shared [[PartitionDecisionState]] (Task
  * 5): Keep and Discard are that state's two declared sections, the keep
  * bounds are its minimum and maximum, and moving a card is the same code
  * that moves an option in a parked walker partition. Everything Search and
  * the starting adviser need beyond that -- the two-stage `Arrange`/`Resolve`
  * flow, the resolution choice and its replacement target, the stage reset a
  * returned card triggers -- stays here, on top.
  */
final case class CardDecisionState(
    decisionId: String,
    stage: CardDecisionStage,
    cards: Vector[CardDetails],
    partition: PartitionDecisionState,
    selectedResolution: Option[CardResolution] = None,
    selectedReplacement: Option[CardDetails] = None
) {
  import CardDecisionState.{discardKey, keepKey}

  def keep: Vector[CardDetails] = cardsIn(keepKey)
  def discard: Vector[CardDetails] = cardsIn(discardKey)
  def keepMinimum: Int = partition.section(keepKey).fold(0)(_.minRequired)
  def keepMaximum: Int =
    partition.section(keepKey).flatMap(_.maxAllowed).getOrElse(0)

  def allIds: Vector[String] = partition.placed
  def arrangementValid(expected: Vector[CardDetails]): Boolean =
    partition.canConfirm && allIds.size == expected.size &&
      allIds.toSet == expected.map(_.cardId).toSet

  def moveToKeep(cardId: String): CardDecisionState =
    rearranged(partition.moveTo(cardId, keepKey), this)

  /** Returning a card to Discard reopens the arrangement: a resolution
    * chosen for a card that is no longer kept means nothing.
    */
  def moveToDiscard(cardId: String): CardDecisionState =
    rearranged(partition.moveTo(cardId, discardKey),
      copy(stage = CardDecisionStage.Arrange))

  /** Discard ordering, which is a Discard-only concern: a kept card has no
    * position to slide.
    */
  def move(cardId: String, delta: Int): CardDecisionState =
    if (!partition.itemsIn(discardKey).contains(cardId)) this
    else copy(partition = partition.shift(cardId, delta))

  def arrangeDrop(cardId: String, beforeId: Option[String]): CardDecisionState =
    if (!partition.itemsIn(discardKey).contains(cardId)) this
    else copy(partition = partition.placeBefore(cardId, discardKey, beforeId))

  def chooseResolution(resolution: CardResolution): CardDecisionState =
    copy(selectedResolution = Some(resolution), selectedReplacement = None)

  def chooseReplacement(cardId: String): CardDecisionState =
    copy(selectedReplacement = selectedResolution.toVector
      .flatMap(_.replacementTargets).find(_.cardId == cardId))

  def resolutionValid: Boolean = selectedResolution.exists { resolution =>
    !resolution.replacementRequired || selectedReplacement.exists(selected =>
      resolution.replacementTargets.exists(_.cardId == selected.cardId))
  }

  private def cardsIn(key: String): Vector[CardDetails] =
    partition.itemsIn(key).flatMap(id => cards.find(_.cardId == id))

  /** A refused move leaves the state untouched, selection included; an
    * accepted one drops the resolution draft, which was chosen against the
    * arrangement that just changed.
    */
  private def rearranged(moved: PartitionDecisionState,
      accepted: CardDecisionState): CardDecisionState =
    if (moved == partition) this
    else accepted.copy(partition = moved, selectedResolution = None,
      selectedReplacement = None)
}

object CardDecisionState {
  private[frontend] val keepKey = "keep"
  private[frontend] val discardKey = "discard"

  /** Opens with every candidate in Discard, for Search and the starting
    * adviser alike. The projected keep bounds become the Keep section's
    * minimum and maximum, so a single-slot Keep swaps its occupant out
    * rather than refusing a second card.
    */
  def initial(decision: PendingCardDecision): CardDecisionState =
    CardDecisionState(decision.decisionId, CardDecisionStage.Arrange,
      decision.cards, PartitionDecisionState.allIn(
        Vector(PartitionSection(keepKey, "Keep", decision.keepMinimum,
            Some(decision.keepMaximum)),
          PartitionSection(discardKey, "Discard", 0)),
        decision.cards.map(_.cardId), discardKey))
}
