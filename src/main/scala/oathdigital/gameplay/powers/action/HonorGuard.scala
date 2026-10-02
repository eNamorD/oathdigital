package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object HonorGuardCard extends Denizen(DenizenId("251"), "Honor Guard", Suit.Order) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.honor-guard"),
    persistent = false, cost = Cost(favor = 2, favorBurnt = 1),
    text = "**ACTION:** Choose a player with no [suit-order] advisers " +
      "whose pawn is at your site. Bury a faceup adviser they have.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Honor Guard (card 251, adviser-only), ACTION: place 2 favor on this card
  * and burn 1, then choose a player with no Order advisers whose pawn is at
  * your site, and bury a faceup adviser they have.
  *
  * One question, as Hunger asks: the faceup denizen advisers of every player
  * at the player's site who has no faceup Order adviser, in seat then adviser
  * order. The user holds Honor Guard, an Order adviser, so is never a
  * candidate. `Bury` ignores locked, so a locked adviser is a candidate. The
  * burial uses the standard returns: favor to the card's suit bank, secrets
  * to the player facedown. With no candidate the cost stays paid and one line
  * says so.
  *
  * Its line covers the generic Buried line.
  */
final case class HonorGuard private (catalog: ExecutableCatalog)
    extends PaidAction(HonorGuardCard.power):
  import HonorGuard._

  override def noteKeys: Vector[NoteKey] = Vector(buried, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => bury(live, player, pending)),
    Note(this.id, buriedNote(_, player, source), covers = true))))

  private final case class Candidate(owner: PlayerId, card: DenizenState)

  private def ordered(owner: PlayerState): Boolean = owner.advisers.exists {
    case DenizenState(card, Orientation.FaceUp, _) =>
      catalog.suitOf(card).contains(Suit.Order)
    case _ => false
  }

  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] =
    val site = PowerAccess.pawnSite(ready, actor)
    for
      owner <- ready.game.current.players
      if site.nonEmpty && owner.pawnSite == site && !ordered(owner)
      card <- owner.advisers.collect {
        case card @ DenizenState(_, Orientation.FaceUp, _) => card }
    yield Candidate(owner.player, card)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(c => DecisionOption.Denizen(
          DecisionOptionRef.Denizen(c.card.id))),
        heading = Some("Honor Guard: bury a faceup adviser of a player at " +
          "your site with no Order adviser"))))

  /** No answer means no adviser could be buried. */
  private def bury(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => burial(ready, actor, ref)

  private def burial(ready: ReadyGame, actor: PlayerId,
      ref: DecisionOptionRef): Either[OathViolation, Vector[CoreOperation]] =
    for
      chosen <- candidates(ready, actor)
        .find(c => DecisionOptionRef.Denizen(c.card.id) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not an adviser Honor Guard can bury"))
      suit = catalog.suitOf(chosen.card.id)
      _ <- Either.cond(chosen.card.tokens.favor == 0 || suit.isDefined, (),
        OathViolation.InvalidEventOrder(
          s"no suit is known for ${chosen.card.id.value}"))
    yield Bury.standard(BuryableCard.Denizen(chosen.card.id),
      PositionedLocation(Location.PlayArea(chosen.owner)), suit,
      chosen.card.tokens.favor, chosen.card.tokens.secrets, actor)

  /** The chosen adviser, read where it stood before the bury step. */
  private def buriedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId) match
        case None => Some(spared(card))
        case Some(ref) => buriedLine(states, actor, card, ref))

  private def buriedLine(states: NoteStates, actor: PlayerId,
      card: PowerSourceRef, ref: DecisionOptionRef): Option[PowerNote] = for
    step <- states.previous
    chosen <- candidates(step._1, actor)
      .find(c => DecisionOptionRef.Denizen(c.card.id) == ref)
  yield buried(card, NoteArg.Player(actor), NoteArg.Card(chosen.card.id),
    NoteArg.Player(chosen.owner))

object HonorGuard:
  val id: PowerId = HonorGuardCard.power.id
  val decisionId: String = "power.honor-guard.adviser"
  /** "{Red} buried {card} from {Blue}'s advisers." */
  val buried: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" buried "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text("'s advisers.")))
  /** "No adviser could be buried." */
  val spared: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No adviser could be buried.")))

  def forCatalog(catalog: ExecutableCatalog): HonorGuard =
    new HonorGuard(catalog)
