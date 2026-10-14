package oathdigital.gameplay.powerresolver

import oathdigital.model.{DecisionOptionRef, Operation}

/** An action's modifier decision, as a power judged there finds it at
  * `PowerCtx.operation`. It names what the player selected when the action
  * started (Trade's resource), which the walk's state does not hold yet on
  * the command that starts the action. */
trait ModifierHost extends Operation:
  def startSelection: Vector[DecisionOptionRef]
