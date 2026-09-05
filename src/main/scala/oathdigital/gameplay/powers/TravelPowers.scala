package oathdigital.gameplay.powers

import oathdigital.gameplay._
import oathdigital.gameplay.operations.{Move, OperationReason,
  OperationRestriction, Piece}
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powerresolver.CostContribution.{Add, Replace}
import oathdigital.gameplay.powerresolver.SuppressionRegistry
import oathdigital.gameplay.powers.travel._
import oathdigital.model.{PlayerId, PowerId, SiteId, SiteRule, SiteRuler}
import SiteRuler._

/** TravelCost-window site terrain powers. Each declares a typed terrain kind
  * and a typed cost fact; the powers-owned [[TravelCostWindow]] fold reads them
  * (sub-trait accessor pattern — no new PowerHandler method). Coast powers
  * register their generic suppression rule: on a coast route (source coastal
  * and destination coastal-or-island) the source coast ignores
  * Island/Mountain/Pass. Narrow Pass carries no cost but exposes a restriction
  * factory evaluated by Travel legality (module-bound action, Q53); the
  * restriction body lives on the power (Q28-A).
  */
object TravelPowers {
  private val modifier = Some(MajorActionType.Travel)
  private def topology = Vector(ReviewedHandler.automatic(PowerWindow.TravelCost,
    implemented = true))

  private final case class TerrainPower(
      idValue: String,
      override val terrain: TravelTerrainKind,
      override val contribution: Option[CostContribution]
  ) extends ReviewedPower(idValue, modifier, topology)
      with TravelCostTerrainPower {
    override val powerId: PowerId = id
  }

  private val coast = TravelTerrainKind.Coast

  val BrokenPeaksMountain: TravelCostTerrainPower with Power = TerrainPower(
    "site.broken-peaks.mountain", TravelTerrainKind.Mountain,
    Some(Add(PowerWindow.TravelCost, 1)))
  val DesolateShoreCoast: TravelCostTerrainPower with Power = TerrainPower(
    "site.desolate-shore.coast", coast, Some(Replace(PowerWindow.TravelCost, 1)))
  val FairIsleCoast: TravelCostTerrainPower with Power = TerrainPower(
    "site.fair-isle.coast", coast, Some(Replace(PowerWindow.TravelCost, 1)))
  val FairIsleIsland: TravelCostTerrainPower with Power = TerrainPower(
    "site.fair-isle.island", TravelTerrainKind.Island,
    Some(Add(PowerWindow.TravelCost, 2)))
  val GreenShoreCoast: TravelCostTerrainPower with Power = TerrainPower(
    "site.green-shore.coast", coast, Some(Replace(PowerWindow.TravelCost, 1)))
  val HeadwatersMountain: TravelCostTerrainPower with Power = TerrainPower(
    "site.headwaters.mountain", TravelTerrainKind.Mountain,
    Some(Add(PowerWindow.TravelCost, 1)))
  val HiddenPlaceMountain: TravelCostTerrainPower with Power = TerrainPower(
    "site.hidden-place.mountain", TravelTerrainKind.Mountain,
    Some(Add(PowerWindow.TravelCost, 1)))
  val MinesMountain: TravelCostTerrainPower with Power = TerrainPower(
    "site.mines.mountain", TravelTerrainKind.Mountain,
    Some(Add(PowerWindow.TravelCost, 1)))
  val RockyCoast: TravelCostTerrainPower with Power = TerrainPower(
    "site.rocky-coast.coast", coast, Some(Replace(PowerWindow.TravelCost, 1)))
  val SunkenIslesCoast: TravelCostTerrainPower with Power = TerrainPower(
    "site.sunken-isles.coast", coast, Some(Replace(PowerWindow.TravelCost, 1)))
  val SunkenIslesIsland: TravelCostTerrainPower with Power = TerrainPower(
    "site.sunken-isles.island", TravelTerrainKind.Island,
    Some(Add(PowerWindow.TravelCost, 2)))
  val TidalMarshesCoast: TravelCostTerrainPower with Power = TerrainPower(
    "site.tidal-marshes.coast", coast, Some(Replace(PowerWindow.TravelCost, 1)))

  private val terrainPowers: Vector[TravelCostTerrainPower with Power] =
    Vector(BrokenPeaksMountain, DesolateShoreCoast, FairIsleCoast,
      FairIsleIsland, GreenShoreCoast, HeadwatersMountain, HiddenPlaceMountain,
      MinesMountain, RockyCoast, SunkenIslesCoast, SunkenIslesIsland,
      TidalMarshesCoast)

  val NarrowPass: Power with NarrowPassPower = NarrowPassPowerImpl()

  /** Generic coast-route suppression: while the source coast applies, every
    * other TravelCost terrain contribution (Island/Mountain/Pass) is ignored.
    * Registered once per coast id so the fold's active-set query fires on any
    * coastal source.
    */
  private val ignoredTerrain: Vector[PowerId] = terrainPowers.collect {
    case power if power.terrain != coast => power.powerId
  }
  terrainPowers.collect {
    case power if power.terrain == coast =>
      SuppressionRegistry.register(PowerWindow.TravelCost, power.powerId,
        ignoredTerrain)(context => context match {
          case route: TravelCostWindowContext => route.coastRouteOn(power.powerId)
          case _ => false
        })
  }

  val powers: Vector[Power] = terrainPowers :+ NarrowPass
}

/** Restriction seam for the Narrow Pass power. The factory is bound to the
  * pass site by Travel legality (module-owned candidate selection); each
  * returned restriction reproduces the retired pass handler: crossing into the
  * pass's region from another region to a non-pass destination is blocked
  * unless the pass is ruled by the moving player. Its detail stably encodes the
  * pass site + destination so the typed surface can rebuild
  * [[OathViolation.TravelPassBlocked]].
  */
trait NarrowPassPower extends Power {
  def restrictionFor(passSite: SiteId): OperationRestriction
}

object TravelPassBlockedCodec {
  val code: String = "travel-pass-blocked"

  def encode(passSite: SiteId, destination: SiteId): String =
    s"pass=${passSite.value};destination=${destination.value}"

  def decode(reason: OperationReason): Option[OathViolation.TravelPassBlocked] =
    if (reason.code != code) None
    else {
      val values = reason.detail.split(";").toVector.flatMap { part =>
        part.split("=", 2).toVector match {
          case Vector("pass", value) => Some("pass" -> value)
          case Vector("destination", value) => Some("destination" -> value)
          case _ => None
        }
      }.toMap
      for {
        pass <- values.get("pass")
        destination <- values.get("destination")
      } yield OathViolation.TravelPassBlocked(SiteId(pass), SiteId(destination))
    }
}

private final case class NarrowPassPowerImpl() extends NarrowPassPower {
  override val id: PowerId = PowerId("site.narrow-pass.pass")
  override val modifier: Option[MajorActionType] = Some(MajorActionType.Travel)
  override val handlers: Vector[PowerHandler] =
    Vector(ReviewedHandler.automatic(PowerWindow.TravelCost, implemented = true))

  override def restrictionFor(passSite: SiteId): OperationRestriction =
    new OperationRestriction {
      override def reason(ready: ReadyGame,
          operation: oathdigital.gameplay.operations.CoreOperation)
          : Option[OperationReason] = operation match {
        case oathdigital.gameplay.operations.Move(Piece.Pawn(player),
            oathdigital.gameplay.operations.PositionedLocation(
              oathdigital.gameplay.operations.Location.Site(from), _),
            oathdigital.gameplay.operations.PositionedLocation(
              oathdigital.gameplay.operations.Location.Site(to), _),
            _) =>
          val map = ready.game.current.map
          (for {
            fromRegion <- map.regionOf(from)
            toRegion <- map.regionOf(to)
            passRegion <- map.regionOf(passSite)
          } yield if (fromRegion != toRegion && toRegion == passRegion &&
              to != passSite) {
            rulerBlocks(ready, passSite, player).map(_ =>
              OperationReason(TravelPassBlockedCodec.code,
                TravelPassBlockedCodec.encode(passSite, to)))
          } else None).getOrElse(None)
        case _ => None
      }
    }

  private def rulerBlocks(ready: ReadyGame, passSite: SiteId,
      mover: PlayerId): Option[Unit] =
    SiteRule.ruler(
      ready.game.current.map.sites(passSite).forces,
      ready.game.current.players).toOption match {
      case Some(Player(player)) if player == mover => None
      case _ => Some(())
    }
}
