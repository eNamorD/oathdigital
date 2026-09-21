package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Grasping Vines (card 178), a persistent rule of a faceup site card: an enemy
  * traveling from a site ruled by the Vines' ruler kills one warband on their
  * own board if able. The Vines' ruler is the ruler of the site it stands at,
  * and that ruler is exempt.
  *
  * The kill is unconditional and not required: it is a plain `Kill` placed
  * before the pawn's move, which does nothing for a board with no warband, so a
  * traveller with none is not stopped.
  */
final case class GraspingVines private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = GraspingVines.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      kill(ctx).fold(operations)(_ +: operations))))

  override def applicable(ctx: PowerCtx): Boolean = kill(ctx).nonEmpty

  private def kill(ctx: PowerCtx): Option[CoreOperation] = for {
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- TravelRulers.rulerOfCard(ctx.state, cardId)
    if TravelRulers.isEnemy(ruler, route.player)
    if TravelRulers.rulerOf(ctx.state, route.source).contains(ruler)
    warband <- TravelPayments.ownWarband(ctx.state, route.player,
      GraspingVines.Warbands)
  } yield Kill(warband, PositionedLocation(Location.PlayArea(route.player)))
}

object GraspingVines {
  val id: PowerId = PowerId("denizen.grasping-vines")
  val Warbands: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[GraspingVines] =
    CatalogCards.denizen(catalog, id).map(new GraspingVines(_, catalog))
}
