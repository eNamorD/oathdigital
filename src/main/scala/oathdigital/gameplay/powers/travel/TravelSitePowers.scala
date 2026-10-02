package oathdigital.gameplay.powers.travel

import oathdigital.catalog.Site
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.wake.{HeadwatersSite, TidalMarshesSite}
import oathdigital.model.{DecisionOptionRef, Location, Move, NoteArg, NoteKey,
  NotePart, OathViolation, Operation, Piece, PlayerId, PositionedLocation,
  PowerId, PowerNote, PowerSourceRef, PowerWindow, RuleSourceRef, SiteId,
  SiteRule, SiteRuler, SpendSupply, Tokens}

object BrokenPeaksSite extends Site(SiteId("site:broken-peaks"), "Broken Peaks",
    defense = 2, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 2),
    forgeRequirements = None,
    homeland = None):
  val mountain = PowerId("site.broken-peaks.mountain")
  val handlers: Vector[PowerId] = Vector(mountain)

object DesolateShoreSite extends Site(SiteId("site:desolate-shore"), "Desolate Shore",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(5),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.desolate-shore.coast")
  val handlers: Vector[PowerId] = Vector(coast)

object FairIsleSite extends Site(SiteId("site:fair-isle"), "Fair Isle",
    defense = 2, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(3),
    startingResources = Tokens(3, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.fair-isle.coast")
  val island = PowerId("site.fair-isle.island")
  val handlers: Vector[PowerId] = Vector(coast, island)

object GreenShoreSite extends Site(SiteId("site:green-shore"), "Green Shore",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.green-shore.coast")
  val handlers: Vector[PowerId] = Vector(coast)

object HiddenPlaceSite extends Site(SiteId("site:hidden-place"), "Hidden Place",
    defense = 2, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val mountain = PowerId("site.hidden-place.mountain")
  val handlers: Vector[PowerId] = Vector(mountain)

object MinesSite extends Site(SiteId("site:mines"), "Mines",
    defense = 1, capacity = 1, relicSlots = 2,
    recoverDifficulty = Some(5),
    startingResources = Tokens(3, 0),
    forgeRequirements = None,
    homeland = None):
  val mountain = PowerId("site.mines.mountain")
  val handlers: Vector[PowerId] = Vector(mountain)

object NarrowPassSite extends Site(SiteId("site:narrow-pass"), "Narrow Pass",
    defense = 2, capacity = 1, relicSlots = 1,
    recoverDifficulty = Some(5),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val pass = PowerId("site.narrow-pass.pass")
  val handlers: Vector[PowerId] = Vector(pass)

object RockyCoastSite extends Site(SiteId("site:rocky-coast"), "Rocky Coast",
    defense = 2, capacity = 1, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.rocky-coast.coast")
  val handlers: Vector[PowerId] = Vector(coast)

object SunkenIslesSite extends Site(SiteId("site:sunken-isles"), "Sunken Isles",
    defense = 2, capacity = 0, relicSlots = 3,
    recoverDifficulty = Some(6),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val coast = PowerId("site.sunken-isles.coast")
  val island = PowerId("site.sunken-isles.island")
  val handlers: Vector[PowerId] = Vector(coast, island)

private[travel] object TravelRoute:
  final case class PawnMove(player: PlayerId, source: SiteId, destination: SiteId)

  def pawnMove(operation: Operation): Option[PawnMove] =
    Operation.flatten(operation).collectFirst:
      case Move(Piece.Pawn(player), PositionedLocation(Location.Site(source), _),
          PositionedLocation(Location.Site(destination), _), _) =>
        PawnMove(player, source, destination)

  def adjustSupply(operations: Vector[Operation], actor: PlayerId)
      (rewrite: Int => Int): Vector[Operation] =
    operations.map:
      case payment @ SpendSupply(player, amount, _) if player == actor =>
        payment.copy(amount = rewrite(amount))
      case operation => operation

final case class MountainSitePower(id: PowerId, site: SiteId)
    extends ContributingPower:
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelCost,
      Vector(Transform((ctx, operations) =>
        TravelRoute.adjustSupply(operations, ctx.activePlayer)(_ + 1))))
  override def applicable(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(_.destination == site)

final case class IslandSitePower(id: PowerId, site: SiteId)
    extends ContributingPower:
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelCost,
      Vector(Transform((ctx, operations) =>
        TravelRoute.adjustSupply(operations, ctx.activePlayer)(_ + 2))))
  override def applicable(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(_.destination == site)

final case class CoastSitePower(id: PowerId, site: SiteId,
    coastOrIslandSites: Set[SiteId]) extends ContributingPower:
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map.empty.updated(PowerWindow.TravelCost,
      Vector(Transform((ctx, operations) =>
        TravelRoute.adjustSupply(operations, ctx.activePlayer)(_ => 1))))
  override def applicable(ctx: PowerCtx): Boolean = TravelRoute
    .pawnMove(ctx.operation).exists(route => route.source == site &&
      coastOrIslandSites.contains(route.destination))
  override def shouldIgnore(other: ContributingPower): Boolean = other match
    case _: MountainSitePower | _: IslandSitePower => true
    case _ => false

final case class NarrowPassSitePower(id: PowerId, site: SiteId,
    coastSites: Set[SiteId], coastOrIslandSites: Set[SiteId])
    extends ContributingPower:
  def source: RuleSourceRef = RuleSourceRef.Site(site)
  override def noteKeys: Vector[NoteKey] = Vector(NarrowPassSitePower.noTarget)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx))),
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(campaignBlocked, campaignNote)))

  /** A hidden target site's line. A Travel the Pass blocks is refused whole
    * and writes nothing (power log lines design, ruling 3). */
  private def campaignNote(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] = Some(NarrowPassSitePower.noTarget(
    PowerSourceRef.Site(site), NoteArg.Player(ctx.activePlayer)))

  override def applicable(ctx: PowerCtx): Boolean = ctx.window match
    // Per candidate, in `campaignBlocked`: nothing about the tree decides it.
    case PowerWindow.CampaignTargetSelection => true
    case _ => TravelRoute.pawnMove(ctx.operation).exists { route =>
      val map = ctx.state.game.current.map
      (for
        sourceRegion <- map.regionOf(route.source)
        destinationRegion <- map.regionOf(route.destination)
        passRegion <- map.regionOf(site)
      yield sourceRegion != destinationRegion && destinationRegion == passRegion &&
        route.destination != site).getOrElse(false)
    }

  /** Campaign: "If your pawn is outside this region, you cannot ... target
    * other sites in this region in campaigns, unless you have the consent of
    * the Pass's ruler." Judged per candidate site, never against the other
    * targets. Consent is approximated as ruling the Pass, as it was before
    * this was a power. Only site options are considered: a Raid targets a
    * pawn, relics and banners, never a site.
    */
  private def campaignBlocked(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match
    case DecisionOptionRef.Site(target) =>
      val current = ctx.state.game.current
      val map = current.map
      val outside = for
        pawn <- current.players.find(_.player == ctx.activePlayer)
          .flatMap(_.pawnSite)
        pawnRegion <- map.regionOf(pawn)
        targetRegion <- map.regionOf(target)
        passRegion <- map.regionOf(site)
      yield pawnRegion != targetRegion && targetRegion == passRegion &&
        target != site
      val ruledByActor = map.sites.get(site).exists(pass =>
        SiteRule.ruler(pass.forces, current.players) match {
          case Right(SiteRuler.Player(player)) => player == ctx.activePlayer
          case _ => false
        })
      Option.when(outside.contains(true) && !ruledByActor)(
        OathViolation.CampaignUnavailable(
          s"a Pass prevents targeting '${target.value}' from the pawn site"))
    case _ => None

  private def blocked(ctx: PowerCtx): Option[OathViolation] =
    TravelRoute.pawnMove(ctx.operation).flatMap { route =>
      val map = ctx.state.game.current.map
      val coastRoute = coastSites.contains(route.source) &&
        coastOrIslandSites.contains(route.destination)
      if coastRoute then None
      else SiteRule.ruler(map.sites(site).forces, ctx.state.game.current.players) match
        case Right(SiteRuler.Player(player)) if player == route.player => None
        case Right(_) | Left(_) =>
          Some(OathViolation.TravelPassBlocked(site, route.destination))
    }

object NarrowPassSitePower:
  /** "{Red} cannot target other sites in the region." */
  val noTarget: NoteKey = NoteKey("no-target", Vector(NotePart.Arg(0),
    NotePart.Text(" cannot target other sites in the region.")))

/** The sites' Travel terrain contributions. Static site topology lives on
  * the power objects; command state supplies only the current route through
  * PowerCtx.operation.
  */
object TravelSitePowers:
  private enum Terrain { case Mountain, Island, Coast, NarrowPass }
  import Terrain._
  private final case class Supported(id: PowerId, site: SiteId,
      terrain: Terrain)

  /** Explicit reviewed Travel handlers, in power id order. A power comes
    * only from this list, never from a `*.coast`-looking handler id.
    */
  private val supported = Vector(
    Supported(BrokenPeaksSite.mountain, BrokenPeaksSite.id, Mountain),
    Supported(DesolateShoreSite.coast, DesolateShoreSite.id, Coast),
    Supported(FairIsleSite.coast, FairIsleSite.id, Coast),
    Supported(FairIsleSite.island, FairIsleSite.id, Island),
    Supported(GreenShoreSite.coast, GreenShoreSite.id, Coast),
    Supported(HeadwatersSite.mountain, HeadwatersSite.id, Mountain),
    Supported(HiddenPlaceSite.mountain, HiddenPlaceSite.id, Mountain),
    Supported(MinesSite.mountain, MinesSite.id, Mountain),
    Supported(NarrowPassSite.pass, NarrowPassSite.id, NarrowPass),
    Supported(RockyCoastSite.coast, RockyCoastSite.id, Coast),
    Supported(SunkenIslesSite.coast, SunkenIslesSite.id, Coast),
    Supported(SunkenIslesSite.island, SunkenIslesSite.id, Island),
    Supported(TidalMarshesSite.coast, TidalMarshesSite.id, Coast))
  private val coastSites = supported.collect {
    case Supported(_, site, Coast) => site
  }.toSet
  private val coastOrIslandSites = supported.collect {
    case Supported(_, site, Coast | Island) => site
  }.toSet

  val all: Vector[ContributingPower] = supported.map:
    case Supported(id, site, Mountain) => MountainSitePower(id, site)
    case Supported(id, site, Island) => IslandSitePower(id, site)
    case Supported(id, site, Coast) =>
      CoastSitePower(id, site, coastOrIslandSites)
    case Supported(id, site, NarrowPass) =>
      NarrowPassSitePower(id, site, coastSites, coastOrIslandSites)
