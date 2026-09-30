package oathdigital.gameplay.phases.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.StateBasedEvaluation
import oathdigital.model.{OathTransition, OathViolation, OperationRestriction}

/** Round end after the last player's Rest, moved unchanged from
  * `Rest.finishRound`.
  */
object TurnBoundary:
  def finishRound(catalog: ExecutableCatalog, transition: OathTransition,
      randomPort: WarExhaustionRandomPort,
      restrictions: Vector[OperationRestriction])
      : Either[OathViolation, OathTransition] =
    StateBasedEvaluation.endRound(transition.state, randomPort.choose)
      .flatMap(_.foldLeft[Either[OathViolation, OathTransition]](
        Right(transition)) {
        case (Right(current), event) =>
          StateBasedEvaluation.evolve(catalog, current.state, event,
            restrictions).map { next =>
            current.copy(state = next, events = current.events :+ event)
          }
        case (failure @ Left(_), _) => failure
      })
