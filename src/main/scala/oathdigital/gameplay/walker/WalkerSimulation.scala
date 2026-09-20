package oathdigital.gameplay.walker

import oathdigital.model.{Answered, CoreOperation, Decide, DecisionAnswer,
  DecisionOption, DecisionQuery, OathEvent, OathViolation, Operation,
  PendingTree, ReadyGame}

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
  *
  * `preview` and `previewParked` extend the same idea to a tree that parks
  * on a `ChooseOne`: they answer each option against the tree and report
  * what that answer would record.
  */
object WalkerSimulation {

  /** What answering one option would record. `complete` is true when the tree
    * then finished and false when it parked at a further decision.
    */
  final case class PreviewOutcome(operations: Vector[CoreOperation],
      complete: Boolean)

  /** One option of a previewed decision and what answering it does, or the
    * violation that makes it unanswerable.
    */
  final case class PreviewedOption(option: DecisionOption,
      outcome: Either[OathViolation, PreviewOutcome])

  def run(tree: Operation, state: ReadyGame,
      powers: WalkerPowers): Either[OathViolation, Vector[CoreOperation]] =
    guarded {
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder))
        .flatMap {
          case WalkerOutcome.Finished(_, events) =>
            Right(recordedOperations(events))
          case WalkerOutcome.Parked(_, _) => Left(
            OathViolation.InvalidEventOrder("a simulated walker tree parked; " +
              "only a tree that runs to the end has an outcome to simulate"))
        }
    }

  /** Whether a freshly built tree could start now: the same restriction check
    * and first walk a start performs, with nothing persisted. A tree that
    * parks and one that finishes both start; a rejected cost or restriction
    * does not. This is how a start control learns Supply affordability without
    * a second rule beside `SpendSupply`.
    */
  def starts(tree: Operation, state: ReadyGame, powers: WalkerPowers): Boolean =
    guarded {
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder))
    }.isRight

  /** Previews a freshly built tree: walks it to its first park and previews
    * that decision. The tree must park before it runs any operation, because
    * the state after an executed prefix is not reported by `advance`.
    */
  def preview(tree: Operation, state: ReadyGame,
      powers: WalkerPowers): Either[OathViolation, Vector[PreviewedOption]] =
    guarded {
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder))
        .flatMap {
          case WalkerOutcome.Parked(pending, events)
              if recordedOperations(events).isEmpty =>
            previewParked(state, tree, pending, powers)
          case WalkerOutcome.Parked(_, _) => Left(
            OathViolation.InvalidEventOrder("a previewed tree may not run " +
              "operations before its first decision"))
          case WalkerOutcome.Finished(_, _) => Left(
            OathViolation.InvalidEventOrder(
              "a previewed tree finished without parking on a decision"))
        }
    }

  /** Previews the decision `pending` is parked on: each `ChooseOne` option is
    * answered as the decision's owner, against a copy of the parked position,
    * and the walk continues until it finishes or parks again. Nothing is
    * persisted. Any other query shape is rejected.
    */
  def previewParked(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers): Either[OathViolation, Vector[PreviewedOption]] =
    guarded {
      ProcedureWalker.parkedDecide(state, tree, pending, powers)
        .toRight(OathViolation.InvalidEventOrder(
          "the walker is not parked on a decision"))
        .flatMap { decide =>
          decide.query match {
            case DecisionQuery.ChooseOne(options, _) =>
              Right(options.map(option => PreviewedOption(option,
                answer(state, tree, pending, powers, decide, option))))
            case _ => Left(OathViolation.InvalidEventOrder(
              s"decision ${decide.decisionId} is not a choose-one, so it " +
                "cannot be previewed"))
          }
        }
    }

  private def answer(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide, option: DecisionOption)
      : Either[OathViolation, PreviewOutcome] = guarded {
    ProcedureWalker.resolve(state, tree, pending,
      Answered(decide.decisionId, DecisionAnswer.ChooseOneAnswer(option.ref),
        decide.owner), powers, WalkerDice.placeholder).map {
      case WalkerOutcome.Finished(_, events) =>
        PreviewOutcome(recordedOperations(events), complete = true)
      case WalkerOutcome.Parked(_, events) =>
        PreviewOutcome(recordedOperations(events), complete = false)
    }
  }

  private def recordedOperations(events: Vector[OathEvent])
      : Vector[CoreOperation] = events.collect {
    case step: WalkerStepRecorded => step.ops
  }.flatten

  // `ProcedureWalker` reports a broken resume position by throwing, and a
  // simulation must never propagate that to a projector building a view.
  private def guarded[A](body: => Either[OathViolation, A])
      : Either[OathViolation, A] =
    try body
    catch {
      case error: IllegalArgumentException => Left(
        OathViolation.InvalidEventOrder(Option(error.getMessage)
          .getOrElse("invalid simulated walker position")))
    }
}
