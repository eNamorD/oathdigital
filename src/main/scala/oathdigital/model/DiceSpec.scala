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
