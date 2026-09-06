package oathdigital.gameplay.walker

import oathdigital.gameplay.ReadyGame
import oathdigital.model.PlayerId

// Walker/leaf vocabulary that references the gameplay layer (spec decision
// S1). Kept OUT of `oathdigital.model`: model stores only the pending pointer
// (PendingTree) and must not import gameplay, while these types are consumed
// by the Task 2 Decide leaf, the Task 3 walker, and Task 5 action payloads.
//
// `DecisionPayload` used to live here (Task 2); Task 5 ruling 5.1 moved it to
// `oathdigital.model` because answered decisions are persisted on
// `CurrentGameState.walkerPending` between commands and must stay model-safe
// (see `model/DecisionPayload.scala`).

/** Resolves which player owns a pending walker decision at walk/resume time.
  *
  * Open for the same reason as `DecisionPayload`: concrete owners (acting
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
