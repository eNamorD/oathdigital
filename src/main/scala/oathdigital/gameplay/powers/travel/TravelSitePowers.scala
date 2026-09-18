package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.model.{Location, Move, OathViolation, Operation, Piece, PlayerId, PositionedLocation, PowerId, PowerWindow, RuleSourceRef, SiteId, SiteRule, SiteRuler, SpendSupply}

private[travel] object TravelRoute {
  final case class PawnMove(player: PlayerId, source: SiteId, destination: SiteId)

  def pawnMove(operation: Operation): Option[PawnMove] =
    Operation.flatten(operation).collectFirst {
      case Move(Piece.Pawn(player), PositionedLocation(Location.Site(source), _),
          PositionedLocation(Location.Site(destination), _), _) =>
        PawnMove(player, source, destination)
    }

  def adjustSupply(operations: Vector[Operation], actor: PlayerId)
      (rewrite: Int => Int): Vector[Operation] =
    operations.map {
      case payment @ SpendSupply(player, amount, _) if player == actor =>
        payment.copy(amount = rewrite(amount))
      case operation => operation
    }
}

final case class MountainSitePower(id: PowerId, site: SiteId)
    extends ContributingPower {
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelCost,
      Vector(Transform((ctx, operations) =>
        TravelRoute.adjustSupply(operations, ctx.activePlayer)(_ + 1))))
  override def applicable(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(_.destination == site)
}

final case class IslandSitePower(id: PowerId, site: SiteId)
    extends ContributingPower {
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelCost,
      Vector(Transform((ctx, operations) =>
        TravelRoute.adjustSupply(operations, ctx.activePlayer)(_ + 2))))
  override def applicable(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(_.destination == site)
}

final case class CoastSitePower(id: PowerId, site: SiteId,
    coastOrIslandSites: Set[SiteId]) extends ContributingPower {
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelCost,
      Vector(Transform((ctx, operations) =>
        TravelRoute.adjustSupply(operations, ctx.activePlayer)(_ => 1))))
  override def applicable(ctx: PowerCtx): Boolean = TravelRoute
    .pawnMove(ctx.operation).exists(route => route.source == site &&
      coastOrIslandSites.contains(route.destination))
  override def shouldIgnore(other: ContributingPower): Boolean = other match {
    case _: MountainSitePower | _: IslandSitePower => true
    case _ => false
  }
}

final case class NarrowPassSitePower(id: PowerId, site: SiteId,
    coastSites: Set[SiteId], coastOrIslandSites: Set[SiteId])
    extends ContributingPower {
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelActionEligibility,
      Vector(Restriction((ctx, _) => blocked(ctx))))

  override def applicable(ctx: PowerCtx): Boolean = TravelRoute
    .pawnMove(ctx.operation).exists { route =>
      val map = ctx.state.game.current.map
      (for {
        sourceRegion <- map.regionOf(route.source)
        destinationRegion <- map.regionOf(route.destination)
        passRegion <- map.regionOf(site)
      } yield sourceRegion != destinationRegion && destinationRegion == passRegion &&
        route.destination != site).getOrElse(false)
    }

  private def blocked(ctx: PowerCtx): Option[OathViolation] =
    TravelRoute.pawnMove(ctx.operation).flatMap { route =>
      val map = ctx.state.game.current.map
      val coastRoute = coastSites.contains(route.source) &&
        coastOrIslandSites.contains(route.destination)
      if (coastRoute) None
      else SiteRule.ruler(map.sites(site).forces, ctx.state.game.current.players) match {
        case Right(SiteRuler.Player(player)) if player == route.player => None
        case Right(_) | Left(_) =>
          Some(OathViolation.TravelPassBlocked(site, route.destination))
        }
    }
}

/** Catalog-bound site contributions for Travel's terrain rules. Static site
  * topology lives on the power objects; command state supplies only the
  * current route through PowerCtx.operation.
  */
object TravelSitePowers {
  private sealed trait Terrain
  private case object Mountain extends Terrain
  private case object Island extends Terrain
  private case object Coast extends Terrain
  private case object NarrowPass extends Terrain
  private final case class Supported(id: PowerId, terrain: Terrain)

  /** Explicit reviewed Travel handlers. A catalog may contain unrelated or
    * future `*.coast`-looking ids; only a reviewed descriptor becomes a power.
    */
  private val supported = Vector(
    Supported(PowerId("site.broken-peaks.mountain"), Mountain),
    Supported(PowerId("site.desolate-shore.coast"), Coast),
    Supported(PowerId("site.fair-isle.coast"), Coast),
    Supported(PowerId("site.fair-isle.island"), Island),
    Supported(PowerId("site.green-shore.coast"), Coast),
    Supported(PowerId("site.headwaters.mountain"), Mountain),
    Supported(PowerId("site.hidden-place.mountain"), Mountain),
    Supported(PowerId("site.mines.mountain"), Mountain),
    Supported(PowerId("site.narrow-pass.pass"), NarrowPass),
    Supported(PowerId("site.rocky-coast.coast"), Coast),
    Supported(PowerId("site.sunken-isles.coast"), Coast),
    Supported(PowerId("site.sunken-isles.island"), Island),
    Supported(PowerId("site.tidal-marshes.coast"), Coast)
  )

  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] = {
    val present = supported.foldLeft(Vector.empty[(Supported, SiteId)]) {
      (found, descriptor) =>
        catalog.sites.find(_.handlers.contains(descriptor.id.value)) match {
          case Some(site) => found :+ (descriptor -> site.id)
          case None => found
        }
    }
    val coastSites = present.collect {
      case (Supported(_, Coast), site) => site
    }.toSet
    val coastOrIslandSites = present.collect {
      case (Supported(_, Coast | Island), site) => site
    }.toSet
    present.map {
      case (Supported(id, Mountain), site) => MountainSitePower(id, site)
      case (Supported(id, Island), site) => IslandSitePower(id, site)
      case (Supported(id, Coast), site) =>
        CoastSitePower(id, site, coastOrIslandSites)
      case (Supported(id, NarrowPass), site) =>
        NarrowPassSitePower(id, site, coastSites, coastOrIslandSites)
    }
  }
}
