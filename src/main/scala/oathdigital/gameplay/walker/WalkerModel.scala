package oathdigital.gameplay.walker

import oathdigital.gameplay.ReadyGame
import oathdigital.model.PlayerId

// Walker/leaf vocabulary that references the gameplay layer (spec decision
// S1). Kept OUT of `oathdigital.model`: model stores only the pending pointer
// (PendingTree) and must not import gameplay, while these types are consumed
// by the Task 2 Decide leaf, the Task 3 walker, and Task 5 action payloads.

/** Open decision payload carried by a walker `Decide` leaf (spec decision D2).
  *
  * Deliberately NOT sealed: per D2 a power or action declares its own payload
  * case (plus validation/preview) wherever it lives, and the engine stays
  * generic over payloads.
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

/** Navigation-local walker context, rebuilt from state at each command (S1):
  * never stored in game state. `ready` is the ready game the walker is
  * advancing, and `scratch` carries node-local values across a walk step.
  */
final case class WalkerCtx(
    ready: ReadyGame,
    scratch: Map[String, Any] = Map.empty
)
