package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Royal Stables (card 245, site-only), a selected Travel modifier with no
  * cost, usable at the pawn's site or a site the player rules: Travel costs
  * one less Supply, never less than 1.
  *
  * It lowers the amount of the Travel's `SpendSupply`, when the Travel has
  * one. Tents and Forest Paths remove the payment entirely, so with either the
  * Travel stays free. Terrain and the other Travel powers settle the amount
  * first: they all sort at priority 0, and Royal Stables sorts after them.
  */
final case class RoyalStables private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = RoyalStables.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def priority: Int = RoyalStables.Priority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      TravelRoute.adjustSupply(operations, ctx.activePlayer)(amount =>
        (amount - RoyalStables.Reduction).max(RoyalStables.Minimum)))))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

object RoyalStables:
  val id: PowerId = PowerId("denizen.royal-stables")
  val Reduction: Int = 1
  val Minimum: Int = 1
  /** Folds after every Travel power at the default priority 0. */
  val Priority: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[RoyalStables] =
    CatalogCards.denizen(catalog, id).map(new RoyalStables(_, catalog))
