package oathdigital.application

import oathdigital.gameplay.walker.{HiddenOutcomes, RollPayload,
  WalkerStepRecorded}
import oathdigital.model._

/** Whether a closed command batch is a barrier that undo never takes back
  * (undo design, "Barrier detection"). It reads only the batch's events and
  * the states around it, so the rules engine stays unaware of undo. */
private[application] object UndoBarrier:
  def blocks(events: Vector[OathEvent], before: OathState, after: OathState)
      : Boolean =
    (before, after) match
      case (OathState.Ready(start), OathState.Ready(end)) =>
        events.exists(reveals) ||
          start.game.current.turn.activePlayer !=
            end.game.current.turn.activePlayer ||
          end.game.current.result.nonEmpty
      case _ => true

  private def reveals(event: OathEvent): Boolean = event match
    case step: WalkerStepRecorded =>
      step.payload.isInstanceOf[RollPayload] || step.ops.exists(hidden)
    case _ => false

  /** A composite operation hides when it or any leaf inside it does. */
  private def hidden(operation: Operation): Boolean =
    HiddenOutcomes.hides(operation) ||
      Operation.flatten(operation).exists(HiddenOutcomes.hides)
