package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object ConvoysCard extends Denizen(DenizenId("151"), "Convoys", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.convoys"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Take another region's discard pile and put it " +
      "on top of your region's discard pile _(even if empty)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Convoys (card 151, site-only), ACTION: place 1 favor on this card, then
  * take another region's discard pile and put it on top of your region's,
  * even if it is empty.
  *
  * Your region is the pawn's. The two other regions' piles are offered as
  * buttons with their own labels, in region order: the `DiscardPile` option
  * is the pile a card is discarded to or a Search draws from, which is not
  * this card's question. The pile moves card by card, bottom first, each to
  * the top of the pawn's region's pile, so it keeps its order on top. An
  * empty pile moves nothing, and the line still names both piles. No generic
  * line exists for these moves.
  */
case object Convoys extends PaidAction(ConvoysCard.power):
  val decisionId: String = "power.convoys.pile"
  private val notes = NoteSet(id)
  /** "Moved the {pile} onto the {pile}." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("Moved the "), NotePart.Arg(0), NotePart.Text(" onto the "),
    NotePart.Arg(1), NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] = Set(decisionId)

  /** The button for `region`'s pile. */
  def ref(region: Region): DecisionOptionRef.Button =
    DecisionOptionRef.Button(region.key)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => move(live, player, pending)),
    Note(id, movedNote(_, player, source)))))

  private def home(ready: ReadyGame, actor: PlayerId): Option[Region] =
    PowerAccess.pawnSite(ready, actor).flatMap(ready.game.current.map.regionOf)

  /** The other regions, in region order. */
  private def others(ready: ReadyGame, actor: PlayerId): Vector[Region] =
    home(ready, actor).toVector.flatMap(at => Region.all.filterNot(_ == at))

  private def pile(region: Region): SearchSource =
    SearchSource.RegionalDiscard(region)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    others(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(region => DecisionOption.Button(ref(region),
          s"Move the ${SearchSource.name(pile(region))}")),
        heading = Some("Convoys: put another region's discard pile on top " +
          "of your region's"))))

  /** The region whose pile `answered` chose. */
  private def chosen(ready: ReadyGame, actor: PlayerId,
      answered: Option[DecisionOptionRef]): Option[Region] =
    answered.flatMap(choice => others(ready, actor).find(ref(_) == choice))

  private def move(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for
    to <- home(ready, actor).toRight(OathViolation.PawnSiteMissing(actor))
    from <- chosen(ready, actor, PowerAnswers.one(pending, decisionId))
      .toRight(PowerAnswers.missing(decisionId))
  yield ready.game.current.commonCards.discard(from).map(card => Move(
    Piece.Card(card), PositionedLocation(Location.RegionalDiscard(from)),
    PositionedLocation(Location.RegionalDiscard(to), StackPosition.Top)))

  private def movedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    to <- home(states.now, actor)
    from <- chosen(states.now, actor, NoteSupport.answer(states, decisionId))
  yield moved(card, NoteArg.Pile(pile(from)), NoteArg.Pile(pile(to)))
