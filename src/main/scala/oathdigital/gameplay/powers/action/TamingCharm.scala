package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PowerAnswers}
import oathdigital.model._

object TamingCharmCard extends Denizen(DenizenId("37"), "Taming Charm", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.taming-charm"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Discard a [suit-beast] or [suit-nomad] card at " +
      "your site to gain [favor] [favor] from the matching favor " +
      "bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Taming Charm (card 37), ACTION: place 1 secret on this card, then
  * discard a Beast or Nomad card at your site to gain 2 favor from the
  * matching favor bank.
  *
  * The player chooses a faceup Beast or Nomad denizen or ruined edifice at
  * their site ([[SiteCards]]), which gets the standard discard. The discard
  * returns the card's favor to the bank the gain takes from, so the gain
  * runs as its own step, read after the discard: 2 favor, or all the bank
  * holds. The choice is narrated: the line names the card.
  *
  * The line covers the step before it: the gain's Gain line, or, with the
  * bank empty, the discard's Discarded line. With nothing to discard, the
  * cost stays paid and the line names the site.
  */
final case class TamingCharm private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.taming-charm", Cost(secret = 1)):
  import TamingCharm._

  private val cards = new SiteCards(catalog, Set(Suit.Beast, Suit.Nomad))
  override def noteKeys: Vector[NoteKey] = Vector(gained, discardedOnly, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => cards.ask(live, player, decisionId,
      "Taming Charm: discard a Beast or Nomad card at your site")),
    BuildOps((live, pending) => discard(live, player, pending),
      required = true),
    BuildOps((live, pending) => Right(gain(live, player, pending))),
    Note(this.id, tamedNote(_, player, source), covers = true))))

  private def discard(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    cards.chosen(ready, player, pending, decisionId) match
      case None => Right(Vector.empty)
      case Some((site, card, suit)) => cards.discard(ready, site, card, suit,
        player, required = true).map(Vector(_))

  /** The gain from the bank of the answered card's suit, read from the
    * catalog: the card has left the site by now. */
  private def gain(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Vector[CoreOperation] = (for
    answer <- PowerAnswers.one(pending, decisionId)
    card <- cards.cardOf(answer)
    suit <- catalog.suitOf(card)
    take = Gained.min(ready.banks.favor.getOrElse(suit, 0))
    if take > 0
  yield Gain.Favor(player, suit, take)).toVector

  private def tamedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId).flatMap(cards.cardOf) match
        case Some(chosen) => Some(line(states, card, player, chosen))
        case None => PowerAccess.pawnSite(states.now, player).map(site =>
          bare(card, NoteArg.Site(site))))

  /** The favor the player gained in the step before the note: the gain's,
    * or none when the empty bank skipped it. */
  private def line(states: NoteStates, card: PowerSourceRef, player: PlayerId,
      chosen: CardId): PowerNote =
    val favor = states.previous.fold(0)(NoteSupport.favor(_, player))
    catalog.suitOf(chosen).filter(_ => favor > 0) match
      case Some(suit) => gained(card, NoteArg.Player(player),
        NoteArg.Card(chosen), NoteArg.Amount(favor, NoteUnit.Favor),
        NoteArg.Bank(suit))
      case None =>
        discardedOnly(card, NoteArg.Player(player), NoteArg.Card(chosen))

object TamingCharm:
  val id: PowerId = PowerId("denizen.taming-charm")
  val decisionId: String = "power.taming-charm.card"
  val Gained: Int = 2
  /** "{Red} discarded {card} and gained {n favor} from {the Beast bank}." */
  val gained: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(" from "),
    NotePart.Arg(3), NotePart.Text(".")))
  /** "{Red} discarded {card}." */
  val discardedOnly: NoteKey = NoteKey("used.discarded", Vector(
    NotePart.Arg(0), NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{site} held no Beast or Nomad card." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no Beast or Nomad card.")))

  def forCatalog(catalog: ExecutableCatalog): Option[TamingCharm] =
    CatalogCards.denizen(catalog, id).map(_ => new TamingCharm(catalog))
