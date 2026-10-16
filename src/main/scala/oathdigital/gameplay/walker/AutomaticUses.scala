package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.model.{AutomaticUse, Operation, PendingTree, ReadyGame}

/** How the walker resolves an [[AutomaticUse]] (catalog batch 8, slice
  * 10): its effect, unless a power-use restriction refuses it in `state`.
  * Every walker path that resolves a `Branch` resolves this node the same
  * way, so a resume addresses the same vector the walk did.
  *
  * A walk `inside` the node resumes a park in its effect, which proves the
  * use was allowed when the walk entered it. Its effect then runs unjudged:
  * a step of the effect that turned the verdict would otherwise empty the
  * vector the parked position points into. */
private[walker] object AutomaticUses:
  def children(use: AutomaticUse, state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers, inside: Boolean): Vector[Operation] =
    val refused = !inside && PowerUseRules.refusals(state,
      PowerUse.automatic(use.user, use.power, use.card),
      powers.useRestrictions).nonEmpty
    if refused then Vector.empty else use.effect(state, pending)
