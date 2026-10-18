package oathdigital.gameplay.powerresolver

import oathdigital.model.{Branch, CoreOperation, DeferralKey, Operation,
  PendingTree, ReadyGame, TakeDeferred}

/** Reads and resolves the ops a power holds with `Defer` (operation rewrite
  * design, "Deferral"). A power holds an op by matching it with
  * `OperationRewrite.ops` and returning `Defer(key, Vector(op))`, so that it
  * does not run. It records one by returning `Defer(key, Vector(op))` and
  * then `op`: the record holds the operation itself, not its result, so
  * either order records the same thing, and recording first leaves the
  * operation as the last leaf its own node ran — which a `Note` placed
  * after it restates (`TradeProcedure.tradedNote`). A later step resolves
  * what is held, in the same action.
  */
object Deferred:
  /** The ops held under `key`, oldest first. */
  def held(ready: ReadyGame, key: DeferralKey): Vector[CoreOperation] =
    ready.game.current.walkerDeferred.getOrElse(key, Vector.empty)

  /** A step that resolves what `key` holds: nothing while nothing is held,
    * else what `f` returns for the held ops, then the take. `f` returns its
    * questions first, then what runs. The take comes last because a `Branch`
    * selects again when a parked command resumes, and must find the ops
    * still held; so nothing after `f`'s last question may park.
    */
  def resolve(key: DeferralKey)(
      f: (ReadyGame, PendingTree, Vector[CoreOperation]) => Vector[Operation])
      : Operation =
    Branch((ready, pending) => {
      val ops = held(ready, key)
      if ops.isEmpty then Vector.empty
      else f(ready, pending, ops) :+ TakeDeferred(key)
    })
