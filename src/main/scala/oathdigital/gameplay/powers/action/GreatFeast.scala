package oathdigital.gameplay.powers.action

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts}
import oathdigital.model._

/** Great Feast (card 257), ACTION: place 1 favor on this card, then discard
  * a Beast card at your site to gain 3 Supply.
  *
  * The player chooses a faceup Beast denizen or ruined Beast edifice at
  * their site ([[SiteCards]]), which gets the standard discard. The Supply
  * runs in the same required batch, capped to the room on the track: a
  * required batch refuses an operation it cannot make in full. The choice is
  * narrated: the line names the card, and covers the Discarded line. With
  * nothing to discard, the cost stays paid and the line names the site.
  */
final case class GreatFeast private (catalog: ExecutableCatalog)
    extends PaidAction("denizen.great-feast", Cost(favor = 1)):
  import GreatFeast._

  private val cards = new SiteCards(catalog, Set(Suit.Beast))
  override def noteKeys: Vector[NoteKey] = Vector(feasted, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => cards.ask(live, player, decisionId,
      "Great Feast: discard a Beast card at your site")),
    BuildOps((live, pending) => feast(live, player, pending), required = true),
    Note(this.id, feastNote(_, player, source), covers = true))))

  private def feast(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    cards.chosen(ready, player, pending, decisionId) match
      case None => Right(Vector.empty)
      case Some((site, card, suit)) => meal(ready, player, site, card, suit)

  /** The discard, then the Supply the track has room for. */
  private def meal(ready: ReadyGame, player: PlayerId, site: SiteId,
      card: SiteDenizenState, suit: Suit)
      : Either[OathViolation, Vector[CoreOperation]] = for
    discard <- cards.discard(ready, site, card, suit, player, required = true)
    held <- PlayerFacts.player(ready, player)
  yield
    val room = Gained.min(SupplyTrack.Maximum - held.board.supply.supply)
    Vector[CoreOperation](discard) ++
      Option.when(room > 0)(GainSupply(player, room))

  private def feastNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId).flatMap(cards.cardOf) match
        case Some(chosen) => Some(feasted(card, NoteArg.Player(player),
          NoteArg.Card(chosen), NoteArg.Amount(
            states.previous.fold(0)(NoteSupport.supply(_, player)),
            NoteUnit.Supply)))
        case None => PowerAccess.pawnSite(states.now, player).map(site =>
          bare(card, NoteArg.Site(site))))

object GreatFeast:
  val id: PowerId = PowerId("denizen.great-feast")
  val decisionId: String = "power.great-feast.card"
  val Gained: Int = 3
  /** "{Red} discarded {card} and gained {n} Supply." */
  val feasted: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{site} held no Beast card." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no Beast card.")))

  def forCatalog(catalog: ExecutableCatalog): Option[GreatFeast] =
    CatalogCards.denizen(catalog, id).map(_ => new GreatFeast(catalog))
