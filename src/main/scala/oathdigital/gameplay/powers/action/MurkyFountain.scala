package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Murky Fountain (edifice E15, ruined), ACTION: place 1 secret on this card.
  * If your pawn is at this site, roll 2 defense dice and gain Supply equal to
  * the total. A total of zero also ends your Act phase with `EnterPhase(Rest)`,
  * which is what Begin Rest does once its validation gate has passed. If your
  * pawn is elsewhere the cost is paid and nothing else happens, and the dice
  * are never asked for.
  *
  * The ruined face carries its own power id, so an intact Marble Fountains
  * never offers this power. The pawn test is in `build`, which is safe because
  * a pawn cannot move between the command that starts the power and the end of
  * a tree that never parks.
  */
case object MurkyFountain extends PaidAction("edifice.e15.ruined",
    Cost(secret = 1)):
  val Dice: Int = 2
  val pool: PoolKey = PoolKey("murky-fountain")
  val gained: NoteKey = NoteSupport.gainedKey("gained")
  val ended: NoteKey = NoteKey("ended", Vector(NotePart.Arg(0),
    NotePart.Text("'s Act phase ended.")))
  /** Its line when the pawn is elsewhere: the cost is paid, nothing else. */
  val away: NoteKey = NoteKey("used.away", Vector(NotePart.Arg(0),
    NotePart.Text(" was not at its site.")))
  override def noteKeys: Vector[NoteKey] = Vector(RollResults.rolled, gained,
    ended, away)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Edifice(edifice) =>
      if !atPawnSite(ready, player, edifice) then Right(Sequence(Vector(
        Note(id, _ => PowerSourceRef.of(source).map(away(_,
          NoteArg.Player(player)))))))
      else Right(Sequence(Vector(
        ModifyDicePool(pool, Dice),
        Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic),
        Note(id, RollResults.rollNote(source, player, pool), covers = true),
        BuildOps((state, _) => Right(outcome(state, player))),
        Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
          NoteSupport.supply)),
        Note(id, endNote(_, player, source)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not an edifice source"))

  /** The outcome step moved the turn out of the Act phase. */
  private def endNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    if step._1.game.current.turn.phase != step._2.game.current.turn.phase
  yield ended(card, NoteArg.Player(player))

  private def atPawnSite(ready: ReadyGame, player: PlayerId, id: EdificeId)
      : Boolean = PowerAccess.siteOf(ready, player, id)
    .exists(PowerAccess.pawnSite(ready, player).contains)

  private def outcome(state: ReadyGame, player: PlayerId)
      : Vector[CoreOperation] =
    val total = RollResults.score(state, pool)
    if total > 0 then Vector(GainSupply(player, total))
    else Vector(EnterPhase(Phase.Rest))
