package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.model._

/** An ACTION phase power gated only by its cost. The engine pays `cost`
  * onto the power's source card and reads "payable, including the
  * empty-card rule" as its usability, so a subclass writes only `build`.
  */
abstract class PaidAction(idValue: String, override val cost: Cost)
    extends PhasePower {
  final val id: PowerId = PowerId(idValue)
  final def timing: PowerTiming = PowerTiming.Act
  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = true
}
