package oathdigital.model

/** One recorded answer to a parked walker decision.
  *
  * `answered` grows per resolution (a `Repeat` re-parks the same decision id
  * on every fresh pass and each answer is recorded — duplicates accumulate,
  * and pass guards must key on the LATEST answer, not on `answered.size`).
  *
  * @param decisionId the id of the `Decide` node that parked.
  * @param answer the model-safe choice the player made.
  * @param by the player who submitted it: the authorized requester, which
  *   equals the parked `Decide`'s owner. Journalled so log lines can name who
  *   chose from the payload alone; replay does not re-derive the owner.
  */
final case class Answered(decisionId: String, answer: DecisionAnswer, by: PlayerId)

/** Parked walker position recorded in game state while an action awaits a
  * decision or roll (spec decision S1).
  *
  * Pending stores ONLY a pointer into the action: the stable node-id chain
  * `at` and the decisions already `answered`. The player the procedure
  * belongs to is always `turn.activePlayer`; nothing a walk does changes it.
  * The action tree is derived per command and never stored in state. The walker
  * context is rebuilt from state at each command — so this type stays a pure
  * model value with no dependency on the gameplay operation ADT (see the
  * `inner production packages do not import outer adapters` guard in
  * `BackendArchitectureSuite`).
  *
  * @param at stable node-id chain naming the node the walker resumes at.
  * @param answered decisions already recorded during this action, in answer
  *   order (each carries its model-safe [[DecisionAnswer]]).
  */
final case class PendingTree(
    at: Vector[String],
    answered: Vector[Answered]
)

/** Stable name of a dice pool (e.g. "recover", "campaign.attack").
  *
  * A pure model value: `CurrentGameState.rollPools` lives in model and must
  * not import gameplay, so the pool keys are declared here. The dice-pool
  * leaves in `gameplay/operations/CoreOperations.scala` reference them through
  * the normal model import.
  */
final case class PoolKey(value: String)
