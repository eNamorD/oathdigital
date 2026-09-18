package oathdigital.gameplay.operations

import oathdigital.model.CardIndex
import oathdigital.model.{OathViolation, ReadyGame}

private[gameplay] final case class OperationShadowComparison(
    candidateSucceeded: Boolean,
    stateMatches: Boolean,
    cardIndexMatches: Boolean
) {
  def matchesAuthoritative: Boolean =
    candidateSucceeded && stateMatches && cardIndexMatches
}

/** Internal differential result. Candidate state never becomes authoritative,
  * serialized, logged, or projected by production evolution.
  */
private[gameplay] final case class OperationShadowResult(
    authoritative: ReadyGame,
    candidate: Either[OathViolation, ReadyGame],
    comparison: OperationShadowComparison
)

private[gameplay] object OperationShadowEvolution {
  def compare(
      authoritative: ReadyGame,
      candidate: Either[OathViolation, ReadyGame]
  ): OperationShadowResult = {
    val comparison = candidate match {
      case Left(_) => OperationShadowComparison(
        candidateSucceeded = false,
        stateMatches = false,
        cardIndexMatches = false
      )
      case Right(shadow) => OperationShadowComparison(
        candidateSucceeded = true,
        stateMatches = authoritative == shadow,
        cardIndexMatches = (
          CardIndex.from(authoritative.game),
          CardIndex.from(shadow.game)
        ) match {
          case (Right(left), Right(right)) => left == right
          case _ => false
        }
      )
    }
    OperationShadowResult(authoritative, candidate, comparison)
  }
}
