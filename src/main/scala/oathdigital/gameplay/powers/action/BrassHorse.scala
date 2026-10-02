package oathdigital.gameplay.powers.action

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.model._

object BrassHorseCard extends Relic(RelicId("R03"), "Brass Horse", value = 76, defense = 2):
  val power = PrintedPower(PowerId("relic.brass-horse"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Reveal the top card of your region's discard " +
      "pile. Place your pawn at a different site with a matching " +
      "card. If you **cannot**, place it at any site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Brass Horse (relic R03), ACTION: place 1 secret on this relic, reveal the
  * top card of the discard pile of the region your pawn is in, and place your
  * pawn at a different site holding a card of the same suit (a denizen or an
  * edifice). If it cannot, place it at any other site.
  *
  * The reveal is a public `Reveal` of the pile's top card. A discarded card
  * has no orientation state, so the reveal changes nothing and "turn it
  * facedown again" needs no operation. The destination decision is a live
  * `Branch`, asked only when more than one site qualifies. The reveal sits
  * before it and changes neither the pile nor the pawn.
  *
  * The power holds the catalog because a card's suit is a catalog fact and
  * `PhasePower.build` receives no catalog.
  */
final class BrassHorse(catalog: ExecutableCatalog)
    extends PaidAction(BrassHorseCard.power):
  import BrassHorse._

  override def noteKeys: Vector[NoteKey] = Vector(revealed, placed)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((state, _) => reveal(state, player)),
    Note(this.id, revealNote(_, player, source), covers = true),
    Branch((state, _) => ask(state, player)),
    BuildOps((state, pending) => place(state, player, pending)),
    Note(this.id, PawnMoves.placedNote(placed, source, player)))))

  /** The reveal changes nothing, so the pile's top card is still the one it
    * revealed. */
  private def revealNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    found <- region(states.now, player).toOption
    shown <- top(states.now, found)
  yield revealed(card, NoteArg.Player(player), NoteArg.Card(shown))

  private def region(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Region] = for
    site <- PawnMoves.pawnSite(ready, player)
    found <- ready.game.current.map.regionOf(site).toRight(
      OathViolation.InvalidEventOrder(s"${site.value} is in no region"))
  yield found

  private def top(ready: ReadyGame, region: Region): Option[WorldCardId] =
    ready.game.current.commonCards.discard(region).lastOption

  private def holds(ready: ReadyGame, site: SiteId, suit: Suit): Boolean =
    ready.game.current.map.sites.get(site).exists(_.denizens.exists(card =>
      catalog.suitOf(card.id).contains(suit)))

  /** The sites the pawn may be placed at: other sites holding a card of the
    * revealed suit, or every other site when there is none or no card was
    * revealed. A Vision has no suit.
    */
  private def destinations(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[SiteId]] = for
    here <- PawnMoves.pawnSite(ready, player)
    found <- region(ready, player)
  yield
    val others = PawnMoves.sitesOtherThan(ready, here)
    val matching = top(ready, found).flatMap(catalog.suitOf).toVector
      .flatMap(suit => others.filter(holds(ready, _, suit)))
    if matching.nonEmpty then matching else others

  private def reveal(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    region(ready, player).map(found => top(ready, found).toVector.map(card =>
      Reveal(card, Location.RegionalDiscard(found)): CoreOperation))

  /** An error surfaces later, from `place`, so it is not swallowed here. */
  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    destinations(ready, player) match
      case Right(sites) if sites.size > 1 => Vector(PawnMoves.siteChoice(
        decisionId, player, sites,
        "Brass Horse: choose the site to place your pawn at"))
      case _ => Vector.empty

  private def place(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    destinations(ready, player).flatMap:
      case Vector() => Right(Vector.empty)
      case Vector(only) => PawnMoves.relocate(ready, player, only)
      case _ => PawnMoves.chosenSite(pending, decisionId)
        .flatMap(PawnMoves.relocate(ready, player, _))

object BrassHorse:
  val id: PowerId = PowerId("relic.brass-horse")
  val decisionId: String = "power.brass-horse.site"
  /** Its own line: the reveal is its first effect, so the line sits there,
    * in the command that starts it. The placement may follow a question in
    * a later command, so it writes a line of its own. */
  val revealed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" revealed "), NotePart.Arg(1), NotePart.Text(".")))
  val placed: NoteKey = PawnMoves.placedKey("placed")
