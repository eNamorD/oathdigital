package oathdigital.gameplay.walker

import oathdigital.gameplay.WalkerEvent
import oathdigital.gameplay.operations.CoreOperation
import oathdigital.model.{DieFace, PlayerId, PoolKey}

/** Payload of one recorded walker step (Task 3).
  *
  * Open, not `sealed`: concrete per-node payloads specialize in later tasks
  * (DecisionStarted/Resolved, RollRecorded, ...) and may be declared wherever
  * the owning action lives. This slice carries only the minimal placeholder.
  */
trait WalkerStepPayload extends Product with Serializable

object WalkerStepPayload {
  /** Placeholder carried by every executed delta leaf this slice. `label` is
    * the executed leaf's `productPrefix` (e.g. `"Move"`, `"AdjustSupply"`).
    * Payloads specialize per node in later tasks.
    */
  final case class DeltaRecorded(label: String) extends WalkerStepPayload
}

/** Faces the acting player rolled for `pool`, recorded when a `Roll` park is
  * resumed through `ProcedureWalker.roll` (Task 4). The step's `ops` stay
  * empty: the outcome is a state write (a `RollOutcome` into
  * `CurrentGameState.rollOutcomes`), not an operation batch, so replay must
  * re-derive the outcome from this payload rather than applying ops.
  */
final case class RollPayload(pool: PoolKey, faces: Vector[DieFace])
    extends WalkerStepPayload

/** Container event: recorded once per delta leaf the walker executes.
  *
  * `ops` holds the applied leaves (`PrimitiveOperation`s, which are
  * `CoreOperation`s), so replay applies exactly the deltas that produced this
  * step; `nodeId` is the leaf's child-index path joined with `"."` (repeat
  * passes of one body leaf repeat the same `nodeId` — events stay ordered in
  * the journal).
  */
final case class WalkerStepRecorded(
    actor: PlayerId,
    nodeId: String,
    payload: WalkerStepPayload,
    ops: Vector[CoreOperation]
) extends WalkerEvent
