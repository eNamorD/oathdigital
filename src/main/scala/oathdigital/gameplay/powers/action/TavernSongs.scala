package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Tavern Songs (card 54), ACTION: peek at the top three cards of your
  * region's discard pile.
  *
  * Free. The pile is the discard pile of the region of the player's pawn
  * site. Its top three cards, or fewer, are recorded as `Peek`s, so the
  * player may identify them, then an `Inspect` decision shows them top
  * first, as Scryer's does. A pile is stored top last, so the order is
  * reversed. An empty pile shows nothing and asks nothing.
  *
  * Its line comes between the peeks and the `Inspect`, so it posts when the
  * player looks, and covers the peek lines. Its empty variant is built by
  * the same covering `Note`; it has no peek line to cover.
  */
case object TavernSongs extends PaidAction("denizen.tavern-songs",
    Cost.free):
  val inspectDecisionId: String = "power.tavern-songs.inspect"
  /** How many cards it peeks at. */
  val Depth: Int = 3
  /** "{Red} peeked at the top of the {Cradle discard pile}: {cards}." */
  val peeked: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the top of the "), NotePart.Arg(1),
    NotePart.Text(": "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} peeked at the {Cradle discard pile}, which was empty." */
  val peekedEmpty: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the "), NotePart.Arg(1),
    NotePart.Text(", which was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(peeked, peekedEmpty)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => Right(region(live, player).toVector.flatMap(at =>
      top(live, at).map(card =>
        Peek(player, card, Location.RegionalDiscard(at)))))),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      at <- region(states.now, player)
    yield
      val pile = NoteArg.Pile(SearchSource.RegionalDiscard(at))
      val cards = top(states.now, at)
      if cards.isEmpty then peekedEmpty(card, NoteArg.Player(player), pile)
      else peeked(card, NoteArg.Player(player), pile, NoteArg.Cards(cards)),
      covers = true),
    Branch((live, _) => region(live, player).toVector.flatMap { at =>
      val cards = top(live, at)
      Option.when(cards.nonEmpty)(Decide(inspectDecisionId, player,
        DecisionQuery.Inspect(cards, heading = Some("Tavern Songs: the top " +
          s"of the ${SearchSource.name(SearchSource.RegionalDiscard(at))}"))))
        .toVector
    }))))

  /** The region of the player's pawn site. */
  private def region(ready: ReadyGame, player: PlayerId): Option[Region] =
    PowerAccess.pawnSite(ready, player).flatMap(ready.game.current.map.regionOf)

  /** The pile's top cards, top first. */
  private def top(ready: ReadyGame, at: Region): Vector[WorldCardId] =
    ready.game.current.commonCards.discard(at).reverse.take(Depth)
