package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

object BanditChiefCard extends Denizen(DenizenId("100"), "Bandit Chief", Suit.Discord) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.bandit-chief"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** kill one warband at each site not ruled by " +
      "the bandits.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Bandit Chief (card 100, site-only), WHEN PLAYED: kill one warband at each
  * site not ruled by the bandits.
  *
  * The sites are read live, in map order: every site in play whose warbands
  * are not bandits, the actor's own included. Each loses one warband by a
  * `Kill`, in a step of its own, with a line naming whose warband died. The
  * refill after the command puts bandits on a site the kill empties. Nothing
  * is asked. With no such site, one line says so.
  *
  * No generic line exists for a kill, so the lines cover nothing.
  */
case object BanditChief extends WhenPlayedPower:
  val cardId: DenizenId = BanditChiefCard.id
  val id: PowerId = BanditChiefCard.power.id
  private val notes = NoteSet(id)
  /** "Killed {n} {Blue} warband at {site}." */
  val killed: NoteKey = notes.key("killed", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "No warband was killed." */
  val none: NoteKey = notes.key("none", Vector(
    NotePart.Text("No warband was killed.")))

  override def noteKeys: Vector[NoteKey] = notes.declared

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(Branch((live, _) => killAll(live)))

  /** The sites in play held by warbands other than bandits, in map order,
    * with the kind that holds each. */
  private def targets(ready: ReadyGame): Vector[(SiteId, ForceKind)] =
    val map = ready.game.current.map
    map.inPlay.flatMap(site => map.sites.get(site).map(_.forces).collect {
      case SiteForces.Occupied(kind, count)
          if kind != ForceKind.Bandit && count > 0 => site -> kind })

  private def killAll(ready: ReadyGame): Vector[Operation] =
    targets(ready) match
      case Vector() => Vector(Note(id, _ => Some(none(cardSource))))
      case found => found.flatMap { (site, kind) => Vector(
        BuildOps((_, _) => Right(Vector(Kill(Piece.Warbands(kind, 1),
          PositionedLocation(Location.Site(site)))))),
        Note(id, killedNote(_, site, kind))) }

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The warbands of `kind` the step before killed at `site`, and whose. */
  private def killedNote(states: NoteStates, site: SiteId, kind: ForceKind)
      : Option[LogNote] = for
    (before, after) <- states.previous
    lost = held(before, site, kind) - held(after, site, kind)
    if lost > 0
    owner <- after.game.current.players.find(seat =>
      PlayerFacts.forceKind(after, seat.player).toOption.contains(kind))
  yield killed(cardSource, NoteArg.Number(lost), NoteArg.Player(owner.player),
    NoteArg.Site(site))
