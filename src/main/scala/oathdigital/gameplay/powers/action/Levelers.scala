package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Levelers (card 135), ACTION: place 1 secret on this card, then move 2
  * favor from the favor bank with the most favor to the bank with the
  * least. You decide ties.
  *
  * The banks are read live, after the cost. The source is a bank holding
  * the most, and the destination another bank holding the least of the
  * rest. When every bank holds the same, that is any source and any other
  * bank. The player chooses among tied banks, the source first, then the
  * destination. A single candidate is taken without asking. The move takes
  * 2 favor, or all the source holds. With every bank empty, nothing moves
  * and one line says so. Both choices are narrated: the line names the
  * banks.
  */
case object Levelers extends PaidAction("denizen.levelers", Cost(secret = 1)):
  val sourceDecisionId: String = "power.levelers.source"
  val destinationDecisionId: String = "power.levelers.destination"
  val Moved: Int = 2
  val moved: NoteKey = BankMoves.moved
  /** "Every favor bank was empty." */
  val empty: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(moved, empty)
  override def narratedDecisions: Set[String] =
    Set(sourceDecisionId, destinationDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askSource(live, player, source)),
    Branch((live, pending) => askDestination(live, player, pending)),
    BuildOps((live, pending) => Right(level(live, pending))),
    Note(id, note(_, source)))))

  private def stock(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  /** The banks holding the most favor, in suit order: every bank when all
    * hold the same, and none when all are empty. */
  def sources(ready: ReadyGame): Vector[Suit] =
    val most = Suit.all.map(stock(ready, _)).max
    if most == 0 then Vector.empty
    else Suit.all.filter(stock(ready, _) == most)

  /** The banks other than `from` holding the least favor of the rest, in
    * suit order. */
  def destinations(ready: ReadyGame, from: Suit): Vector[Suit] =
    val others = Suit.all.filterNot(_ == from)
    val least = others.map(stock(ready, _)).min
    others.filter(stock(ready, _) == least)

  private def choice(decisionId: String, player: PlayerId, banks: Vector[Suit],
      heading: String): Vector[Operation] = Vector(Decide(decisionId, player,
    DecisionQuery.ChooseOne(banks.map(suit =>
      DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some(heading))))

  private def askSource(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Vector[Operation] = sources(ready) match
    case Vector() =>
      Vector(Note(id, _ => PowerSourceRef.of(source).map(empty(_))))
    case Vector(_) => Vector.empty
    case several => choice(sourceDecisionId, player, several,
      "Levelers: move 2 favor from a bank holding the most")

  private def answered(pending: PendingTree, decisionId: String): Option[Suit] =
    PowerAnswers.one(pending, decisionId).collect {
      case DecisionOptionRef.FavorBank(suit) => suit }

  /** The source: the only candidate, or the answer. */
  private def chosenSource(ready: ReadyGame, pending: PendingTree)
      : Option[Suit] = sources(ready) match
    case Vector(only) => Some(only)
    case _ => answered(pending, sourceDecisionId)

  private def askDestination(ready: ReadyGame, player: PlayerId,
      pending: PendingTree): Vector[Operation] =
    chosenSource(ready, pending).map(destinations(ready, _)) match
      case Some(several) if several.size >= 2 => choice(destinationDecisionId,
        player, several, "Levelers: move it to a bank holding the least")
      case _ => Vector.empty

  /** The destination: the only candidate, or the answer. */
  private def chosenDestination(ready: ReadyGame, from: Suit,
      pending: PendingTree): Option[Suit] = destinations(ready, from) match
    case Vector(only) => Some(only)
    case _ => answered(pending, destinationDecisionId)

  private def level(ready: ReadyGame, pending: PendingTree)
      : Vector[CoreOperation] = (for
    from <- chosenSource(ready, pending)
    to <- chosenDestination(ready, from, pending)
    amount = math.min(Moved, stock(ready, from))
    if amount > 0
  yield BankMoves.move(amount, from, to)).toVector

  /** The move's line. With every bank empty, the source's `Branch` already
    * wrote the empty line, and no bank changed. */
  private def note(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(BankMoves.movedNote(_)(states))
