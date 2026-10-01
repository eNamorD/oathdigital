package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Memory of Home (card 49), ACTION: place 1 secret on this card and burn
  * 1, then move all the favor from any one favor bank to the Hearth bank.
  *
  * The banks offered are those other than Hearth that hold favor, read live
  * after the cost. One stocked bank is taken without asking. With none,
  * nothing moves and one line says so. The choice is narrated: the line
  * names the bank.
  */
case object MemoryOfHome extends PaidAction("denizen.memory-of-home",
    Cost(secret = 1, secretBurnt = 1)):
  val decisionId: String = "power.memory-of-home.bank"
  val moved: NoteKey = BankMoves.moved
  /** "Every other favor bank was empty." */
  val empty: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("Every other favor bank was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(moved, empty)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player, source)),
    BuildOps((live, pending) => Right(gather(live, pending))),
    Note(id, note(_, source)))))

  /** The banks other than Hearth that hold favor, in suit order. */
  private def stocked(ready: ReadyGame): Vector[Suit] =
    Suit.all.filter(suit => suit != Suit.Hearth &&
      ready.banks.favor.getOrElse(suit, 0) > 0)

  private def ask(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Vector[Operation] = stocked(ready) match
    case Vector() =>
      Vector(Note(id, _ => PowerSourceRef.of(source).map(empty(_))))
    case Vector(_) => Vector.empty
    case several => Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
      several.map(suit =>
        DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some("Memory of Home: move all of a bank's favor to the " +
        "Hearth bank"))))

  /** The only stocked bank, or the answer. */
  private def chosen(ready: ReadyGame, pending: PendingTree): Option[Suit] =
    stocked(ready) match
      case Vector(only) => Some(only)
      case _ => PowerAnswers.one(pending, decisionId).collect {
        case DecisionOptionRef.FavorBank(suit) => suit }

  private def gather(ready: ReadyGame, pending: PendingTree)
      : Vector[CoreOperation] = chosen(ready, pending).toVector.map(suit =>
    BankMoves.move(ready.banks.favor.getOrElse(suit, 0), suit, Suit.Hearth))

  /** The move's line. With no stocked bank, the `Branch` already wrote the
    * empty line, and no bank changed. */
  private def note(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(BankMoves.movedNote(_)(states))
