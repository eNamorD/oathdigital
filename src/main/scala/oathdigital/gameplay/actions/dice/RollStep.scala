package oathdigital.gameplay.actions.dice

import oathdigital.model._

/** The one way a tree rolls dice (catalog batch 7, slice 1): an automatic
  * `Roll`, then the roller's reroll choice, asked again after each reroll
  * until the roller keeps the roll or nothing usable is left. With no
  * roller (bandits defending), it is the bare `Roll`.
  *
  * `window` is the roll's own, which a power hooks to change the pool
  * before it rolls (Mounted Patrol).
  */
object RollStep:
  def apply(pool: PoolKey, dice: DiceSpec, roller: Option[PlayerId],
      window: Option[PowerWindow] = None): Operation =
    val roll = Roll(pool, dice, RollMode.Automatic, window)
    roller.fold[Operation](roll) { player =>
      val choice = new RerollChoice(pool, dice.die, player)
      Sequence(Vector(roll, Repeat((ready, pending) =>
        choice.throwAt(ready).forall(rolled => !RerollChoice.kept(pending,
          RerollChoice.decisionId(pool, rolled.number))), choice)))
    }
