package oathdigital.gameplay.powers.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** E02, both faces (2026-09-21 Chronicle design, "Setup powers"). Each names
  * `PowerWindow.WhenExplored` alongside `SetupEnd` so the same contribution
  * will serve WHEN EXPLORED once an explore procedure exists to fire it;
  * nothing folds that window yet, so only the SETUP path runs or is tested.
  */
sealed abstract class MarketRule extends ContributingPower {
  def catalog: ExecutableCatalog
  def edifice: EdificeId
  protected def side: EdificeSide

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ready: ReadyGame): Option[SiteId] =
    EdificeSetupSupport.siteOf(ready, edifice, side)

  override def applicable(ctx: PowerCtx): Boolean = at(ctx.state).isDefined

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]]

  final def contributions: Map[PowerWindow, Vector[Contribution]] = {
    val effect = Vector(Transform((ctx, ops) => at(ctx.state) match {
      case Some(site) => ops :+ BuildOps((ready, _) => build(ready, site))
      case None => ops
    }))
    Map(PowerWindow.SetupEnd -> effect, PowerWindow.WhenExplored -> effect)
  }
}

final case class GreatMarket private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends MarketRule {
  def id: PowerId = GreatMarket.id
  protected def side: EdificeSide = EdificeSide.Intact

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] = for {
    suit <- catalog.suitOf(edifice).toRight(OathViolation.UnknownEdifice(edifice))
    region <- ready.game.current.map.regionOf(at).toRight(
      OathViolation.InvalidEventOrder(s"${at.value} is not in play"))
  } yield {
    val current = ready.game.current
    val count = current.map.inPlay.filter(s => current.map.regionOf(s).contains(region))
      .flatMap(s => current.map.sites(s).denizens).size
    if (count == 0) Vector.empty
    else Vector(Move(Piece.Favor(count),
      PositionedLocation(Location.FavorBank(suit)), PositionedLocation(Location.Site(at))))
  }
}
object GreatMarket {
  val id: PowerId = PowerId("edifice.e02.intact")
  def forCatalog(catalog: ExecutableCatalog): Option[GreatMarket] =
    CatalogCards.edifice(catalog, id).map(new GreatMarket(_, catalog))
}

final case class BanditMarket private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends MarketRule {
  def id: PowerId = BanditMarket.id
  protected def side: EdificeSide = EdificeSide.Ruined

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    catalog.suitOf(edifice).toRight(OathViolation.UnknownEdifice(edifice)).map { suit =>
      val current = ready.game.current
      val bandited = current.map.inPlay.filter(s => SiteRule.ruler(
        current.map.sites(s).forces, current.players).contains(SiteRuler.Bandits))
      val placed: Vector[CoreOperation] = bandited.map(s => Move(Piece.Favor(1),
        PositionedLocation(Location.FavorBank(suit)), PositionedLocation(Location.Site(s))))
      val burned: Vector[CoreOperation] = Suit.all.map(bank =>
        Burn.favor(1, PositionedLocation(Location.FavorBank(bank))))
      placed ++ burned
    }
}
object BanditMarket {
  val id: PowerId = PowerId("edifice.e02.ruined")
  def forCatalog(catalog: ExecutableCatalog): Option[BanditMarket] =
    CatalogCards.edifice(catalog, id).map(new BanditMarket(_, catalog))
}
