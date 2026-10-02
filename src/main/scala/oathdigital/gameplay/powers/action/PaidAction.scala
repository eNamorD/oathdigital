package oathdigital.gameplay.powers.action

import oathdigital.catalog.PrintedPower
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.model._

/** An ACTION phase power gated only by its cost. The engine pays `cost`
  * onto the power's source card and reads "payable, including the
  * empty-card rule" as its usability, so a subclass writes only `build`.
  *
  * A card's action passes its printed power, which carries the id and the
  * printed cost. A banner's action, which no card prints, names both.
  */
abstract class PaidAction(final val id: PowerId, override val cost: Cost)
    extends PhasePower:
  def this(power: PrintedPower) = this(power.id, power.cost)

  final def timing: PowerTiming = PowerTiming.Act
  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = true
