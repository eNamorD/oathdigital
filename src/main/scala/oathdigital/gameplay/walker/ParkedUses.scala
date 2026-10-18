package oathdigital.gameplay.walker

import oathdigital.model.{AutomaticUse, Operation, PendingTree, ReadyGame}

/** The automatic use whose power-use step a walk is parked at (automatic
  * power consent design, "Reaching the step"). The step is the first child
  * of its node, and no other first child of the node parks
  * ([[AutomaticUses]]), so a walk parked at an automatic use's first child
  * is parked at its step. Reading the node needs only navigation, not the
  * parked decision, so the check is cheap enough to run after every
  * command. */
object ParkedUses:
  /** The index of the node's first child. */
  val Slot = "0"

  def of(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[AutomaticUse] =
    if !pending.at.lastOption.contains(Slot) then None
    else WalkerPowerGather.nodeAt(state, action,
        pending.copy(at = pending.at.init), powers)
      .collect { case use: AutomaticUse => use }
