package oathdigital.gameplay.powerresolver

import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.operations.Operation
import oathdigital.model.{DecisionOptionRef, PlayerId, PowerId, PowerTiming}

/** A WAKE, ACTION or REST power a player uses as an action.
  *
  * The engine finds its sources, checks access and once-per-turn use, and
  * records the use after `build`'s tree. `build` must be a pure function of
  * state: the tree is rebuilt on every resume.
  */
trait PhasePower {
  def id: PowerId
  def timing: PowerTiming
  /** Power-specific preconditions beyond access and once-per-turn. */
  def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef): Boolean
  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation]
}

final case class PhasePowers(powers: Vector[PhasePower]) {
  def find(id: PowerId): Option[PhasePower] = powers.find(_.id == id)
}
object PhasePowers {
  val empty: PhasePowers = PhasePowers(Vector.empty)
}
