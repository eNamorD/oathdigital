package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{CoreOperation, Operation}
import oathdigital.model.{OathViolation, ReadyGame}

/** Runs a declared action tree to completion against immutable state and
  * throws the result away, reporting only the operations it would have
  * applied (batch-1 Task 5).
  *
  * This is how a projector or a preview learns what an action would cost
  * without the client having committed to it. It is the command path minus
  * the journal: the same restrictions run, the same windows gather, the same
  * transforms fold, and the same `OperationPipeline` validates -- so a
  * candidate the simulation accepts is a candidate the command accepts, and
  * one it rejects is one the command would have rejected for the same reason.
  * The alternative, a second arithmetic path beside the tree, is what let
  * Travel's projected cost and its charged cost be two expressions that
  * agreed only by convention.
  *
  * Deliberately generic: nothing here knows which action it is running or
  * what the operations mean. The caller supplies a tree and reads whatever
  * it needs out of the returned batch.
  *
  * A tree that parks is rejected rather than reported: a park means the
  * action asks the player something, and an unanswered question has no
  * outcome to simulate. Every registered action whose tree can park
  * (Recover, Forge) therefore has nothing to ask this.
  */
object WalkerSimulation {

  def run(tree: Operation, state: ReadyGame,
      powers: WalkerPowers): Either[OathViolation, Vector[CoreOperation]] =
    try ProcedureWalker.restrictionViolations(tree, powers, state,
      state.game.current.turn.activePlayer)
      .headOption.toLeft(())
      .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers))
      .flatMap {
        case WalkerOutcome.Finished(_, events) => Right(events.collect {
          case step: WalkerStepRecorded => step.ops
        }.flatten)
        case WalkerOutcome.Parked(_, _) => Left(OathViolation.InvalidEventOrder(
          "a simulated walker tree parked; only a tree that runs to the end " +
            "has an outcome to simulate"))
      }
    // `ProcedureWalker` reports a broken resume position by throwing, and a
    // simulation must never propagate that to a projector building a view.
    catch {
      case error: IllegalArgumentException => Left(
        OathViolation.InvalidEventOrder(Option(error.getMessage)
          .getOrElse("invalid simulated walker position")))
    }
}
