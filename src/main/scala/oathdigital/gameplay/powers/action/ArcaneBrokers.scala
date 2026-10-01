package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Arcane Brokers (card 204), ACTION: place 1 favor on this card, then
  * discard a relic you hold, faceup or facedown, to gain 2 secrets.
  *
  * The relic is chosen and discarded as [[HeldRelicSpend]] describes. A
  * discarded relic is set aside until Chronicle, and its secrets return to
  * the player facedown. Its line names the relic and the secrets gained, not
  * those returned, and covers the generic gain line; a relic discard has no
  * generic line. With no relic held the line says so. Holding only the Grand
  * Scepter, it writes nothing.
  */
case object ArcaneBrokers extends PaidAction("denizen.arcane-brokers",
    Cost(favor = 1)):
  val decisionId: String = "power.arcane-brokers.relic"
  val Gained: Int = 2
  /** "{Red} discarded {relic} and gained {n secrets}." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} held no relic." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no relic.")))
  override def noteKeys: Vector[NoteKey] = Vector(discarded, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private val relic = new HeldRelicSpend(decisionId,
    "Arcane Brokers: discard a relic to gain 2 secrets", (_, player, held) =>
      Vector[CoreOperation](Discard.Relic(held.id,
        PositionedLocation(Location.PlayArea(player)), held.tokens.secrets,
        player), Gain.Secrets(player, Gained)))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(
    relic.steps(player) :+ Note(id, discardNote(_, player, source),
      covers = true)))

  private def discardNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      relic.spentRelic(states, player) match
        case Some((spent, tokens)) => states.previous.map(step =>
          discarded(card, NoteArg.Player(player), NoteArg.Card(spent),
            NoteArg.Amount(NoteSupport.secrets(step, player) - tokens.secrets,
              NoteUnit.Secret)))
        case None => Option.when(relic.emptyHanded(states, player))(
          bare(card, NoteArg.Player(player))))
