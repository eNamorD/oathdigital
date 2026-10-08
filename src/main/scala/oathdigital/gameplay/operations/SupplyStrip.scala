package oathdigital.gameplay.operations

import oathdigital.model.{BuildOps, Operation, PlayerId, SpendSupply}

/** Removes `actor`'s Supply payments from a cost window's children, whether
  * they are listed (a Campaign's, a Travel's) or built when the node runs (a
  * Search's `BuildOps`, re-wrapped to filter what it builds). Everything else
  * stays: a Travel's cost window also holds its pawn move. `SpendSupply`
  * cannot be zero, so a waived payment is removed, not set to nothing.
  */
object SupplyStrip:
  def apply(actor: PlayerId, operations: Vector[Operation]): Vector[Operation] =
    operations.flatMap {
      case SpendSupply(player, _, _) if player == actor => Vector.empty
      case payment: BuildOps => Vector(payment.copy(build = (ready, pending) =>
        payment.build(ready, pending).map(_.filterNot {
          case SpendSupply(player, _, _) => player == actor
          case _ => false
        })))
      case other => Vector(other)
    }
