package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** A When Played power of one denizen. When that card is played, `effect`'s
  * operations run after whatever other powers contributed at the
  * card-played window.
  *
  * `effect` is called on every fold, and the walker folds again on every
  * command, so it must be a pure function of the state in `ctx`. Anything
  * that depends on state an earlier operation changes belongs inside a
  * `BuildOps`, `Branch` or `Repeat` it returns, which the walker evaluates
  * at walk time.
  */
trait WhenPlayedPower extends ContributingPower {
  def cardId: DenizenId
  def effect(ctx: PowerCtx): Vector[Operation]

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  final override def applicable(ctx: PowerCtx): Boolean = ctx.operation match {
    case CardPlayedFaceup(card, _) => card == cardId
    case _ => false
  }

  final def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      children ++ effect(ctx))))
}

object WhenPlayedPower {
  /** The denizen that prints power `id`, or `None` for a catalog without it. */
  def cardOf(catalog: ExecutableCatalog, id: PowerId): Option[DenizenId] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(definition => DenizenId(definition.id.value))
}
