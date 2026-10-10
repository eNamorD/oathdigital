package oathdigital.gameplay.actions.dice

import oathdigital.model._

/** The dice of one roll that a reroll may change, and who rolled them
  * (catalog batch 7, slice 1). `indices` address the pool's recorded
  * outcome, which keeps every face the pool has rolled; `number` counts the
  * pool's throws, this one included.
  */
final case class DiceThrow(pool: PoolKey, die: DiceKind, roller: PlayerId,
    number: Int, indices: Vector[Int], faces: Vector[DieFace])

object DiceThrow:
  /** The dice `pool` holds, which is how many faces each throw adds. */
  def count(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollPools.get(pool).fold(0)(_.count)

  /** The last throw of `pool`: its last faces, as many as the pool holds
    * dice. Empty when the pool has not rolled. Recover's pool accumulates
    * faces over its rolls, so each Recover roll is its own throw.
    */
  def lastIndices(ready: ReadyGame, pool: PoolKey): Vector[Int] =
    val faces =
      ready.game.current.rollOutcomes.get(pool).fold(0)(_.faces.size)
    val thrown = count(ready, pool)
    if thrown == 0 || faces < thrown then Vector.empty
    else (faces - thrown until faces).toVector
