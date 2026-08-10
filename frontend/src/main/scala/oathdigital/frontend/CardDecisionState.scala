package oathdigital.frontend

sealed trait CardDecisionStage
object CardDecisionStage {
  case object Arrange extends CardDecisionStage
  case object Resolve extends CardDecisionStage
}

/** Local-only interaction state. Only `finalResolution` becomes a command. */
final case class CardDecisionState(
    decisionId: String,
    stage: CardDecisionStage,
    keep: Vector[CardDetails],
    discard: Vector[CardDetails],
    keepMinimum: Int,
    keepMaximum: Int,
    selectedResolution: Option[CardResolution] = None,
    selectedReplacement: Option[CardDetails] = None
) {
  def allIds: Vector[String] = (keep ++ discard).map(_.cardId)
  def arrangementValid(expected: Vector[CardDetails]): Boolean =
    keep.size >= keepMinimum && keep.size <= keepMaximum &&
      allIds.size == expected.size &&
      allIds.distinct.size == expected.size && allIds.toSet == expected.map(_.cardId).toSet

  def moveToKeep(cardId: String): CardDecisionState = {
    discard.find(_.cardId == cardId).fold(this) { selected =>
      if (keep.size < keepMaximum)
        copy(keep = keep :+ selected, discard = discard.filterNot(_.cardId == cardId),
          selectedResolution = None, selectedReplacement = None)
      else if (keepMaximum == 1)
        copy(keep = Vector(selected),
          discard = keep ++ discard.filterNot(_.cardId == cardId),
          selectedResolution = None, selectedReplacement = None)
      else this
    }
  }

  def moveToDiscard(cardId: String): CardDecisionState =
    keep.find(_.cardId == cardId).fold(this)(card =>
      copy(keep = keep.filterNot(_.cardId == cardId), discard = discard :+ card,
        stage = CardDecisionStage.Arrange, selectedResolution = None,
        selectedReplacement = None))

  def move(cardId: String, delta: Int): CardDecisionState = {
    val from = discard.indexWhere(_.cardId == cardId)
    val to = math.max(0, math.min(discard.size - 1, from + delta))
    if (from < 0 || from == to) this
    else {
      val card = discard(from)
      copy(discard = discard.patch(from, Nil, 1).patch(to, Vector(card), 0))
    }
  }

  def arrangeDrop(cardId: String, beforeId: Option[String]): CardDecisionState = {
    val cards = discard.filterNot(_.cardId == cardId)
    discard.find(_.cardId == cardId).fold(this) { card =>
      val index = beforeId.flatMap(id => Option(cards.indexWhere(_.cardId == id))
        .filter(_ >= 0)).getOrElse(cards.size)
      copy(discard = cards.patch(index, Vector(card), 0))
    }
  }

  def chooseResolution(resolution: CardResolution): CardDecisionState =
    copy(selectedResolution = Some(resolution), selectedReplacement = None)

  def chooseReplacement(cardId: String): CardDecisionState =
    copy(selectedReplacement = selectedResolution.toVector
      .flatMap(_.replacementTargets).find(_.cardId == cardId))

  def resolutionValid: Boolean = selectedResolution.exists { resolution =>
    !resolution.replacementRequired || selectedReplacement.exists(selected =>
      resolution.replacementTargets.exists(_.cardId == selected.cardId))
  }
}

object CardDecisionState {
  def initial(decision: PendingCardDecision): CardDecisionState =
    if (decision.kind == "starting-adviser")
      CardDecisionState(decision.decisionId, CardDecisionStage.Arrange,
        Vector.empty, decision.cards, decision.keepMinimum, decision.keepMaximum)
    else CardDecisionState(decision.decisionId, CardDecisionStage.Arrange,
      Vector.empty, decision.cards, decision.keepMinimum, decision.keepMaximum)
}
