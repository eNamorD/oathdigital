package oathdigital.gameplay.powers.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.action.PawnMoves
import oathdigital.model._

/** The River site power (CR p. 31, NF p. 11), WAKE: "You may place your pawn
  * at another River. This is not a Travel action."
  *
  * It belongs to its site, so the player whose pawn is there may use it, once
  * per turn from each River; the engine enforces both. The placement is a
  * plain `Move`: no Supply is paid and no Travel window runs, so no Pass and
  * no Travel cost modifier applies. Its notes are written under "River",
  * since four sites share the power.
  */
final case class RiverSitePower(id: PowerId, site: SiteId,
    catalog: ExecutableCatalog) extends PhasePower:
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
      other != site && isRiver(catalog, other))

object RiverSitePower:
  val name: String = "River"
  val siteDecisionId: String = "power.river.site"
  /** "{player} placed at {site}.", the power's own line. */
  val placed: NoteKey = PawnMoves.placedKey(NoteKey.Used)

  /** The reviewed River handlers, one power each. */
  val supported: Vector[PowerId] = Vector("site.ancient-city.river",
    "site.headwaters.river", "site.riverbank.river",
    "site.tidal-marshes.river").map(PowerId(_))

  def isRiver(catalog: ExecutableCatalog, site: SiteId): Boolean =
    catalog.site(site).exists(_.handlers.exists(_.endsWith(".river")))

  /** A River power for each reviewed handler `catalog` has. */
  def forCatalog(catalog: ExecutableCatalog): Vector[RiverSitePower] =
    supported.flatMap(id => catalog.siteWithHandler(id).map(definition =>
      RiverSitePower(id, definition.id, catalog)))
