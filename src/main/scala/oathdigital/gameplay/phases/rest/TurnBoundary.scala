package oathdigital.gameplay.phases.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.StateBasedEvaluation
import oathdigital.model.OathState.Ready
import oathdigital.model.{OathContinue, OathTransition, OathViolation}

/** Round end after the last player's Rest, moved unchanged from
  * `Rest.finishRound`.
  */
object TurnBoundary {
  def finishRound(catalog: ExecutableCatalog, transition: OathTransition,
      randomPort: WarExhaustionRandomPort)
      : Either[OathViolation, OathTransition] =
    StateBasedEvaluation.endRound(transition.state, randomPort.choose)
      .flatMap(_.foldLeft[Either[OathViolation, OathTransition]](
        Right(transition)) {
        case (Right(current), event) =>
          StateBasedEvaluation.evolve(catalog, current.state, event).map { next =>
            val continue = next match {
              case Ready(ready) if ready.game.current.result.nonEmpty =>
                OathContinue.GameFinished(ready.game.current.result.get.winner)
              case _ => current.continue
            }
            current.copy(state = next, events = current.events :+ event,
              continue = continue)
          }
        case (failure @ Left(_), _) => failure
      })
}
