package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.PowerWindow

/** Root of the unified operation tree.
  *
  * An operation exposes its immediate children; a tree's leaves are
  * [[PrimitiveOperation]]s, which expose themselves as their only child so the
  * whole action tree shares one accessor. `window` is the hook point for later
  * power wiring; `None` means engine-internal.
  *
  * NOTE: this root trait is deliberately not `sealed`. Scala 2.13 requires
  * every subclass of a sealed type to live in the same source file, and the
  * concrete [[CoreOperation]]/[[PrimitiveOperation]] cases stay in
  * `CoreOperations.scala` with their sealed parents.
  */
trait Operation {
  /** Hook point for power windows; None = engine-internal. */
  def window: Option[PowerWindow] = None
  /** Immediate children of this node. Primitives expose `Vector(this)`. */
  def children: Vector[Operation]
}

object Operation {
  /** Depth-first leaf sequence of an operation tree.
    *
    * A [[PrimitiveOperation]] is a leaf and flattens to itself; composites
    * flatten to the leaves of each child, in child order.
    */
  def flatten(operation: Operation): Vector[Operation] =
    operation match {
      case _: PrimitiveOperation => Vector(operation)
      case _ => operation.children.flatMap(flatten)
    }
}
