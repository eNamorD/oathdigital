package oathdigital.testkit

import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx,
  Transform}
import oathdigital.model._

/** The `AutomaticUse` nodes a power's fold appends (catalog batch 8, slice
  * 10, part 2), read straight from its transforms, so a suite can pin which
  * power, card and user the walker will judge without walking a game. */
object AppendedAutomaticUses:
  /** One appended node's user, power and card. */
  final case class Named(user: PlayerId, power: PowerId, card: CardId)

  /** Each `AutomaticUse` that `power`'s transforms at `window` append to
    * `operation`'s children, folded for `actor` on an empty table. A
    * windowed leaf folds itself, as the walker folds it. */
  def at(power: ContributingPower, window: PowerWindow, operation: Operation,
      actor: PlayerId = Table.p1): Vector[Named] =
    val ctx = PowerCtx(Table.start.ready, actor, power.source, window,
      Vector.empty, operation)
    val children = operation match
      case leaf: LeafOperation => Vector(leaf)
      case other => other.children
    power.contributions.getOrElse(window, Vector.empty)
      .collect { case transform: Transform => transform }
      .foldLeft(children)((ops, transform) => transform.fn(ctx, ops))
      .drop(children.size)
      .collect { case AutomaticUse(user, used, card, _) =>
        Named(user, used, card) }
