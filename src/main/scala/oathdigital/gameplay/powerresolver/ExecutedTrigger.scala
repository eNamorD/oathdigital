package oathdigital.gameplay.powerresolver

import oathdigital.model.{CoreOperation, Operation, PlayerId, ReadyGame}

/** What a trigger on executed operations reads (catalog batch 8, slice 6):
  * the active player, the operations one walker batch executed, and the
  * state the trigger is asked in. */
final case class ExecutedBatch(actor: PlayerId,
    executed: Vector[CoreOperation], now: ReadyGame)

/** A power that starts a follow-up after a walker batch executes
  * operations it watches (catalog batch 8, slice 6), beside the arrival
  * rule `ContributingPower.onArrival`.
  *
  * The walker walks the follow-up right after the batch, before the walk
  * goes on, and asks each trigger when it reaches it: after the follow-ups
  * of the triggers before it ran, in the order contributions chain. A
  * follow-up's own batches start no follow-up.
  *
  * A command that resumes inside the follow-up asks again, with the same
  * executed operations and the state it parked on, and must get the same
  * tree up to the park. So a follow-up runs nothing before its last
  * question.
  */
trait ExecutedTrigger extends ContributingPower:
  def onExecuted(batch: ExecutedBatch): Option[Operation]
