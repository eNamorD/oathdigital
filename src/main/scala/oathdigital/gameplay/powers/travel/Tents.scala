package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Tents (card 29), a selected Travel modifier. Cost: 1 favor placed on the
  * card. If the destination is in the region of the pawn's current site, Travel
  * costs no Supply.
  *
  * The player owns the choice to select it. Selecting Tents places the favor at
  * the start of every Travel (the kit does), whether or not the destination is
  * in the pawn's region, and the Supply payment (terrain adds included) is
  * removed only when it is.
  */
final case class Tents private (cardId: DenizenId, catalog: ExecutableCatalog)
    extends SelectedModifier {
  def id: PowerId = Tents.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def cost: Cost = Cost(favor = Tents.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      if (sameRegion(ctx))
        TravelPayments.withoutSupply(operations, ctx.activePlayer)
      else operations)))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

  private def sameRegion(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists { route =>
      val source = TravelPayments.region(ctx.state, route.source)
      source.nonEmpty &&
        source == TravelPayments.region(ctx.state, route.destination)
    }
}

object Tents {
  val id: PowerId = PowerId("denizen.tents")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[Tents] =
    CatalogCards.denizen(catalog, id).map(new Tents(_, catalog))
}
