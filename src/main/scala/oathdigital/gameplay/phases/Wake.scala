package oathdigital.gameplay.phases

import oathdigital.gameplay.{GameplayTransition, GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._

import GameStateUpdates.updateCurrent

sealed trait WakeCommand extends Product with Serializable
object WakeCommand {
  final case class EndWake(playerId: PlayerId) extends WakeCommand
}

/** What is left of the Wake phase once Take Wealth moved onto the generic
  * walker (batch-1 Task 7): ending the phase.
  *
  * Ending Wake is a phase transition, not an action -- it selects nothing,
  * costs nothing, has no operations to declare and no powers to gather, so
  * there is no tree for it to be. Porting it for symmetry would have added an
  * `ActionRef` whose whole procedure is a phase write, which is the shape this
  * migration exists to remove rather than to spread.
  */
object Wake {

  def handle(
      state: OathState,
      command: WakeCommand
  ): Either[OathViolation, OathTransition] =
    command match {
      case WakeCommand.EndWake(playerId) =>
        OathLifecycle.validateReady(state, playerId).flatMap(_ =>
          transition(
            state,
            Vector(WakeEnded(playerId)),
            ActActionSelection(playerId)
          ))
    }

  def evolve(
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] = event match {
    case WakeEnded(playerId) =>
      OathLifecycle.validateReady(state, playerId).map { ready =>
        Ready(
          updateCurrent(ready)(current =>
            current.copy(turn = current.turn.copy(phase = Phase.Act)))
        )
      }
    case _ => Left(InvalidEventOrder("Wake received a non-Wake event"))
  }

  private def transition(
      state: OathState,
      events: Vector[OathEvent],
      continue: OathContinue
  ): Either[OathViolation, OathTransition] =
    GameplayTransition(state, events, continue)(evolve)
}
