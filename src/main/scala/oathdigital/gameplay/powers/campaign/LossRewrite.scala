package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** The core operations a Campaign window's children produce, rewritten one
  * at a time (R3, from Hospital's private tally). It reaches through
  * `BuildOps`, `Sequence` and `Branch`, the only nodes the plan application
  * and losses windows hold, and leaves every other node as it is.
  *
  * Billowing Fog, Traveling Doctor and Peace Envoy rewrite the losses with
  * it, and `VictoriousNow` the recorded result. `counted` is the shared loss
  * counter: Hospital counts its user's dead with it, and Cursed Cauldron its
  * enemy's.
  */
private[campaign] object LossRewrite:
  def apply(children: Vector[Operation])(
      f: CoreOperation => Vector[CoreOperation]): Vector[Operation] =
    children.map(rewrite(_, f))

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
      returns: Boolean)(children: Vector[Operation]): Vector[Operation] =
    apply(children) {
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

  private def rewrite(operation: Operation,
      f: CoreOperation => Vector[CoreOperation]): Operation = operation match
    case ops: BuildOps => ops.copy(build = (ready, pending) =>
      ops.build(ready, pending).map(_.flatMap(f)))
    case sequence: Sequence =>
      sequence.copy(children = sequence.children.map(rewrite(_, f)))
    case branch: Branch => Branch((ready, pending) =>
      branch.select(ready, pending).map(rewrite(_, f)))
    case core: CoreOperation => f(core) match
      case Vector(same) => same
      case several => Sequence(several)
    case other => other
