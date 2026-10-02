package oathdigital.gameplay.powers.wake

import oathdigital.catalog.Site
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.action.PawnMoves
import oathdigital.model._

object AncientCitySite extends Site(SiteId("site:ancient-city"), "Ancient City",
    defense = 2, capacity = 3, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = Some(Tokens(3, 0)),
    homeland = None):
  val enduring = PowerId("site.ancient-city.enduring")
  val river = PowerId("site.ancient-city.river")
  val handlers: Vector[PowerId] = Vector(enduring, river)

object HeadwatersSite extends Site(SiteId("site:headwaters"), "Headwaters",
    defense = 1, capacity = 2, relicSlots = 1,
    recoverDifficulty = Some(4),
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val river = PowerId("site.headwaters.river")
  val mountain = PowerId("site.headwaters.mountain")
  val handlers: Vector[PowerId] = Vector(river, mountain)

object RiverbankSite extends Site(SiteId("site:riverbank"), "Riverbank",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val river = PowerId("site.riverbank.river")
  val handlers: Vector[PowerId] = Vector(river)

object TidalMarshesSite extends Site(SiteId("site:tidal-marshes"), "Tidal Marshes",
    defense = 1, capacity = 2, relicSlots = 0,
    recoverDifficulty = None,
    startingResources = Tokens(0, 0),
    forgeRequirements = None,
    homeland = None):
  val river = PowerId("site.tidal-marshes.river")
  val coast = PowerId("site.tidal-marshes.coast")
  val handlers: Vector[PowerId] = Vector(river, coast)

/** The River site power (CR p. 31, NF p. 11), WAKE: "You may place your pawn
  * at another River. This is not a Travel action."
  *
  * It belongs to its site, so the player whose pawn is there may use it, once
  * per turn from each River; the engine enforces both. The placement is a
  * plain `Move`: no Supply is paid and no Travel window runs, so no Pass and
  * no Travel cost modifier applies. Its notes are written under "River",
  * since four sites share the power.
  */
final case class RiverSitePower(id: PowerId, site: SiteId)
    extends PhasePower:
  import RiverSitePower._

  def timing: PowerTiming = PowerTiming.Wake
  override def noteKeys: Vector[NoteKey] = Vector(placed)
  override def noteSource: Option[String] = Some(name)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = destinations(ready).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    PawnMoves.siteChoice(siteDecisionId, player, destinations(ready),
      "River: choose the River to place your pawn at"),
    BuildOps((state, pending) => PawnMoves.chosenSite(pending, siteDecisionId)
      .flatMap(PawnMoves.relocate(state, player, _))),
    Note(id, PawnMoves.placedNote(placed, source, player)))))

  /** Every River in play but this one, in map order. */
  private def destinations(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(other =>
      other != site && isRiver(other))

object RiverSitePower:
  val name: String = "River"
  val siteDecisionId: String = "power.river.site"
  /** "{player} placed at {site}.", the power's own line. */
  val placed: NoteKey = PawnMoves.placedKey(NoteKey.Used)

  /** One River power for each River site. */
  val all: Vector[RiverSitePower] = Vector(
    RiverSitePower(AncientCitySite.river, AncientCitySite.id),
    RiverSitePower(HeadwatersSite.river, HeadwatersSite.id),
    RiverSitePower(RiverbankSite.river, RiverbankSite.id),
    RiverSitePower(TidalMarshesSite.river, TidalMarshesSite.id))

  def isRiver(site: SiteId): Boolean = all.exists(_.site == site)
