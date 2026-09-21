package oathdigital.gameplay.powers.action

import oathdigital.model.{PoolKey, ReadyGame}

/** Reads the outcome an automatic `Roll` wrote into state. A pool that never
  * rolled reads as zero, so a `BuildOps` that runs after a skipped roll needs
  * no case of its own.
  */
private[action] object RollResults {
  /** Shields for a defense roll, swords for an attack roll, scored by
    * `DefenseDieFace.score` and `AttackDieFace.score` when the roll was
    * recorded.
    */
  def score(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollOutcomes.get(pool).fold(0)(_.score)

  /** Skull faces rolled in an attack pool. */
  def skulls(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollOutcomes.get(pool).fold(0)(_.skulls)
}
