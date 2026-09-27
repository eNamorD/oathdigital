package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Oracular Pig (relic R14), ACTION, no cost: peek at the top 3 cards of the
  * world deck.
  *
  * Each card, or every card when the deck holds fewer, is recorded as a
  * `Peek`, then an `Inspect` decision shows them top first; the world deck
  * is stored top first. An empty deck peeks at nothing, asks nothing and
  * writes no line: the spec gives it none.
  */
case object OracularPig extends PaidAction("relic.oracular-pig", Cost.free):
  val inspectDecisionId: String = "power.oracular-pig.inspect"
  val Count: Int = 3
  /** "{Red} peeked at the top of the world deck: {cards}." */
  val peeked: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the top of the world deck: "), NotePart.Arg(1),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(peeked)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => Right(top(live).map(card =>
      Peek(player, card, Location.Deck(CardDeck.World))))),
    Note(id, states => PowerSourceRef.of(source)
      .filter(_ => top(states.now).nonEmpty)
      .map(peeked(_, NoteArg.Player(player), NoteArg.Cards(top(states.now)))),
      covers = true),
    Branch((live, _) => Option.when(top(live).nonEmpty)(Decide(
      inspectDecisionId, player, DecisionQuery.Inspect(top(live),
        heading = Some("Oracular Pig: the top of the world deck")))).toVector))))

  /** The top of the world deck, top first. */
  private def top(ready: ReadyGame): Vector[WorldCardId] =
    ready.game.current.commonCards.worldDeck.take(Count)
