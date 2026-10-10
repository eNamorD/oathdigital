package oathdigital.gameplay.actions.dice

import oathdigital.model._

/** The dice of one roll that a reroll may change, and who rolled them
  * (catalog batch 7, slice 1). `indices` address the pool's recorded
  * outcome, which keeps every face the pool has rolled, and may include
  * earlier faces rerolled with this throw; `number` is the pool's count of
  * rolls, this one included.
  */
final case class DiceThrow(pool: PoolKey, die: DiceKind, roller: PlayerId,
    number: Int, indices: Vector[Int], faces: Vector[DieFace])

object DiceThrow:
  /** The dice `pool` holds, which is how many faces each throw adds. */
  def count(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollPools.get(pool).fold(0)(_.count)

  /** The last throw of `pool`: the faces its last roll added, as many as
    * the pool holds dice, and every earlier face rerolled since, in index
    * order (Silver Charm rerolls earlier dice with a roll). Empty when the
    * pool has not rolled. Recover's pool accumulates faces over its rolls,
    * so each Recover roll is its own throw.
    */
  def lastIndices(ready: ReadyGame, pool: PoolKey): Vector[Int] =
    val thrown = count(ready, pool)
    ready.game.current.rollOutcomes.get(pool)
      .filter(outcome => thrown > 0 && outcome.faces.size >= thrown)
      .fold(Vector.empty[Int]) { outcome =>
        val size = outcome.faces.size
        (outcome.rerolled ++ (size - thrown until size)).distinct.sorted
      }
