package oathdigital.model

/** Family of a rolled die, matching the two physical die kinds in Oath. */
enum DiceKind { case Defense, Attack }

/** Die specification a walker `Roll` node draws from a named pool.
  *
  * Own sealed family, so it can live in its own file. (A `DiceSpec` carries
  * only the die kind for now; later slice tasks may extend it.)
  */
final case class DiceSpec(die: DiceKind)

/** How a `Roll` node gets its faces. */
enum RollMode:
  /** The walker parks on the node. No production tree builds one and no
    * command answers it; the engine's `roll` resume is reached by tests. */
  case Parked
  /** The walker asks its dice source and keeps walking in the same command. */
  case Automatic
