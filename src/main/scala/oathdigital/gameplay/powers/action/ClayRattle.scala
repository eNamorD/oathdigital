package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Clay Rattle (relic R47), ACTION: place 2 secrets on this relic, then
  * shuffle the world deck or any region's discard pile.
  *
  * The player chooses the pile among four buttons, empty piles included:
  * the card lets any pile be chosen. The shuffle is declared with no order,
  * and the walker draws the order from its random source and records it
  * (the `Shuffle` operation). A `Branch` reads the answer, since the tree is
  * built before it is given.
  */
case object ClayRattle extends PaidAction("relic.clay-rattle",
    Cost(secret = 2)):
  val decisionId: String = "power.clay-rattle.pile"
  val shuffled: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" shuffled the "), NotePart.Arg(1), NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(shuffled)

  /** The piles it may shuffle, in the order they are offered. */
  val piles: Vector[SearchSource] =
    SearchSource.WorldDeck +: Region.all.map(SearchSource.RegionalDiscard(_))

  def ref(pile: SearchSource): DecisionOptionRef.Button =
    val key = pile match
      case SearchSource.WorldDeck => "world-deck"
      case SearchSource.RegionalDiscard(region) => region.key
    DecisionOptionRef.Button(key)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Decide(decisionId, player, DecisionQuery.ChooseOne(piles.map(pile =>
      DecisionOption.Button(ref(pile), SearchSource.name(pile).capitalize)),
      heading = Some("Clay Rattle: shuffle a pile"))),
    Branch((_, pending) => chosen(pending.answered).toVector.map(Shuffle(_))),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      pile <- chosen(states.answered)
    yield shuffled(card, NoteArg.Player(player), NoteArg.Pile(pile))))))

  private def chosen(answered: Vector[Answered]): Option[SearchSource] =
    answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(picked), _) =>
        picked
    }.flatMap(picked => piles.find(ref(_) == picked))
