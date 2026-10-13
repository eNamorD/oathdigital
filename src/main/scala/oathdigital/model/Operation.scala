package oathdigital.model

/** Root of the unified operation tree.
  *
  * An operation exposes its immediate children; a tree's leaves are
  * [[PrimitiveOperation]]s, which expose themselves as their only child so the
  * whole action tree shares one accessor. `window` is the hook point for powers
  * to wire their effects on the tree; `None` means engine-internal.
  *
  * NOTE: this root trait is deliberately not `sealed`. Scala 2.13 requires
  * every subclass of a sealed type to live in the same source file, and the
  * concrete [[CoreOperation]]/[[PrimitiveOperation]] cases stay in
  * `CoreOperations.scala` with their sealed parents.
  */
trait Operation:
  /** Hook point for power windows; None = engine-internal. */
  def window: Option[PowerWindow] = None
  /** Immediate children of this node. Primitives expose `Vector(this)`. */
  def children: Vector[Operation]

/** A custom composite that plans its children from inputs it keeps, so it
  * cannot be rebuilt from new children alone (a card play's body).
  * `rewritten` rebuilds it with each child passed through `rewrite`, and
  * the rebuilt node keeps the rewrite whenever it plans its children
  * again. `OperationRewrite` descends it unless it carries a window of its
  * own.
  */
trait RewritableComposite extends Operation:
  def rewritten(rewrite: Operation => Operation): Operation

/** Ends the walk where it is reached: the procedure finishes at once, and
  * nothing after it in the tree runs. It changes no state and is never
  * part of a recorded batch, so the journal holds only the procedure's
  * usual completion. A Campaign that is skipped, or left with nothing to
  * attack, ends with it. */
case object EndWalk extends Operation:
  val children: Vector[Operation] = Vector.empty

object Operation:
  /** Depth-first leaf sequence of an operation tree.
    *
    * A [[PrimitiveOperation]] is a leaf and flattens to itself; composites
    * flatten to the leaves of each child, in child order.
    */
  def flatten(operation: Operation): Vector[Operation] =
    operation match
      case _: PrimitiveOperation => Vector(operation)
      case _ => operation.children.flatMap(flatten)
