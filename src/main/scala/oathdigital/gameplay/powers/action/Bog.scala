package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Bog (card 210, site-only), ACTION: discard a relic you hold, faceup or
  * facedown, to gain 3 favor from the Beast bank.
  *
  * Free. The relic is chosen and discarded as for Arcane Brokers
  * ([[HeldRelicSpend]]). The gain takes what the Beast bank holds, up to 3,
  * capped when the batch is built (a required batch refuses a bank that
  * cannot give in full). Its line names the relic and the favor gained, and
  * covers the generic gain line. With the bank empty it names only the relic.
  * With no relic held the line says so. Holding only the Grand Scepter, it
  * writes nothing.
  */
case object Bog extends PaidAction("denizen.bog", Cost.free):
  val decisionId: String = "power.bog.relic"
  val Gained: Int = 3
  /** "{Red} discarded {relic} and gained {n favor} from {the Beast bank}." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(" from "),
    NotePart.Arg(3), NotePart.Text(".")))
  /** "{Red} discarded {relic}." */
  val discardedOnly: NoteKey = NoteKey("used.discarded", Vector(
    NotePart.Arg(0), NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{Red} held no relic." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no relic.")))
  override def noteKeys: Vector[NoteKey] =
    Vector(discarded, discardedOnly, bare)
  override def narratedDecisions: Set[String] = Set(decisionId)

  private val relic = new HeldRelicSpend(decisionId,
    "Bog: discard a relic to gain 3 favor from the Beast bank",
    (ready, player, held) =>
      val take = Gained.min(ready.banks.favor.getOrElse(Suit.Beast, 0))
      Vector[CoreOperation](Discard.Relic(held.id,
        PositionedLocation(Location.PlayArea(player)), held.tokens.secrets,
        player)) ++ Option.when(take > 0)(Gain.Favor(player, Suit.Beast, take)))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(
    relic.steps(player) :+ Note(id, discardNote(_, player, source),
      covers = true)))

  private def discardNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      relic.spentRelic(states, player) match
        case Some((spent, _)) => states.previous.map { step =>
          val favor = NoteSupport.favor(step, player)
          if favor > 0 then discarded(card, NoteArg.Player(player),
            NoteArg.Card(spent), NoteArg.Amount(favor, NoteUnit.Favor),
            NoteArg.Bank(Suit.Beast))
          else discardedOnly(card, NoteArg.Player(player), NoteArg.Card(spent))
        }
        case None => Option.when(relic.emptyHanded(states, player))(
          bare(card, NoteArg.Player(player))))
