package oathdigital.gameplay.operations

import oathdigital.model._

final case class SkippedOperation(requested: CoreOperation,
    reasons: Vector[OperationReason])

final case class OperationRun(state: ReadyGame,
    executed: Vector[CoreOperation], skipped: Vector[SkippedOperation]) {
  /** Legacy event reducers must not accept a different effect than recorded. */
  def expectEffects(requested: Vector[CoreOperation],
      detail: String): Either[OathViolation, ReadyGame] =
    if (executed == requested.map(OperationRun.canonical)) Right(state)
    else Left(OathViolation.InvalidEventOrder(detail))
}

object OperationRun {
  def canonical(operation: CoreOperation): CoreOperation = operation match {
    case value: SpendSupply => value.copy(required = true)
    case value: Discard.Denizen => value.copy(required = false)
    case value: Play => value.copy(required = false)
    case value: Replace => value.copy(required = false)
    case other => other
  }
}

/** Sole public orchestrator of an operation batch. It owns the per-run
  * [[OperationValidator]] (assembled from the caller's contextual
  * `allowlist` policy plus the restrictions vector), folds each operation
  * through staged validation and the raw [[OperationExecutor]], applies the
  * owning procedure's direct `update`, and runs the post-state invariant.
  *
  * Whole-batch rejection must stay staged: an operation later in a batch can
  * be satisfiable only after earlier operations ran (for example a Campaign
  * losing-force `ReturnToBoard` that moves warbands out of a bank which
  * in-batch `Kill`s replenish). Validating every operation against the initial
  * state would reject those trajectory batches the retired executor accepted,
  * so aggregated whole-batch validation is exposed through [[report]] and the
  * authoritative rejection is the staged `validateOne` per operation — the
  * same first-fail, atomic behavior the executor performed.
  *
  * `requireAll` treats every operation as `required` (a reduced or skipped
  * effect rejects the batch). The walker sets it for the `Move` children of a
  * required composite, which carry no flag of their own.
  */
object OperationPipeline {
  def run(
      ready: ReadyGame,
      operations: Vector[CoreOperation],
      allowlist: OperationPolicy,
      restrictions: Vector[OperationRestriction] = Vector.empty,
      requireAll: Boolean = false
  )(
      update: ReadyGame => Either[OathViolation, ReadyGame]
  ): Either[OathViolation, OperationRun] = {
    val validator = new OperationValidator(allowlist, restrictions)
    if (operations.isEmpty) Left(OperationError.EmptyOperationBatch.toViolation)
    else
      for {
        expected <- OperationStateInvariant.cardIds(ready)
          .left.map(_.toViolation)
        staged <- operations.foldLeft[Either[OathViolation, OperationRun]](
          Right(OperationRun(ready, Vector.empty, Vector.empty))) {
          (result, operation) => result.flatMap { current =>
            OperationResolution.resolve(current.state, operation, validator,
              requireAll)
              .flatMap {
                case OperationResolution.Skip(reasons) =>
                  Right(current.copy(skipped = current.skipped :+
                    SkippedOperation(operation, reasons)))
                case OperationResolution.Execute(actual) =>
                  new OperationExecutor().execute(current.state, actual)
                    .left.map(_.toViolation).map(state => current.copy(
                      state = state,
                      executed = current.executed :+ OperationRun.canonical(actual)))
              }
          }
        }
        updated <- OperationError.describe(update(staged.state))
          .left.map(_.toViolation).flatMap(identity)
        // Decision 5: the invariant runs once after the module update, not
        // between operations — intermediate states within a legal batch are
        // not invariant-checked (matching the retired post-`update` check).
        _ <- OperationStateInvariant.validate(updated, expected)
          .left.map(_.toViolation)
      } yield staged.copy(state = updated)
  }

  /** Aggregated whole-batch report against the initial state: shape reasons
    * (per operation plus cross-operation) followed by allowlist reasons.
    */
  def report(
      ready: ReadyGame,
      operations: Vector[CoreOperation],
      allowlist: OperationPolicy,
      restrictions: Vector[OperationRestriction] = Vector.empty
  ): Vector[OperationReason] =
    new OperationValidator(allowlist, restrictions).validateBatch(ready, operations)
}
