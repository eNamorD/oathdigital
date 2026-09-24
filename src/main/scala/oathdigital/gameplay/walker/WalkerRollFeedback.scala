package oathdigital.gameplay.walker

import oathdigital.model.PoolKey

/** The roll a parked decision wants shown beside it: which pool, the number
  * the score is measured against where there is one, and any consequence the
  * procedure has already worded.
  *
  * A `Decide` carries no pool of its own, and a projector that matched on the
  * procedure to decide what to show was exactly the drift the walker registry
  * exists to end -- so the procedure declares it, keyed by decision id, in the
  * same registry entry that already declares its roll decision id and its
  * continuations.
  */
final case class WalkerRollFeedback(pool: PoolKey, target: Option[Int] = None,
    detail: Vector[String] = Vector.empty)
