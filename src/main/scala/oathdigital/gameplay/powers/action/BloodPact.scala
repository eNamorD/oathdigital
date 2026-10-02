package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

object BloodPactCard extends Denizen(DenizenId("62"), "Blood Pact", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.blood-pact"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Sacrifice an even number of warbands on your " +
      "board. For every two you sacrifice, gain [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Blood Pact (card 62), ACTION: place 1 secret on this card, then sacrifice
  * an even number of warbands on your board, gaining 1 secret for every two.
  *
  * With 2 or more warbands on the board, the player chooses a number of pairs
  * from 0 to half their warbands, rounded down, in a `ChooseAmount`. The
  * sacrifice and the gain run in one batch, so its line covers the generic
  * gain line. With fewer than 2 warbands nothing is asked and nothing
  * happens, and choosing 0 sacrifices nothing.
  */
case object BloodPact extends PaidAction(BloodPactCard.power):
  val decisionId: String = "power.blood-pact.pairs"
  /** "{Red} sacrificed {n warbands} and gained {m secrets}." */
  val sacrificed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" sacrificed "), NotePart.Arg(1),
    NotePart.Text(" and gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} sacrificed no warbands." */
  val spared: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" sacrificed no warbands.")))
  override def noteKeys: Vector[NoteKey] = Vector(sacrificed, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => pact(live, player, pending)),
    Note(id, pactNote(_, player, source), covers = true))))

  /** The most pairs the player's board can give. */
  private def pairs(ready: ReadyGame, player: PlayerId): Int =
    PlayerFacts.player(ready, player).toOption.fold(0)(_.board.warbands / 2)

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    val most = pairs(ready, player)
    if most == 0 then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseAmount(0, most,
      Some("Blood Pact: choose how many pairs of warbands to sacrifice. " +
        "Each pair gains a secret"), "Sacrifice")))

  /** No answer means the board held fewer than two warbands. */
  private def pact(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    if pairs(ready, player) == 0 then Right(Vector.empty)
    else for
      count <- PowerAnswers.amount(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      kind <- PlayerFacts.forceKind(ready, player)
    yield
      if count == 0 then Vector.empty
      else Vector[CoreOperation](Sacrifice(player,
        Piece.Warbands(kind, 2 * count),
        PositionedLocation(Location.PlayArea(player))),
        Gain.Secrets(player, count))

  /** The warbands the step before the note took from the board. */
  private def pactNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map { card =>
      val lost = states.previous.fold(0)(step =>
        -NoteSupport.warbands(step, player))
      if lost > 0 then sacrificed(card, NoteArg.Player(player),
        NoteArg.Amount(lost, NoteUnit.Warband),
        NoteArg.Amount(lost / 2, NoteUnit.Secret))
      else spared(card, NoteArg.Player(player))
    }
