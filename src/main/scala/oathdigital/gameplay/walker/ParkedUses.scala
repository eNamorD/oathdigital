package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.PowerUseStep
import oathdigital.model.{AutomaticUse, Operation, PendingTree, ReadyGame}

/** The automatic use whose power-use step a walk is parked at (automatic
  * power consent design, "Reaching the step"): the step is the first
  * child of its node, so the node is the step's parent. */
object ParkedUses:
  def of(state: ReadyGame, action: Operation, pending: PendingTree,
      powers: WalkerPowers): Option[AutomaticUse] =
    if pending.at.isEmpty then None
    else ProcedureWalker.parkedDecide(state, action, pending, powers,
        probing = false)
      .filter(decide => PowerUseStep.isDecision(decide.decisionId))
      .flatMap(_ => WalkerPowerGather.nodeAt(state, action,
        pending.copy(at = pending.at.init), powers))
      .collect { case use: AutomaticUse => use }
