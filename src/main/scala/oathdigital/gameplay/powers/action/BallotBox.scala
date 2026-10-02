package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

object BallotBoxCard extends Denizen(DenizenId("141"), "Ballot Box", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.ballot-box"),
    persistent = false, cost = Cost(favor = 2),
    text = "**ACTION:** If you have an adviser matching a card at this " +
      "site, replace all warbands on this site with your warbands. " +
      "Remove any that can't be replaced.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Ballot Box (card 141, site-only), ACTION: place 2 favor on this card. If
  * you have an adviser matching a card at this site, replace all warbands on
  * this site with your warbands. Remove any that cannot be replaced.
  *
  * "This site" is Ballot Box's. A match is a faceup denizen adviser of the
  * player's whose suit is that of a faceup denizen or an edifice, on either
  * face, at the site, Ballot Box included. Every warband there that is not
  * the player's, bandits included, is replaced from the player's supply with
  * a `Replace`. Those the supply cannot cover are killed first, since a site
  * holds one kind of warband. A site left empty is refilled with bandits
  * after the action. Nothing is asked.
  *
  * Its `used` lines cover the Moved line of the `Replace`. A short supply
  * adds a `removed` line for the warbands killed.
  */
final case class BallotBox private (catalog: ExecutableCatalog)
    extends PaidAction(BallotBoxCard.power):
  import BallotBox._

  override def noteKeys: Vector[NoteKey] = Vector(replaced, replacedBandits,
    removed, removedBandits, unmatched, empty)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Denizen(card) => SiteRulers.siteOf(ready, card)
      .toRight(OathViolation.InvalidEventOrder(
        s"${card.value} is not at a site"))
      .map(site => Sequence(Vector[Operation](
        Branch((live, _) => vote(live, player, source, site)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.wireId} is not Ballot Box"))

  /** Read after the cost: the line for no match or no warband to replace,
    * or the replacement and its lines. */
  private def vote(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, site: SiteId): Vector[Operation] =
    def line(key: NoteKey, args: NoteArg*): Vector[Operation] =
      Vector(Note(this.id, _ => PowerSourceRef.of(source).map(key(_, args*))))
    if !matched(ready, player, site) then
      line(unmatched, NoteArg.Player(player), NoteArg.Site(site))
    else enemies(ready, player, site) match
      case None => line(empty, NoteArg.Site(site))
      case Some((kind, count)) => Vector(
        BuildOps((live, _) => replacement(live, player, site, kind, count)),
        Note(this.id, replacedNote(_, site, kind, source), covers = true),
        Note(this.id, removedNote(_, site, kind, source)))

  /** Whether a faceup denizen adviser of the player's shares a suit with a
    * faceup denizen or an edifice at the site. */
  private def matched(ready: ReadyGame, player: PlayerId, site: SiteId)
      : Boolean =
    val advised = PlayerFacts.player(ready, player).toOption.toVector
      .flatMap(_.advisers.collect {
        case DenizenState(card, Orientation.FaceUp, _) => card })
      .flatMap(catalog.suitOf).toSet
    ready.game.current.map.sites.get(site).toVector.flatMap(_.denizens.collect {
      case DenizenState(card, Orientation.FaceUp, _) => card: CardId
      case EdificeState(card, _, _) => card: CardId
    }).flatMap(catalog.suitOf).exists(advised)

  /** The warbands at the site that are not the player's: their kind and
    * count. */
  private def enemies(ready: ReadyGame, player: PlayerId, site: SiteId)
      : Option[(ForceKind, Int)] =
    val mine = PlayerFacts.forceKind(ready, player).toOption
    forcesAt(ready, site).collect {
      case SiteForces.Occupied(kind, count) if !mine.contains(kind) =>
        kind -> count }

  /** The kill of what the supply cannot cover, then the `Replace` of the
    * rest. */
  private def replacement(ready: ReadyGame, player: PlayerId, site: SiteId,
      kind: ForceKind, count: Int)
      : Either[OathViolation, Vector[CoreOperation]] =
    PlayerFacts.forceKind(ready, player).map { mine =>
      val covered = count.min(PlayerFacts.banked(ready, mine))
      val at = PositionedLocation(Location.Site(site))
      Vector[CoreOperation]() ++
        Option.when(count > covered)(
          Kill(Piece.Warbands(kind, count - covered), at)) ++
        Option.when(covered > 0)(Replace(Piece.Warbands(kind, covered),
          Piece.Warbands(mine, covered), at))
    }

  /** The player's warbands the step put at the site, and the warbands of
    * `kind` it killed there. */
  private def counts(states: NoteStates, site: SiteId, kind: ForceKind)
      : (Int, Int) = states.previous.fold((0, 0)) { (before, after) =>
    val placed = forcesAt(after, site) match
      case Some(SiteForces.Occupied(other, n)) if other != kind => n
      case _ => 0
    (placed, held(before, site, kind) - held(after, site, kind) - placed)
  }

  private def replacedNote(states: NoteStates, site: SiteId, kind: ForceKind,
      source: DecisionOptionRef): Option[PowerNote] =
    val (placed, _) = counts(states, site, kind)
    PowerSourceRef.of(source).filter(_ => placed > 0).map(card =>
      whose(states.now, kind) match
        case Some(owner) => replaced(card, NoteArg.Number(placed),
          NoteArg.Player(owner), NoteArg.Site(site))
        case None => replacedBandits(card, NoteArg.Number(placed),
          NoteArg.Site(site)))

  private def removedNote(states: NoteStates, site: SiteId, kind: ForceKind,
      source: DecisionOptionRef): Option[PowerNote] =
    val (_, killed) = counts(states, site, kind)
    PowerSourceRef.of(source).filter(_ => killed > 0).map(card =>
      whose(states.now, kind) match
        case Some(owner) => removed(card, NoteArg.Number(killed),
          NoteArg.Player(owner), NoteArg.Site(site))
        case None => removedBandits(card, NoteArg.Number(killed),
          NoteArg.Site(site)))

object BallotBox:
  val id: PowerId = BallotBoxCard.power.id
  /** "Replaced {n} {Blue} warband at {site}." */
  val replaced: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Arg(1), NotePart.Plural(0, " warband at ", " warbands at "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Replaced {n} bandit warband at {site}." */
  val replacedBandits: NoteKey = NoteKey("used.bandits", Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit warband at ", " bandit warbands at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "Removed {n} {Blue} warband at {site}." */
  val removed: NoteKey = NoteKey("removed", Vector(
    NotePart.Text("Removed "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Arg(1), NotePart.Plural(0, " warband at ", " warbands at "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Removed {n} bandit warband at {site}." */
  val removedBandits: NoteKey = NoteKey("removed.bandits", Vector(
    NotePart.Text("Removed "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit warband at ", " bandit warbands at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "{Red} had no adviser matching a card at {site}." */
  val unmatched: NoteKey = NoteKey("used.unmatched", Vector(NotePart.Arg(0),
    NotePart.Text(" had no adviser matching a card at "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{site} held no warband to replace." */
  val empty: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no warband to replace.")))

  private def forcesAt(ready: ReadyGame, site: SiteId): Option[SiteForces] =
    ready.game.current.map.sites.get(site).map(_.forces)

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    forcesAt(ready, site) match
      case Some(SiteForces.Occupied(`kind`, n)) => n
      case _ => 0

  /** The player whose warbands are of `kind`; none for bandits. */
  private def whose(ready: ReadyGame, kind: ForceKind): Option[PlayerId] =
    ready.game.current.players.find(state =>
      PlayerForceKind.of(ready, state).contains(kind)).map(_.player)

  def forCatalog(catalog: ExecutableCatalog): BallotBox =
    new BallotBox(catalog)
