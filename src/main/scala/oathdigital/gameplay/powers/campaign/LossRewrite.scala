package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
import oathdigital.model._

/** The core operations a Campaign window's children produce, rewritten one
  * at a time (R3, from Hospital's private tally) through
  * `OperationRewrite.ops`, which reaches inside the closures that build
  * them and leaves alone any node with a window of its own.
  *
  * Billowing Fog, Traveling Doctor and Peace Envoy rewrite the losses with
  * it, and `VictoriousNow` the recorded result. `counted` is the shared loss
  * counter: Hospital counts its user's dead with it, and Cursed Cauldron its
  * enemy's.
  */
private[campaign] object LossRewrite:
  def apply(ctx: PowerCtx, children: Vector[Operation])(
      f: CoreOperation => Vector[CoreOperation]): Vector[Operation] =
    OperationRewrite.ops(ctx, children)((_, _) => { case operation =>
      f(operation) })

  /** `children`, with each kill of `owner`'s warbands of force `kind` counted
    * in `pool`, a pool that is never rolled or shown and that the walker
    * clears with every pool when the Campaign ends. Bandits are no player,
    * so only their kills count. When `returns` is set, a warband returned
    * from the supply to the owner's board did not stay dead, so it comes
    * back out of the count; only the losses return warbands.
    *
    * It counts the amount each operation asks for, which the Campaign's
    * kills always execute in full because they read the board they kill
    * from. */
  def counted(pool: PoolKey, kind: ForceKind, owner: Option[PlayerId],
      returns: Boolean)(ctx: PowerCtx, children: Vector[Operation])
      : Vector[Operation] =
    apply(ctx, children) {
      case kill @ Kill(Piece.Warbands(`kind`, count), _) =>
        Vector(kill, ModifyDicePool(pool, count))
      case sacrifice @ Sacrifice(player, Piece.Warbands(`kind`, count), _)
          if owner.contains(player) =>
        Vector(sacrifice, ModifyDicePool(pool, count))
      case move @ Move(Piece.Warbands(`kind`, count),
          PositionedLocation(Location.WarbandBank(_), _),
          PositionedLocation(Location.PlayArea(player), _), None)
          if returns && owner.contains(player) =>
        Vector(move, ModifyDicePool(pool, -count))
      case other => Vector(other)
    }
