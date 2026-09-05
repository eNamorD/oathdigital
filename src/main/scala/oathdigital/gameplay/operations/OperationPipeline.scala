package oathdigital.gameplay.operations

import oathdigital.gameplay.{OathViolation, ReadyGame}

/** Sole public orchestrator of an operation batch. It owns the per-run
  * [[OperationValidator]] (assembled from the caller's contextual
  * `allowlist` policy plus the restrictions vector), folds each operation
  * through staged validation and the raw [[OperationExecutor]], applies the
  * owning procedure's direct `update`, and runs the post-state invariant.
  *
  * Ordering mirrors the retired transaction seam: empty batch guard,
  * card-ids snapshot, staged per-operation validation (trajectory) plus raw
  * execution, describe-guarded direct update, final state invariant. Every
  * rejection is an [[OathViolation.CoreOperationRejected]] carrying the
  * reason's verbatim code and detail.
  *
  * Whole-batch rejection must stay staged: an operation later in a batch can
  * be satisfiable only after earlier operations ran (for example a Campaign
  * losing-force `ReturnToBoard` that moves warbands out of a bank which
  * in-batch `Kill`s replenish). Validating every operation against the initial
  * state would reject those trajectory batches the retired executor accepted,
  * so aggregated whole-batch validation is exposed through [[report]] and the
  * authoritative rejection is the staged `validateOne` per operation — the
  * same first-fail, atomic behavior the executor performed.
  */
object OperationPipeline {
  def run(
      ready: ReadyGame,
      operations: Vector[CoreOperation],
      allowlist: OperationPolicy,
      restrictions: Vector[OperationRestriction] = Vector.empty
  )(
      update: ReadyGame => Either[OathViolation, ReadyGame]
  ): Either[OathViolation, ReadyGame] = {
    val validator = new OperationValidator(allowlist, restrictions)
    if (operations.isEmpty) Left(OperationError.EmptyOperationBatch.toViolation)
    else
      for {
        expected <- OperationStateInvariant.cardIds(ready)
          .left.map(_.toViolation)
        staged <- operations.foldLeft[Either[OathViolation, ReadyGame]](
          Right(ready)) { (result, operation) =>
          result.flatMap { current =>
            validator.validateOne(current, operation) match {
              case reasons if reasons.nonEmpty =>
                Left(OathViolation.CoreOperationRejected(reasons.head.code,
                  reasons.head.detail))
              case _ => new OperationExecutor().execute(current, operation)
                .left.map(_.toViolation)
            }
          }
        }
        updated <- OperationError.describe(update(staged))
          .left.map(_.toViolation).flatMap(identity)
        _ <- OperationStateInvariant.validate(updated, expected)
          .left.map(_.toViolation)
      } yield updated
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
