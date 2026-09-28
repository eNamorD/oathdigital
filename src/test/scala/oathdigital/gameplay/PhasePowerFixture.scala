package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** A synthetic phase power on p1's faceup adviser, shared by the engine suite
  * and the projection suite (Task 11).
  */
object PhasePowerFixture:
  final case class TestPower(id: PowerId, timing: PowerTiming,
      tree: PlayerId => Operation = _ => BuildOps((_, _) => Right(Vector.empty)),
      override val cost: Cost = Cost.free)
      extends PhasePower:
    def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef) = true
    def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef): Either[OathViolation, Operation] =
      Right(tree(player))

  /** The card whose printed power the test powers stand in for, and that
    * power's id. */
  val card: DenizenId = CatalogNames.denizen("Magician's Code")
  val powerId: PowerId = PowerId("denizen.magicians-code")
  val source: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  /** p1 holds Magician's Code faceup, in `phase` of p1's turn. No site holds
    * forces, so an action boundary visibly refills bandits. */
  def holding(phase: Phase): ReadyGame =
    Table.start.adviser(p1, card).turn(p1, phase).ready
