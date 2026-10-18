package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.banner.CardPowerBanners
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object TerrorSpellsCard extends Denizen(DenizenId("61"), "Terror Spells", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.terror-spells"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Kill any two warbands in your region _(at sites " +
      "or on boards, even yours)_ if you hold the Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Terror Spells (card 61), ACTION: place 1 secret on this card, then, if
  * the player holds the Darkest Secret, kill two warbands in the region of
  * their pawn, at sites or on boards, even their own.
  *
  * Without the Darkest Secret the power is still offered: the cost stays
  * paid and nothing else happens. With it, the places are read live after
  * the cost: each site of the region holding warbands, in map order, then
  * the board of each player whose pawn is in the region, the player's own
  * included, in seat order. Two warbands die, or every one when the region
  * holds fewer. When only one split kills them, they die without asking;
  * otherwise one `Distribute` asks how many die in each place, at most two
  * in one. Each place's kill is one `Kill` of its kind, bandits included. A
  * site the kill empties is refilled with bandits by the existing refill
  * after the action.
  *
  * No generic line exists for a kill, so its line covers nothing.
  */
case object TerrorSpells extends PaidAction(TerrorSpellsCard.power):
  val decisionId: String = "power.terror-spells.kills"
  val Kills: Int = 2
  private val notes = NoteSet(id)
  /** "Killed {n} warbands." */
  val killed: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Plural(0, " warband.", " warbands.")))
  /** "The {Darkest Secret} was not held." */
  val unheld: NoteKey = notes.key("used.none", Vector(NotePart.Text("The "),
    NotePart.Arg(0), NotePart.Text(" was not held.")))
  /** "No warband was in the region." */
  val empty: NoteKey = notes.key("used.empty", Vector(
    NotePart.Text("No warband was in the region.")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => kill(live, player, pending)),
    Note(id, killNote(_, player, source)))))

  /** One place warbands stand in the region: how many, and of what kind. */
  private final case class Place(ref: DecisionOptionRef, at: Location,
      kind: ForceKind, count: Int):
    /** The most that may die here. */
    def most: Int = math.min(Kills, count)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    CardPowerBanners.holdsDarkestSecret(ready, player)

  /** The places holding warbands in the region of the player's pawn: sites
    * in map order, then boards in seat order. None while the player does
    * not hold the Darkest Secret. */
  private def places(ready: ReadyGame, player: PlayerId): Vector[Place] =
    val current = ready.game.current
    val map = current.map
    if !holds(ready, player) then Vector.empty
    else PowerAccess.pawnSite(ready, player).flatMap(map.regionOf).toVector
      .flatMap { region =>
        val sites = map.inPlay.filter(map.regionOf(_).contains(region))
        val atSites = sites.flatMap(site =>
          map.sites.get(site).map(_.forces).collect {
            case SiteForces.Occupied(kind, count) if count > 0 =>
              Place(DecisionOptionRef.Site(site), Location.Site(site), kind,
                count)
          })
        val onBoards = current.players.filter(held =>
            held.pawnSite.exists(sites.contains) && held.board.warbands > 0)
          .flatMap(held => PlayerForceKind.of(ready, held).map(kind =>
            Place(DecisionOptionRef.Player(held.player),
              Location.PlayArea(held.player), kind, held.board.warbands)))
        atSites ++ onBoards
      }

  /** How many die: two, or every warband when the region holds fewer. */
  private def total(found: Vector[Place]): Int =
    math.min(Kills, found.map(_.count).sum)

  /** Whether more than one split kills the total. */
  private def asks(found: Vector[Place]): Boolean =
    found.map(_.most).sum > total(found)

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    val found = places(ready, player)
    if !asks(found) then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.Distribute.exactly(
      found.map(place => DistributeSlot(place.ref, 0, place.most, None)),
      total(found), Some("Terror Spells: kill two warbands in your region"),
      "Kill warbands")))

  /** Each place's cap when there is one split; the answer otherwise. */
  private def kill(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = places(ready, player)
    if !asks(found) then Right(found.flatMap(place => killAt(place, place.most)))
    else PowerAnswers.distribution(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId))
      .flatMap(_.filter(_.amount > 0).foldLeft[Either[OathViolation,
          Vector[CoreOperation]]](Right(Vector.empty)) { (done, row) =>
        done.flatMap(ops => found.find(_.ref == row.ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${row.ref.wireId} holds no warband Terror Spells can kill"))
          .map(place => ops ++ killAt(place, row.amount)))
      })

  private def killAt(place: Place, amount: Int): Vector[CoreOperation] =
    if amount <= 0 then Vector.empty
    else Vector(Kill(Piece.Warbands(place.kind, amount),
      PositionedLocation(place.at)))

  /** The warbands that died in the step before the note; with none, why. A
    * step that killed nothing, such as the cost's, reads as none. */
  private def killNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[LogNote] =
    PowerSourceRef.of(source).map { card =>
      val lost = states.previous.fold(0) { case (before, after) =>
        inPlay(before) - inPlay(after) }
      if lost > 0 then killed(card, NoteArg.Number(lost))
      else if !holds(states.now, player) then
        unheld(card, NoteArg.Banner(Banner.DarkestSecret))
      else empty(card)
    }

  /** Warbands on boards and at sites, of every kind. */
  private def inPlay(ready: ReadyGame): Int =
    val current = ready.game.current
    current.players.map(_.board.warbands).sum +
      current.map.sites.values.map(_.forces).collect {
        case SiteForces.Occupied(_, count) => count }.sum
