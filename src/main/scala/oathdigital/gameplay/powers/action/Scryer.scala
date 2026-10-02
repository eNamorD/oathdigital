package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.model._

object ScryerCard extends Denizen(DenizenId("19"), "Scryer", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.scryer"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Peek at any one discard pile.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Scryer (denizen 19), ACTION: place 1 secret on this card, then peek at
  * any one discard pile.
  *
  * The player chooses among the three regions' piles, empty ones included:
  * the card lets any pile be chosen. Every card in the chosen pile is
  * recorded as a `Peek`, so the player may identify it, then an `Inspect`
  * decision shows the cards top first. A pile is stored top last, so the
  * order is reversed. An empty pile asks nothing more.
  *
  * The note comes between the peeks and the `Inspect`, so it posts when the
  * player looks. Its empty variant is built by the same covering `Note`; it
  * has no peek line to cover.
  */
case object Scryer extends PaidAction(ScryerCard.power):
  val pileDecisionId: String = "power.scryer.pile"
  val inspectDecisionId: String = "power.scryer.inspect"
  /** "{Red} peeked at the {Cradle discard pile}: {cards}." */
  val peeked: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the "), NotePart.Arg(1), NotePart.Text(": "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} peeked at the {Cradle discard pile}, which was empty." */
  val peekedEmpty: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the "), NotePart.Arg(1),
    NotePart.Text(", which was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(peeked, peekedEmpty)

  /** The regions whose discard piles it may peek at, in the order offered. */
  val piles: Vector[Region] = Region.all

  def ref(region: Region): DecisionOptionRef.Button =
    DecisionOptionRef.Button(region.key)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Decide(pileDecisionId, player, DecisionQuery.ChooseOne(piles.map(region =>
      DecisionOption.Button(ref(region), name(region).capitalize)),
      heading = Some("Scryer: peek at a discard pile"))),
    BuildOps((live, pending) => Right(chosen(pending.answered).toVector
      .flatMap(region => topFirst(live, region).map(card =>
        Peek(player, card, Location.RegionalDiscard(region)))))),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      region <- chosen(states.answered)
    yield
      val pile = NoteArg.Pile(SearchSource.RegionalDiscard(region))
      val cards = topFirst(states.now, region)
      if cards.isEmpty then peekedEmpty(card, NoteArg.Player(player), pile)
      else peeked(card, NoteArg.Player(player), pile, NoteArg.Cards(cards)),
      covers = true),
    Branch((live, pending) => chosen(pending.answered).toVector.flatMap {
      region =>
        val cards = topFirst(live, region)
        Option.when(cards.nonEmpty)(Decide(inspectDecisionId, player,
          DecisionQuery.Inspect(cards,
            heading = Some(s"Scryer: the ${name(region)}")))).toVector
    }))))

  /** "Cradle discard pile". */
  private def name(region: Region): String =
    SearchSource.name(SearchSource.RegionalDiscard(region))

  /** The pile's cards, top first. */
  private def topFirst(ready: ReadyGame, region: Region): Vector[WorldCardId] =
    ready.game.current.commonCards.discard(region).reverse

  private def chosen(answered: Vector[Answered]): Option[Region] =
    answered.collectFirst {
      case Answered(`pileDecisionId`, DecisionAnswer.ChooseOneAnswer(picked),
          _) => picked
    }.flatMap(picked => piles.find(ref(_) == picked))
