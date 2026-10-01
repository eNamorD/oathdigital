package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.model._

/** Bandit Paymaster (card 219), ACTION: place 1 favor on this card, then
  * remove a bandit from your site, except the last, to gain 3 warbands.
  *
  * When the player's site holds 2 or more bandit warbands, one is killed
  * back to the bandit bank and the player gains 3 warbands, or what their
  * supply holds. Both run in one step: the banks differ. Otherwise nothing
  * happens and one line says so. Nothing is asked.
  */
case object BanditPaymaster extends PaidAction("denizen.bandit-paymaster",
    Cost(favor = 1)):
  val Removed: Int = 1
  val Gained: Int = 3
  /** "Removed {1} bandit warband from {site}, and {Red} gained
    * {n warbands}." */
  val paid: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Removed "),
    NotePart.Arg(0), NotePart.Plural(0, " bandit warband from ",
      " bandit warbands from "), NotePart.Arg(1), NotePart.Text(", and "),
    NotePart.Arg(2), NotePart.Text(" gained "), NotePart.Arg(3),
    NotePart.Text(".")))
  /** "{site} had no bandit to spare." */
  val spared: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no bandit to spare.")))
  override def noteKeys: Vector[NoteKey] = Vector(paid, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => pay(live, player)),
    Note(id, paidNote(_, player, source)))))

  private def bandits(ready: ReadyGame, site: SiteId): Int =
    ready.game.current.map.sites.get(site).map(_.forces) match
      case Some(SiteForces.Occupied(ForceKind.Bandit, count)) => count
      case _ => 0

  private def pay(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAccess.pawnSite(ready, player)
      .filter(bandits(ready, _) > Removed) match
      case None => Right(Vector.empty)
      case Some(site) => PlayerFacts.forceKind(ready, player).map(kind =>
        Vector[CoreOperation](Kill(Piece.Warbands(ForceKind.Bandit, Removed),
          PositionedLocation(Location.Site(site))),
          Gain.Warbands(player, kind, Gained)))

  private def paidNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).zip(PowerAccess.pawnSite(states.now, player))
      .map((card, site) => line(states, card, site, player))

  /** The bandits the site lost and the warbands the player gained in the
    * step before the note. */
  private def line(states: NoteStates, card: PowerSourceRef, site: SiteId,
      player: PlayerId): PowerNote =
    val removed = states.previous.fold(0)((before, after) =>
      bandits(before, site) - bandits(after, site))
    val gained = states.previous.fold(0)(NoteSupport.warbands(_, player))
    if removed > 0 then paid(card, NoteArg.Number(removed),
      NoteArg.Site(site), NoteArg.Player(player),
      NoteArg.Amount(gained, NoteUnit.Warband))
    else spared(card, NoteArg.Site(site))
