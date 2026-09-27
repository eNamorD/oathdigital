package oathdigital.gameplay.powerresolver

import oathdigital.model.{Cost, DecisionOptionRef, OathViolation, Operation, PlayerId, PowerId, PowerTiming, ReadyGame}

/** A WAKE, ACTION or REST power a player uses as an action.
  *
  * The engine finds its sources, checks access and once-per-turn use, and
  * records the use after `build`'s tree. `build` must be a pure function of
  * state: the tree is rebuilt on every resume.
  */
trait PhasePower extends NotingPower:
  def id: PowerId
  def timing: PowerTiming
  /** A power its holder must use (catalog batch 2, N8): it runs on its own
    * at the start of its phase and is never offered or accepted as an
    * optional use. Only WAKE powers are forced today. */
  def forced: Boolean = false
  /** Power-specific preconditions beyond access and once-per-turn. */
  def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef): Boolean
  /** What using the power costs, placed onto its source card. The engine
    * prepends the payment and reads "cost payable, including the empty-card
    * rule" as part of `usable`. Free by default. A cost is not accepted from
    * a banner source: banners have no costs.
    */
  def cost: Cost = Cost.free
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation]

final case class PhasePowers(powers: Vector[PhasePower]):
  def find(id: PowerId): Option[PhasePower] = powers.find(_.id == id)
object PhasePowers:
  val empty: PhasePowers = PhasePowers(Vector.empty)
