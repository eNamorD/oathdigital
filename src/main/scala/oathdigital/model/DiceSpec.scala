package oathdigital.model

/** Family of a rolled die, matching the two physical die kinds in Oath. */
sealed trait DiceKind extends Product with Serializable
object DiceKind {
  case object Defense extends DiceKind
  case object Attack extends DiceKind
}

/** Die specification a walker `Roll` node draws from a named pool.
  *
  * Own sealed family, so it can live in its own file. (A `DiceSpec` carries
  * only the die kind for now; later slice tasks may extend it.)
  */
final case class DiceSpec(die: DiceKind)

/** How a `Roll` node gets its faces. */
sealed trait RollMode extends Product with Serializable
object RollMode {
  /** The walker parks and the faces ride a later `RollWalker` command. */
  case object Parked extends RollMode
  /** The walker asks its dice source and keeps walking in the same command. */
  case object Automatic extends RollMode
}
