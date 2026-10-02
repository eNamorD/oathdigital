package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object SiegeEnginesCard extends Denizen(DenizenId("116"), "Siege Engines", Suit.Order):
  val power = PrintedPower(PowerId("denizen.siege-engines"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Kill two warbands _(even yours)_ at any one " +
      "site in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Siege Engines (card 116), ACTION: place 1 favor on this card, then kill
  * two warbands, even the player's own, at any one site in the region of the
  * player's pawn.
  *
  * Every site in the region is offered, whoever rules it and whether or not
  * it holds warbands. A site holds one kind of warband, so the kill is one
  * `Kill` of up to two of that kind. A site the kill empties is refilled with
  * bandits by the existing refill after the action.
  *
  * The decision is a plain `Decide`, as Wolves' is: its options are the
  * region's sites, which the cost does not change.
  */
case object SiegeEngines extends PaidAction(SiegeEnginesCard.power):
  val decisionId: String = "power.siege-engines.site"
  val Kills: Int = 2
  /** "Killed {n} {Blue} warbands at {site}." */
  val killed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "Killed {n} bandit warbands at {site}." */
  val bandits: NoteKey = NoteKey("used.bandits", Vector(
    NotePart.Text("Killed "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit warband at ", " bandit warbands at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "{site} had no warband to kill." */
  val spared: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no warband to kill.")))
  override def noteKeys: Vector[NoteKey] = Vector(killed, bandits, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Decide(decisionId, player, DecisionQuery.ChooseOne(
      sites(ready, player).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some("Siege Engines: kill two warbands at a site in your region"))),
    BuildOps((live, pending) => kill(live, player, pending)),
    Note(id, killNote(_, source)))))

  /** The sites in the region of the player's pawn, in map order. */
  def sites(ready: ReadyGame, player: PlayerId): Vector[SiteId] =
    val map = ready.game.current.map
    PowerAccess.pawnSite(ready, player).flatMap(map.regionOf).toVector
      .flatMap(region => map.inPlay.filter(map.regionOf(_).contains(region)))

  private def forces(ready: ReadyGame, site: SiteId): Option[SiteForces] =
    ready.game.current.map.sites.get(site).map(_.forces)

  private def kill(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for
    ref <- PowerAnswers.one(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId))
    site <- sites(ready, actor).find(DecisionOptionRef.Site(_) == ref)
      .toRight(OathViolation.InvalidEventOrder(
        s"${ref.wireId} is not a site in the actor's region"))
  yield forces(ready, site) match
    case Some(SiteForces.Occupied(kind, count)) => Vector(Kill(
      Piece.Warbands(kind, math.min(Kills, count)),
      PositionedLocation(Location.Site(site))))
    case _ => Vector.empty

  /** The warbands the chosen site lost in the kill step, and whose. */
  private def killNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    site <- NoteSupport.answer(states, decisionId).collect {
      case DecisionOptionRef.Site(id) => id }
    step <- states.previous
    note <- forces(step._1, site) match
      case Some(SiteForces.Occupied(kind, before)) =>
        val lost = before - remaining(step._2, site, kind)
        if lost <= 0 then Some(spared(card, NoteArg.Site(site)))
        else if kind == ForceKind.Bandit then
          Some(bandits(card, NoteArg.Number(lost), NoteArg.Site(site)))
        else owner(step._1, kind).map(whose => killed(card,
          NoteArg.Number(lost), NoteArg.Player(whose), NoteArg.Site(site)))
      case _ => Some(spared(card, NoteArg.Site(site)))
  yield note

  private def remaining(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    forces(ready, site) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The player whose warbands are of `kind`. Every game is all-Exile, so
    * each non-bandit kind has one owner; Imperial warbands, which have none
    * until Empire rules exist, would leave the kill without a line. */
  private def owner(ready: ReadyGame, kind: ForceKind): Option[PlayerId] =
    ready.game.current.players.find(p =>
      PlayerForceKind.of(ready, p).contains(kind)).map(_.player)
