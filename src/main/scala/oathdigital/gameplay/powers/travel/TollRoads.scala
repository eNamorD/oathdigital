package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Toll Roads (card 118), a persistent rule of a faceup site card: enemies
  * cannot travel to a site ruled by Toll Roads' ruler unless they give 1 favor
  * to that ruler, or burn it when bandits rule. Toll Roads' ruler is the ruler
  * of the site it stands at, and the rule covers every site that ruler holds,
  * Toll Roads' own included. Empire rulers are not supported.
  *
  * The payment is a required operation placed before the pawn's move, so a
  * traveller who cannot pay is rejected, and Travel's destination list does not
  * offer that destination. The ruler is exempt.
  */
final case class TollRoads private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = TollRoads.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      toll(ctx).fold(operations)(_ +: operations))))

  override def applicable(ctx: PowerCtx): Boolean = toll(ctx).nonEmpty

  /** The payment this Travel owes, if it owes one. */
  private def toll(ctx: PowerCtx): Option[CoreOperation] = for {
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- TravelRulers.rulerOfCard(ctx.state, cardId)
    if TravelRulers.isEnemy(ruler, route.player)
    if TravelRulers.rulerOf(ctx.state, route.destination).contains(ruler)
  } yield ruler match {
    case SiteRuler.Player(owner) => Give(Piece.Favor(TollRoads.Favor),
      route.player, Location.PlayArea(route.player),
      Location.PlayArea(owner), required = true)
    case _ => PayCost(route.player, Location.SharedBank,
      Cost(favorBurnt = TollRoads.Favor))
  }
}

object TollRoads {
  val id: PowerId = PowerId("denizen.toll-roads")
  val Favor: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[TollRoads] =
    CatalogCards.denizen(catalog, id).map(new TollRoads(_, catalog))
}
