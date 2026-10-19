package oathdigital.gameplay.walker

import oathdigital.model.{Answered, CoreOperation, DecisionAnswer, DecisionOptionRef, DieFace, PlayerId, PoolKey, PowerId, LogNote, ProcedureRef, QueuedProcedure, WalkerEvent}

/** Payload of one recorded walker step (Task 3).
  *
  * Open, not `sealed`: concrete per-node payloads specialize in later tasks
  * (DecisionStarted/Resolved, RollRecorded, ...) and may be declared wherever
  * the owning action lives. This slice carries only the minimal placeholder.
  */
trait WalkerStepPayload extends Product with Serializable

/** Marks a step that executed a batch of operations, as opposed to a
  * resolved choice or a submitted roll. It carries nothing: the step's
  * `ops` are both the replay authority and the only description of what
  * happened, and every reader works from them. */
case object DeltaPayload extends WalkerStepPayload

/** Records a resolved parked decision (Task 5 ruling 5.3). The step's `ops`
  * stay empty: appending the answer to `pending.answered` is a state write
  * (the walker rebuilds `answered` from these events at replay), not an
  * operation batch.
  *
  * @param by who answered.
  */
final case class ChoicePayload(decisionId: String, answer: DecisionAnswer,
    by: PlayerId)
    extends WalkerStepPayload

/** Faces the acting player rolled for `pool`, recorded when a `Roll` park is
  * resumed through `ProcedureWalker.roll` (Task 4). The step's `ops` stay
  * empty: the outcome is a state write (a `RollOutcome` into
  * `CurrentGameState.rollOutcomes`), not an operation batch, so replay must
  * re-derive the outcome from this payload rather than applying ops.
  * `automatic` is true when the walker rolled the faces itself from its dice
  * source; replay then needs no durable park at the node.
  */
final case class RollPayload(pool: PoolKey, faces: Vector[DieFace],
    automatic: Boolean = false) extends WalkerStepPayload

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
    nodeId: String,
    payload: WalkerStepPayload,
    ops: Vector[CoreOperation],
    contributions: Vector[PowerId]
) extends WalkerEvent

/** Durable state fact written whenever walking stops at a Decide or Roll.
  * `procedure` is stored beside pointer-only PendingTree on replay so generic
  * resume commands can rebuild the correct tree after reload -- one of the
  * three [[oathdigital.model.ProcedureRef]] families (Task 4), tagged with its family on the
  * wire so a decoder rejects a reference read back under the wrong one.
  * `startArgs` (batch-1 Task 5) is carried on every park because a procedure
  * whose tree needs what the player selected at the start cannot rebuild
  * that tree without it, and a selection -- unlike the actor's pawn site --
  * is a choice, not a state read. Empty for the procedures that select
  * nothing, which is every one that parks today.
  * `startFacts` is what the engine derived at the start, carried for the same
  * reason: a fact the walk itself changes (Travel's source site) cannot be
  * read back from state on a resume.
  */
final case class WalkerParked(
    procedure: ProcedureRef,
    at: Vector[String],
    answered: Vector[Answered],
    startArgs: Vector[DecisionOptionRef],
    startFacts: Vector[DecisionOptionRef] = Vector.empty
) extends WalkerEvent

/** Durable action-boundary fact. Replay clears every walker-owned scratch
  * field without deriving or running the operation tree.
  */
final case class WalkerCompleted(procedure: ProcedureRef)
    extends WalkerEvent

/** The first event of a walk started from the queue's head: replay takes
  * the head and holds it as `walkerEntry` until `WalkerCompleted`. An event,
  * not a `WalkerParked` field, because a walk can finish in its starting
  * command without parking, and the cost waiver and the Game Log tag still
  * need the entry (walker-driven game loop design, "The queue"). */
final case class ProcedureDequeued(entry: QueuedProcedure) extends WalkerEvent

/** The entry's actor declined it, and the Game Log writes the Skip line. */
final case class FollowUpSkipped(entry: QueuedProcedure) extends WalkerEvent

/** The action boundary ran: replay clears `boundaryDue` (walker-driven game
  * loop design, plan-time ruling 3). The boundary can write nothing else
  * (no bandits to refill, no title change), so this marker keeps replay
  * exact. No Game Log line. */
case object ActionBoundaryRan extends WalkerEvent

/** Audit fact: a power, printed rule or procedure took effect here and says
  * so in the Game Log (power log lines design; procedures journal their own
  * notes design, ruling 1). Replay applies nothing for it, as it never reads
  * `WalkerStepRecorded.contributions`. `covers` tells the log to drop the
  * generic detail lines of the step before it.
  */
final case class LogNoted(power: PowerId, note: LogNote, covers: Boolean)
    extends WalkerEvent
