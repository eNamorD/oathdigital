package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, SelectedModifier}
import oathdigital.model._

/** Forest Paths (card 43), a selected Travel modifier. Cost: 1 favor placed on
  * the card. If the destination holds a beast denizen or edifice, Travel costs
  * no Supply and the powers of sites are ignored for that Travel.
  *
  * The player owns the choice to select it. Selecting Forest Paths places the
  * favor at the start of every Travel (the kit does). Only when the destination
  * holds a beast card does it remove the Supply payment and ignore the site
  * powers.
  *
  * Ignoring a power is a named ignore, decided per Travel (`ignores` reads the
  * route from the context): while the destination qualifies, every power whose
  * source is a site is dropped from the fold. The terrain adds and the Narrow
  * Pass restriction both hook a Travel window, so Forest Paths hooks both
  * windows too (the second with a transform that changes nothing), because the
  * collector only lets a power ignore what is gathered beside it.
  */
final case class ForestPaths private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = ForestPaths.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def cost: Cost = Cost(favor = ForestPaths.Favor)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      if (beastAtDestination(ctx))
        TravelPayments.withoutSupply(operations, ctx.activePlayer)
      else operations)),
    PowerWindow.TravelActionEligibility ->
      Vector(Transform((_, operations) => operations)))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

  private def beastAtDestination(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(route =>
      TravelPayments.holdsSuit(ctx.state, route.destination, Suit.Beast,
        catalog.suitOf(_)))

  override def ignores(ctx: PowerCtx, other: ContributingPower): Boolean =
    beastAtDestination(ctx) && (other.source match {
      case _: RuleSourceRef.Site => true
      case _ => false
    })
}

object ForestPaths {
  val id: PowerId = PowerId("denizen.forest-paths")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[ForestPaths] =
    CatalogCards.denizen(catalog, id).map(new ForestPaths(_, catalog))
}
