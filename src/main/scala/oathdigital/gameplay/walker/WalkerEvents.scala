package oathdigital.gameplay.walker

import oathdigital.gameplay.WalkerEvent
import oathdigital.gameplay.operations.CoreOperation
import oathdigital.model.{ActionRef, Answered, DecisionPayload, DieFace,
  PlayerId, PoolKey, PowerId, RelicId, SiteId}

/** Payload of one recorded walker step (Task 3).
  *
  * Open, not `sealed`: concrete per-node payloads specialize in later tasks
  * (DecisionStarted/Resolved, RollRecorded, ...) and may be declared wherever
  * the owning action lives. This slice carries only the minimal placeholder.
  */
trait WalkerStepPayload extends Product with Serializable

/** Semantic description of one executed delta node. Recorded operations are
  * the replay authority; this value independently describes what happened for
  * logs, projections, and audit consumers.
  */
sealed trait DeltaMeaning extends Product with Serializable
object DeltaMeaning {
  final case class DicePoolModified(pool: PoolKey, delta: Int)
      extends DeltaMeaning
  final case class SupplySpent(player: PlayerId, amount: Int)
      extends DeltaMeaning
  final case class RelicAcquired(player: PlayerId, relic: RelicId,
      site: SiteId) extends DeltaMeaning
  final case class OperationApplied(label: String) extends DeltaMeaning
}

object WalkerStepPayload {
  /** Semantic fact for an executed delta node. */
  final case class DeltaRecorded(meaning: DeltaMeaning)
      extends WalkerStepPayload
}

/** Records a resolved parked decision (Task 5 ruling 5.3). The step's `ops`
  * stay empty: appending the answer to `pending.answered` is a state write
  * (the walker rebuilds `answered` from these events at replay), not an
  * operation batch.
  */
final case class ChoicePayload(decisionId: String, payload: DecisionPayload)
    extends WalkerStepPayload

/** Faces the acting player rolled for `pool`, recorded when a `Roll` park is
  * resumed through `ProcedureWalker.roll` (Task 4). The step's `ops` stay
  * empty: the outcome is a state write (a `RollOutcome` into
  * `CurrentGameState.rollOutcomes`), not an operation batch, so replay must
  * re-derive the outcome from this payload rather than applying ops.
  */
final case class RollPayload(pool: PoolKey, faces: Vector[DieFace])
    extends WalkerStepPayload

/** Container event: recorded once per delta, resolved choice, or submitted
  * roll the walker executes.
  *
  * `ops` holds the applied leaves (`PrimitiveOperation`s, which are
  * `CoreOperation`s), so replay applies exactly the deltas that produced this
  * step; `nodeId` is the leaf's child-index path joined with `"."` (repeat
  * passes of one body leaf repeat the same `nodeId` — events stay ordered in
  * the journal). `contributions` is the deterministic order of powers whose
  * gather produced this step's `ops` (spec decision 10f) — an AUDIT fact
  * (who influenced this node), never a replay input: `ops` alone is the
  * replay authority (spec decision 5), so `ProcedureWalker.applyRecorded`
  * reads `ops` and ignores this field entirely. `Vector.empty` for a node
  * with no window (no explicit default: every construction site must state
  * what it recorded).
  */
final case class WalkerStepRecorded(
    actor: PlayerId,
    nodeId: String,
    payload: WalkerStepPayload,
    ops: Vector[CoreOperation],
    contributions: Vector[PowerId]
) extends WalkerEvent

/** Durable state fact written whenever walking stops at a Decide or Roll.
  * `action` is stored beside pointer-only PendingTree on replay so generic
  * resume commands can rebuild the correct tree after reload. `modifiers`
  * (fix-round ruling I) is the player-selected power ids chosen when the
  * walker action started, carried on every park of this action so replay
  * restores `CurrentGameState.walkerModifiers` from this fact alone, without
  * re-running the walker or re-deriving anything.
  */
final case class WalkerParked(
    actor: PlayerId,
    action: ActionRef,
    at: Vector[String],
    answered: Vector[Answered],
    modifiers: Vector[PowerId]
) extends WalkerEvent

/** Durable action-boundary fact. Replay clears every walker-owned scratch
  * field without deriving or running the operation tree.
  */
final case class WalkerCompleted(actor: PlayerId, action: ActionRef)
    extends WalkerEvent
