package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy,
  OperationRun}
import oathdigital.gameplay.powerresolver.{ContributingPower, Deferred,
  ExecutedBatch, ExecutedTrigger}
import oathdigital.model.{Defer, DeferralKey, OathViolation, ReadyGame}

/** The walker's side of triggers on executed operations (catalog batch 8,
  * slice 6). A batch that starts a follow-up holds its executed operations
  * under [[key]], in its own journaled step, so a command that resumes
  * inside a follow-up asks the triggers again with the same operations.
  * `ProcedureWalker` walks the follow-ups and then takes what is held. It
  * names no card.
  */
private[walker] object ExecutedTriggers:
  val key: DeferralKey = DeferralKey("walker.executed-triggers")

  /** The triggers among `powers`, in the order contributions chain. */
  def of(powers: WalkerPowers): Vector[ExecutedTrigger] =
    powers.powers.collect { case trigger: ExecutedTrigger => trigger }
      .sortBy(ContributingPower.sortKey)

  /** What a trigger reads now: the held operations and the walk's state. */
  def batch(ctx: WalkCtx): ExecutedBatch =
    ExecutedBatch(ctx.activePlayer, Deferred.held(ctx.state, key), ctx.state)

  /** Whether a batch's operations are held for its follow-ups. */
  def holds(ready: ReadyGame): Boolean = Deferred.held(ready, key).nonEmpty

  /** `run`, with its executed operations held when some trigger starts a
    * follow-up on them. Nothing is held for a follow-up's own batches or
    * for an empty batch. */
  def held(ctx: WalkCtx, run: OperationRun)
      : Either[OathViolation, OperationRun] =
    val batch = ExecutedBatch(ctx.activePlayer, run.executed, run.state)
    if ctx.following || run.executed.isEmpty ||
        !of(ctx.powers).exists(_.onExecuted(batch).nonEmpty) then Right(run)
    else OperationPipeline.run(run.state, Vector(Defer(key, run.executed)),
        OperationPolicy.Permissive, Vector.empty, requireAll = true)(
        Right(_)).map(deferred => run.copy(state = deferred.state,
        executed = run.executed ++ deferred.executed))
