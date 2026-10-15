package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._

object SignalTreesCard extends Denizen(DenizenId("217"), "Signal Trees",
    Suit.Beast):
  val power = PrintedPower(PowerId("denizen.signal-trees"),
    persistent = false, cost = Cost(secret = 1),
    text = "Reveal the top three cards of your region's discard pile. " +
      "±2 [attack-die] per revealed [suit-beast]. Discard all of " +
      "the revealed cards.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Signal Trees (card 217), a battle plan for either side: "Reveal the top
  * three cards of your region's discard pile. ±2 attack dice per revealed
  * Beast. Discard all of the revealed cards."
  *
  * One secret is placed onto the card, so bandits never use it. When it is
  * applied, the top three cards of the discard pile of the region its
  * user's pawn is in, or as many as the pile holds, move off the top one at
  * a time to the top of the next region's pile, so the first drawn ends
  * deepest. Each is revealed there to every player: a card entering a pile
  * is forgotten, so the reveal follows the move, as Call for Help's does.
  * An attacker adds two attack dice per Beast revealed; a defender removes
  * two. The pile is read when the walk reaches the plan, so the plan
  * window's dry run, which records no event, shows nobody the cards.
  *
  * It writes "Revealed and discarded {cards}; added {4} attack dice."
  * ("removed" for a defender), "Revealed and discarded {cards}." when no
  * Beast is revealed, and "Nothing was revealed." for an empty pile.
  */
final case class SignalTrees private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = SignalTreesCard.id
  def id: PowerId = SignalTrees.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = SignalTrees.noteKeys

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Signal Trees: two attack dice per Beast revealed",
      Vector(CampaignPlanCost.Secret(1)), context.user.toVector.map(user =>
        CampaignPlanEffect.Run(Vector(signal(user, context.side))))))

  /** What the plan does for `user` on `side`, read when the walk reaches
    * it: one branch, so the plan's shape never depends on the pile. */
  private def signal(user: PlayerId, side: CampaignPlanSide): Operation =
    Branch((ready, _) =>
      if drawn(ready, user).isEmpty then
        Vector(Note(id, _ => Some(SignalTrees.none(cardSource))))
      else Vector(BuildOps((now, _) => Right(moves(now, user, side))),
        Note(id, signalled(user, side), covers = true)))

  private def home(ready: ReadyGame, user: PlayerId): Option[Region] =
    PowerAccess.pawnSite(ready, user).flatMap(ready.game.current.map.regionOf)

  /** The top cards of `user`'s region's pile, top first. */
  private def drawn(ready: ReadyGame, user: PlayerId): Vector[WorldCardId] =
    home(ready, user).fold(Vector.empty[WorldCardId])(region =>
      ready.game.current.commonCards.discard(region)
        .takeRight(SignalTrees.Revealed).reverse)

  private def beasts(cards: Vector[WorldCardId]): Int =
    cards.count(card => catalog.suitOf(card).contains(Suit.Beast))

  /** Each drawn card to the top of the next region's pile, revealed there,
    * and the attack dice its Beasts add or remove. */
  private def moves(ready: ReadyGame, user: PlayerId,
      side: CampaignPlanSide): Vector[CoreOperation] =
    home(ready, user).toVector.flatMap { region =>
      val next = CardPlay.nextRegion(region)
      val cards = drawn(ready, user)
      val dice = SignalTrees.PerBeast * beasts(cards)
      val signed = side match
        case CampaignPlanSide.Attacker => dice
        case CampaignPlanSide.Defender => -dice
      cards.flatMap(card => Vector[CoreOperation](
        Move(Piece.Card(card),
          PositionedLocation(Location.RegionalDiscard(region)),
          PositionedLocation(Location.RegionalDiscard(next),
            StackPosition.Top)),
        Reveal(card, Location.RegionalDiscard(next)))) ++
        Option.when(dice > 0)(
          ModifyDicePool(CampaignIds.attackPool, signed)).toVector
    }

  /** The line, from the pile as the step before found it. */
  private def signalled(user: PlayerId, side: CampaignPlanSide)(
      states: NoteStates): Option[PowerNote] =
    states.previous.map { (before, _) =>
      val cards = drawn(before, user)
      val dice = SignalTrees.PerBeast * beasts(cards)
      val shown = NoteArg.Cards(cards)
      if dice == 0 then SignalTrees.revealed(cardSource, shown)
      else side match
        case CampaignPlanSide.Attacker =>
          SignalTrees.added(cardSource, shown, NoteArg.Number(dice))
        case CampaignPlanSide.Defender =>
          SignalTrees.removed(cardSource, shown, NoteArg.Number(dice))
    }

object SignalTrees:
  val id: PowerId = SignalTreesCard.power.id
  /** The most cards revealed. */
  val Revealed: Int = 3
  /** The attack dice each Beast revealed adds or removes. */
  val PerBeast: Int = 2
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Revealed and discarded {cards}; added {4} attack dice." */
  val added: NoteKey = notes.key("added", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text("; added "), NotePart.Arg(1),
    NotePart.Plural(1, " attack die.", " attack dice.")))
  /** "Revealed and discarded {cards}; removed {4} attack dice." */
  val removed: NoteKey = notes.key("removed", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text("; removed "), NotePart.Arg(1),
    NotePart.Plural(1, " attack die.", " attack dice.")))
  /** "Revealed and discarded {cards}." */
  val revealed: NoteKey = notes.key("revealed", Vector(
    NotePart.Text("Revealed and discarded "), NotePart.Arg(0),
    NotePart.Text(".")))
  /** "Nothing was revealed." */
  val none: NoteKey = notes.key("none", Vector(
    NotePart.Text("Nothing was revealed.")))

  def forCatalog(catalog: ExecutableCatalog): SignalTrees =
    new SignalTrees(catalog)
