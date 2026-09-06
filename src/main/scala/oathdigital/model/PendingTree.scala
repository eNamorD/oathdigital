package oathdigital.model

import oathdigital.gameplay.ReadyGame
import oathdigital.gameplay.operations.Operation

/** Open decision payload carried by a walker `Decide` leaf (spec decision D2).
  *
  * Deliberately NOT sealed: per D2 a power or action declares its own payload
  * case (plus validation/preview) wherever it lives, and the engine stays
  * generic over payloads. A sealed-in-file trait would force every future
  * payload into this file.
  */
trait DecisionPayload extends Product with Serializable

/** Resolves which player owns a pending walker decision at walk/resume time.
  *
  * Open for the same reason as [[DecisionPayload]]: concrete owners (acting
  * player, a banner holder, ...) are declared by the action trees that need
  * them.
  */
trait OwnerQuery {
  def owner(ctx: WalkerCtx): Option[PlayerId]
}

/** Minimal walker context: the ready game a pending tree was derived from,
  * plus node-local scratch state the walker carries across a park/resume.
  *
  * `ready` is a snapshot taken *before* the parking state was written, so a
  * PendingTree stored in that state's CurrentGameState never (transitively)
  * contains itself: resume reads the live state from `CurrentGameState` and
  * uses this snapshot for derivation-time context.
  */
final case class WalkerCtx(
    ready: ReadyGame,
    scratch: Map[String, Any] = Map.empty
)

/** Parked walker position recorded in game state while an action awaits a
  * decision or roll.
  *
  * @param at stable node-id chain naming the parked node inside `action`.
  * @param answered decisions already recorded during this action.
  * @param actor the player whose action this is.
  * @param action the derived (transformed) action tree containing the parked
  *   node (spec: pending stores the derived tree).
  * @param ctx walker context snapshot (see [[WalkerCtx]]).
  */
final case class PendingTree(
    at: Vector[String],
    answered: Vector[String],
    actor: PlayerId,
    action: Operation,
    ctx: WalkerCtx
)

/** Stable name of a dice pool (e.g. "recover", "campaign.attack").
  *
  * Declared here, not beside the dice-pool leaves in
  * `gameplay/operations/CoreOperations.scala`, so the model aggregate
  * (`CurrentGameState.rollPools`) stays free of gameplay imports: this file is
  * the single model file sanctioned to reference the gameplay operation ADT
  * (see the `inner production packages do not import outer adapters` ruling in
  * `BackendArchitectureSuite`). The leaves that reference it import it from
  * `oathdigital.model` like every other model value.
  */
final case class PoolKey(value: String)
