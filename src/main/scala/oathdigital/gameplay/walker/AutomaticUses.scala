package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.model.{AutomaticUse, Operation, PendingTree, ReadyGame}

/** How the walker resolves an [[AutomaticUse]] (catalog batch 8, slice
  * 10): its effect, unless a power-use restriction refuses it in `state`.
  * Every walker path that resolves a `Branch` resolves this node the same
  * way, so a resume addresses the same vector the walk did. */
private[walker] object AutomaticUses:
  def children(use: AutomaticUse, state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers): Vector[Operation] =
    val refused = PowerUseRules.refusals(state,
      PowerUse.automatic(use.user, use.power, use.card),
      powers.useRestrictions)
    if refused.isEmpty then use.effect(state, pending) else Vector.empty
