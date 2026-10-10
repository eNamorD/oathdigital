package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
import oathdigital.model.{Operation, PlayerId, SpendSupply}

/** Removes `actor`'s Supply payments from a cost window's children, wherever
  * the window holds them (operation rewrite design): listed (a Campaign's, a
  * Travel's), selected by a `Branch`, or built when a node runs (a Search's
  * `BuildOps`, or a waiver's). Everything else stays: a Travel's cost window
  * also holds its pawn move. `SpendSupply` cannot be zero, so a waived
  * payment is removed, not set to nothing; in the tree an empty `Sequence`
  * keeps its place.
  */
object SupplyStrip:
  def apply(ctx: PowerCtx, actor: PlayerId, operations: Vector[Operation])
      : Vector[Operation] =
    OperationRewrite.ops(ctx, operations)((_, _) => {
      case SpendSupply(player, _, _) if player == actor => Vector.empty
    })
