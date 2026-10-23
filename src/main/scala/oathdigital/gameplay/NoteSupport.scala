package oathdigital.gameplay

import oathdigital.model._

/** What a note reads (power log lines design, "Amounts are what
  * happened"), and the sentences several powers and procedures share. It
  * sits outside `powers` so that procedures may use it. A read compares the
  * states around the step the note restates, so a note never repeats the
  * cap logic of the operation it describes.
  */
object NoteSupport:
  /** The states before and after one journaled step. */
  type Step = (ReadyGame, ReadyGame)

  def board(ready: ReadyGame, player: PlayerId): Option[PlayerBoardState] =
    ready.game.current.players.find(_.player == player).map(_.board)

  def favor(step: Step, player: PlayerId): Int = change(step, player)(_.favor)
  def supply(step: Step, player: PlayerId): Int =
    change(step, player)(_.supply.supply)
  def secrets(step: Step, player: PlayerId): Int =
    change(step, player)(held => held.faceUpSecrets + held.faceDownSecrets)
  def warbands(step: Step, player: PlayerId): Int =
    change(step, player)(_.warbands)

  /** The bank whose favor fell in the step, if one did. */
  def bankPaid(step: Step): Option[Suit] = Suit.all.find(suit =>
    step._2.banks.favor.getOrElse(suit, 0) < step._1.banks.favor.getOrElse(suit, 0))

  def relicsGained(step: Step, player: PlayerId): Vector[RelicId] =
    relics(step._2, player).filterNot(relics(step._1, player).contains)
  def relicsLost(step: Step, player: PlayerId): Vector[RelicId] =
    relics(step._1, player).filterNot(relics(step._2, player).contains)

  /** The option `decisionId` was answered with in this action, if it was
    * asked. */
  def answer(states: NoteStates, decisionId: String): Option[DecisionOptionRef] =
    states.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _)
          if ref != Decide.Pass => ref
    }

  /** "{player} gained {amount}.", declared in the borrowing power's own
    * set. The wording is this object's and the name is the caller's, so the
    * key does not exist until the caller asks for it: taking the set makes
    * getting the key and declaring it one act. */
  def gainedKey(notes: NoteSet, name: String): NoteKey =
    notes.key(name, Vector(NotePart.Arg(0),
      NotePart.Text(" gained "), NotePart.Arg(1), NotePart.Text(".")))

  /** What `player` gained in the step before the note, by `read`. Nothing
    * gained writes nothing. */
  def gainNote(key: NoteKey, source: DecisionOptionRef, player: PlayerId,
      unit: NoteUnit, read: (Step, PlayerId) => Int)(states: NoteStates)
      : Option[LogNote] = PowerSourceRef.of(source).flatMap(card =>
    gainedNote(key, card, player, unit, read)(states))

  /** What `player` gained in the step before the note, by `read`, for a
    * power whose card is `card`. Nothing gained writes nothing. */
  def gainedNote(key: NoteKey, card: PowerSourceRef, player: PlayerId,
      unit: NoteUnit, read: (Step, PlayerId) => Int)(states: NoteStates)
      : Option[LogNote] = for
    step <- states.previous
    amount = read(step, player)
    if amount > 0
  yield key(card, NoteArg.Player(player), NoteArg.Amount(amount, unit))

  /** "{player} gained {amount} from {bank}.", declared in the borrowing
    * power's own set. */
  def gainedFromKey(notes: NoteSet, name: String): NoteKey =
    notes.key(name, Vector(
      NotePart.Arg(0), NotePart.Text(" gained "), NotePart.Arg(1),
      NotePart.Text(" from "), NotePart.Arg(2), NotePart.Text(".")))

  /** The favor `player` took from one bank in the step before the note. */
  def gainedFromNote(key: NoteKey, card: PowerSourceRef, player: PlayerId)(
      states: NoteStates): Option[LogNote] = for
    step <- states.previous
    amount = favor(step, player)
    if amount > 0
    bank <- bankPaid(step)
  yield key(card, NoteArg.Player(player), NoteArg.Amount(amount, NoteUnit.Favor),
    NoteArg.Bank(bank))

  /** "Killed {n} {player} warband.", declared in the borrowing power's own
    * set. */
  def killedKey(notes: NoteSet, name: String): NoteKey =
    notes.key(name, Vector(
      NotePart.Text("Killed "), NotePart.Arg(0), NotePart.Text(" "),
      NotePart.Arg(1), NotePart.Plural(0, " warband.", " warbands.")))

  /** The warbands `player` lost in the step before the note. */
  def killedNote(key: NoteKey, card: PowerSourceRef, player: PlayerId)(
      states: NoteStates): Option[LogNote] = for
    step <- states.previous
    lost = -warbands(step, player)
    if lost > 0
  yield key(card, NoteArg.Number(lost), NoteArg.Player(player))

  /** The keys this object owns outright, which no one power owns. A power
    * that writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("NoteSupport")

  /** "{player} took {amount} from {whom}.": a REST power's take, or a secret
    * taken from a player. */
  val took: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))

  /** The favor `player` took from one bank in the step before the note. */
  def tookNote(source: DecisionOptionRef, player: PlayerId)(states: NoteStates)
      : Option[LogNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    amount = favor(step, player)
    if amount > 0
    bank <- bankPaid(step)
  yield took(card, NoteArg.Player(player),
    NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Bank(bank))

  private def change(step: Step, player: PlayerId)(
      read: PlayerBoardState => Int): Int =
    board(step._2, player).fold(0)(read) - board(step._1, player).fold(0)(read)

  private def relics(ready: ReadyGame, player: PlayerId): Vector[RelicId] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.relics.map(_.id))
