package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, RuleSourceRef}
import oathdigital.gameplay.operations.{AdjustSupply, Location, Move, Operation,
  Piece, PositionedLocation}
import oathdigital.gameplay.powerresolver._
import oathdigital.model.{PlayerId, PowerId, SiteId, SiteRule, SiteRuler}

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
      case payment @ AdjustSupply(player, amount) if player == actor =>
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
        TravelRoute.adjustSupply(operations, ctx.actor)(_ - 1))))
  override def applicable(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(_.destination == site)
}

final case class IslandSitePower(id: PowerId, site: SiteId)
    extends ContributingPower {
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelCost,
      Vector(Transform((ctx, operations) =>
        TravelRoute.adjustSupply(operations, ctx.actor)(_ - 2))))
  override def applicable(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(_.destination == site)
}

final case class CoastSitePower(id: PowerId, site: SiteId,
    coastOrIslandSites: Set[SiteId]) extends ContributingPower {
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelCost,
      Vector(Transform((ctx, operations) =>
        TravelRoute.adjustSupply(operations, ctx.actor)(_ => -1))))
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
      else SiteRule.ruler(map.sites(site).forces, ctx.state.game.current.players)
        .toOption.collect {
          case ruler if ruler != SiteRuler.Player(route.player) =>
            OathViolation.TravelPassBlocked(site, route.destination)
        }
    }
}

/** Catalog-bound site contributions for Travel's terrain rules. Static site
  * topology lives on the power objects; command state supplies only the
  * current route through PowerCtx.operation.
  */
object TravelSitePowers {
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] = {
    def powersAt(suffix: String): Vector[(PowerId, SiteId)] = catalog.sites
      .foldLeft(Vector.empty[(PowerId, SiteId)]) { (found, site) =>
        found ++ site.handlers.filter(_.endsWith(suffix)).map(handler =>
          PowerId(handler) -> site.id)
      }
    val coasts = powersAt(".coast")
    val islands = powersAt(".island")
    val coastOrIslandSites = (coasts.map(_._2) ++ islands.map(_._2)).toSet
    val mountains = powersAt(".mountain").map { case (id, site) =>
      MountainSitePower(id, site)
    }
    val islandPowers = islands.map { case (id, site) => IslandSitePower(id, site) }
    val coastPowers = coasts.map { case (id, site) =>
      CoastSitePower(id, site, coastOrIslandSites)
    }
    val passes = powersAt(".pass").collect {
      case (id @ PowerId("site.narrow-pass.pass"), site) =>
        NarrowPassSitePower(id, site, coasts.map(_._2).toSet, coastOrIslandSites)
    }
    mountains ++ islandPowers ++ coastPowers ++ passes
  }
}
