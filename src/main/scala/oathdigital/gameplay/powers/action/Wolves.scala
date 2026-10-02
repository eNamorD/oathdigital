package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

object WolvesCard extends Denizen(DenizenId("39"), "Wolves", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.wolves"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Kill one warband _(even yours)_ on any one " +
      "board.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wolves (card 39), ACTION: place 1 secret on this card, then kill one
  * warband on any one player board, the acting player's included. The kill
  * is not the player's option: it always runs, in its best-effort form (a
  * non-required `Kill`, which does as much as the board allows). A board with
  * no warband is skipped, so nothing is killed.
  *
  * The decision is a plain `Decide`: its options are the players, which the
  * cost does not change, so `build` and `rebuild` derive the same query.
  */
case object Wolves extends PaidAction(WolvesCard.power):
  val decisionId: String = "power.wolves.board"
  val killed: NoteKey = NoteSupport.killedKey(NoteKey.Used)
  /** Its line when the chosen board had no warband: the kill is best-effort. */
  val spared: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no warband to kill.")))
  override def noteKeys: Vector[NoteKey] = Vector(killed, spared)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Decide(decisionId, player, DecisionQuery.ChooseOne(
      ready.game.current.players.map(p => DecisionOption.Player(
        DecisionOptionRef.Player(p.player))),
      heading = Some("Wolves: kill one warband on a player board"))),
    BuildOps((live, pending) => kill(live, pending)),
    Note(id, killNote(_, source)))))

  /** The warbands the chosen board lost in the kill step. */
  private def killNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    target <- NoteSupport.answer(states, decisionId).collect {
      case DecisionOptionRef.Player(board) => board }
    step <- states.previous
    lost = -NoteSupport.warbands(step, target)
  yield
    if lost > 0 then killed(card, NoteArg.Number(lost), NoteArg.Player(target))
    else spared(card, NoteArg.Player(target))

  private def kill(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for
    ref <- PowerAnswers.one(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId))
    board <- ref match
      case DecisionOptionRef.Player(id) => PlayerFacts.player(ready, id)
      case other => Left(OathViolation.InvalidEventOrder(
        s"${other.kind}/${other.wireId} is not a player board"))
    kind <- PlayerFacts.forceKind(ready, board.player)
  yield Vector(Kill(Piece.Warbands(kind, 1),
    PositionedLocation(Location.PlayArea(board.player))))
