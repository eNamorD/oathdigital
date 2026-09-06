package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.operations.{Location, Move, OperationRestriction,
  Piece, PositionedLocation}
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powerresolver.CostContribution.{Add, Replace}
import oathdigital.gameplay.powers.{NarrowPassPower, ReviewedPowerCatalog,
  TravelPassBlockedCodec}
import oathdigital.model.{PlayerId, PowerId, Region, SiteId}

/** Terrain role of a site power. The fold matches these kinds (never catalog
  * handler strings) to decide route topology. Each kind owns its single
  * canonical [[CostContribution]] (Coast replaces with 1, Island adds 2,
  * Mountain adds 1, Pass contributes nothing) — the kind↔contribution mapping
  * lives here once instead of being re-declared on every site power, so a
  * mis-authored site power cannot silently alter a cost.
  */
sealed trait TravelTerrainKind extends Product with Serializable {
  def contribution: Option[CostContribution]
}
object TravelTerrainKind {
  case object Coast extends TravelTerrainKind {
    override val contribution: Option[CostContribution] =
      Some(Replace(PowerWindow.TravelCost, 1))
  }
  case object Island extends TravelTerrainKind {
    override val contribution: Option[CostContribution] =
      Some(Add(PowerWindow.TravelCost, 2))
  }
  case object Mountain extends TravelTerrainKind {
    override val contribution: Option[CostContribution] =
      Some(Add(PowerWindow.TravelCost, 1))
  }
  case object Pass extends TravelTerrainKind {
    override val contribution: Option[CostContribution] = None
  }
}

/** Sub-trait implemented only by TravelCost-window site powers (Q61-A).
  * Powers declare their typed kind; the typed cost fact follows from the kind's
  * canonical contribution, and the generic window fold reads it by
  * pattern-matching this trait.
  */
trait TravelCostTerrainPower {
  def powerId: PowerId
  def terrain: TravelTerrainKind
  def contribution: Option[CostContribution] = terrain.contribution
}

/** Route facts the TravelCost fold computes and hands to suppression
  * predicates. Powers' predicates close over their own id and ask this context
  * whether that id is the source-side coast of a coast route.
  */
final case class TravelCostWindowContext(
    sourcePowerIds: Vector[PowerId],
    destinationPowerIds: Vector[PowerId],
    override val activePowers: Vector[PowerId],
    sourceCoast: Boolean,
    destinationCoastOrIsland: Boolean
) extends WindowContext {
  /** True when `id` sits on the source site of a coast route: the source site
    * is coastal (and this is one of its powers) and the destination site is
    * coastal or an island.
    */
  def coastRouteOn(id: PowerId): Boolean =
    sourcePowerIds.contains(id) && sourceCoast && destinationCoastOrIsland
}

/** Powers-owned TravelCost window runner (not an integration mirror). It
  * computes the route context from typed terrain kinds, consults the window
  * suppression registry, and folds surviving typed contributions over the
  * module-provided base cost. Parity with the retired RuntimeRuleRegistry
  * path:
  *
  *   - Coast route (source coastal AND destination coastal-or-island): a
  *     source-side Coast replaces the cost with its amount (1); the
  *     destination's Island/Mountain adds are structurally excluded by this
  *     branch. Coast powers additionally register their suppression rule in
  *     [[SuppressionRegistry]]; with one terrain power per site the branch
  *     already implements the ignore, so the registry query is a registered
  *     seam for future multi-power windows rather than the deciding mechanism
  *     today (it is exercised by wiring tests).
  *   - Ordinary route: destination Island (+2) and Mountain (+1) contributions
  *     add onto the base; a destination Coast contributes nothing (its Replace
  *     is meaningful only as the source-side trigger of a coast route).
  *
  * No catalog-ID switch: registered powers are matched by their typed
  * [[TravelCostTerrainPower]] sub-trait, whose contribution is the kind's
  * canonical fact.
  */
object TravelCostWindow {
  def fold(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      source: SiteId,
      destination: SiteId,
      baseCost: Int
  ): Int = {
    val registry = ReviewedPowerCatalog.registry(catalog).toOption
    val lookup: PowerId => Option[TravelCostTerrainPower] = id =>
      registry.flatMap(_.lookup(id))
        .collect { case power: TravelCostTerrainPower => power }
    def sitePowers(site: SiteId): Vector[PowerId] =
      catalog.sites.find(_.id == site).toVector.flatMap(_.handlers)
        .map(PowerId).filter(id => lookup(id).nonEmpty)
    val sourceIds = sitePowers(source)
    val destinationIds = sitePowers(destination)
    def kinds(ids: Vector[PowerId]): Vector[TravelTerrainKind] =
      ids.flatMap(lookup).map(_.terrain)
    val sourceKinds = kinds(sourceIds)
    val destinationKinds = kinds(destinationIds)
    val sourceCoast = sourceKinds.contains(TravelTerrainKind.Coast)
    val destinationCoastOrIsland = destinationKinds.exists(kind =>
      kind == TravelTerrainKind.Coast || kind == TravelTerrainKind.Island)
    val context = TravelCostWindowContext(sourceIds, destinationIds,
      (sourceIds ++ destinationIds).distinct, sourceCoast,
      destinationCoastOrIsland)
    val suppressed = SuppressionRegistry.suppressed(PowerWindow.TravelCost,
      context.activePowers, context)
    if (context.sourceCoast && context.destinationCoastOrIsland)
      // Coast route: Replace honors the surviving source-side Coast amount.
      sourceIds.filterNot(suppressed.contains).iterator.flatMap(lookup)
        .collectFirst {
          case power if power.terrain == TravelTerrainKind.Coast =>
            power.contribution.collect {
              case Replace(_, amount) => amount
            }.getOrElse(baseCost)
        }.getOrElse(baseCost)
    else
      // Ordinary route: only destination Island/Mountain Add contributions.
      baseCost + destinationIds.filterNot(suppressed.contains).iterator
        .flatMap(lookup).foldLeft(0) {
          case (sum, power) if power.terrain == TravelTerrainKind.Island ||
              power.terrain == TravelTerrainKind.Mountain =>
            power.contribution match {
              case Some(Add(_, amount)) => sum + amount
              case _ => sum
            }
          case (sum, _) => sum
        }
  }

  /** True when traveling between `source` and `destination` is a coast route:
    * the source site is coastal and the destination site is coastal or an
    * island. Used by legality to skip pass candidates (coast route ignores
    * passes).
    */
  def isCoastRoute(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      source: SiteId,
      destination: SiteId
  ): Boolean = {
    val registry = ReviewedPowerCatalog.registry(catalog).toOption
    val lookup: PowerId => Option[TravelCostTerrainPower] = id =>
      registry.flatMap(_.lookup(id))
        .collect { case power: TravelCostTerrainPower => power }
    def kinds(site: SiteId): Vector[TravelTerrainKind] =
      catalog.sites.find(_.id == site).toVector.flatMap(_.handlers)
        .map(PowerId).flatMap(lookup).map(_.terrain)
    val sourceKinds = kinds(source)
    val destinationKinds = kinds(destination)
    sourceKinds.contains(TravelTerrainKind.Coast) &&
      destinationKinds.exists(kind => kind == TravelTerrainKind.Coast ||
        kind == TravelTerrainKind.Island)
  }
}

/** Travel legality (module-bound action wrapper, Q53): evaluates Narrow Pass
  * power restrictions against a simulated pawn-Move op. Candidate selection is
  * module-owned (pass sites in the destination region, destination != pass,
  * crossing regions, not a coast route); each candidate pass power's own
  * restriction body decides ruler/allow logic. The encoded reason detail is
  * decoded back to the typed [[OathViolation.TravelPassBlocked]].
  */
object TravelCostLegality {
  def blocked(
      catalog: ExecutableCatalog,
      ready: ReadyGame,
      player: PlayerId,
      source: SiteId,
      destination: SiteId
  ): Option[OathViolation] = {
    val map = ready.game.current.map
    val toRegion: Option[Region] = map.regionOf(destination)
    val fromRegion: Option[Region] = map.regionOf(source)
    val crossRegion = fromRegion.isDefined && toRegion.isDefined &&
      fromRegion != toRegion
    val coastRoute = TravelCostWindow.isCoastRoute(
      catalog, ready, source, destination)
    if (!crossRegion || coastRoute) None
    else {
      val op = Move(Piece.Pawn(player),
        PositionedLocation(Location.Site(source)),
        PositionedLocation(Location.Site(destination)))
      val registry = ReviewedPowerCatalog.registry(catalog).toOption
      val reasons = passCandidates(catalog, ready, toRegion).iterator.flatMap {
        passSite =>
          registry.toVector.flatMap(r => passPower(r, catalog, passSite)
            .toVector.flatMap(_.restrictionFor(passSite).reason(ready, op)))
      }.toVector
      reasons.headOption.flatMap(TravelPassBlockedCodec.decode)
    }
  }

  private def passCandidates(catalog: ExecutableCatalog, ready: ReadyGame,
      toRegion: Option[Region]): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(site =>
      ready.game.current.map.regionOf(site) == toRegion &&
        passPowerAt(catalog, ready, site))

  private def passPowerAt(catalog: ExecutableCatalog, ready: ReadyGame,
      site: SiteId): Boolean = {
    val registry = ReviewedPowerCatalog.registry(catalog).toOption
    catalog.sites.find(_.id == site).toVector.flatMap(_.handlers)
      .map(PowerId).exists(id => registry.exists(_.lookup(id).exists {
        case _: NarrowPassPower => true
        case _ => false
      }))
  }

  private def passPower(
      registry: oathdigital.gameplay.powerresolver.PowerRegistry,
      catalog: ExecutableCatalog,
      site: SiteId
  ): Option[NarrowPassPower] =
    catalog.sites.find(_.id == site).toVector.flatMap(_.handlers)
      .map(PowerId).flatMap(id => registry.lookup(id))
      .collectFirst { case power: NarrowPassPower => power }
}
