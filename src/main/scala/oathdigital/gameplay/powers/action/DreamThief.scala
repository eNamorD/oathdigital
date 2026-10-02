package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object DreamThiefCard extends Denizen(DenizenId("70"), "Dream Thief", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.dream-thief"),
    persistent = false, cost = Cost(favor = 2),
    text = "**ACTION:** Swap any two facedown advisers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Dream Thief (card 70), ACTION: place 2 favor on this card, then swap any
  * two facedown advisers.
  *
  * Two questions, both over adviser slots, so no facedown card is named in
  * an option. The first offers every facedown adviser, denizen or Vision, of
  * every player, the player's own included, in seat then adviser order. The
  * second offers the facedown advisers of every player but the first one's
  * owner. They are asked only when at least two players hold a facedown
  * adviser; otherwise the cost stays paid and one line says so.
  *
  * The swap is one `Swap` between the two play areas, so each card keeps its
  * orientation. Knowledge follows the card: each owner knows the card they
  * receive as their own, and still knows the one they gave up. A facedown
  * card has no printed restriction, so the batch is a plain `BuildOps`.
  */
case object DreamThief extends PaidAction("denizen.dream-thief",
    Cost(favor = 2)):
  val firstDecisionId: String = "power.dream-thief.first"
  val secondDecisionId: String = "power.dream-thief.second"
  /** "{Red} swapped {Blue}'s {card} with {Green}'s {card}." */
  val swapped: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" swapped "), NotePart.Arg(1), NotePart.Text("'s "),
    NotePart.Arg(2), NotePart.Text(" with "), NotePart.Arg(3),
    NotePart.Text("'s "), NotePart.Arg(4), NotePart.Text(".")))
  /** "No two facedown advisers could be swapped." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No two facedown advisers could be swapped.")))
  override def noteKeys: Vector[NoteKey] = Vector(swapped, nothing)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askFirst(live, player, source)),
    Branch((live, pending) => askSecond(live, player, pending)),
    BuildOps((live, pending) => swap(live, pending)),
    Note(id, swappedNote(_, player, source)))))

  private final case class Slot(owner: PlayerId, slot: Int, card: CardId):
    def ref: DecisionOptionRef.AdviserSlot =
      DecisionOptionRef.AdviserSlot(owner, slot)

  private def isFacedown(held: AdviserState): Boolean = held match
    case DenizenState(_, orientation, _) => orientation == Orientation.FaceDown
    case VisionState(_, orientation) => orientation == Orientation.FaceDown

  /** Every facedown adviser, in seat then adviser order. */
  private def facedown(ready: ReadyGame): Vector[Slot] = for
    owner <- ready.game.current.players
    (held, slot) <- owner.advisers.zipWithIndex
    if isFacedown(held)
  yield Slot(owner.player, slot, held.id)

  private def options(found: Vector[Slot]): Vector[DecisionOption] =
    found.map(held => DecisionOption.AdviserSlot(held.ref))

  private def askFirst(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    val found = facedown(ready)
    if found.map(_.owner).distinct.size < 2 then
      Vector(Note(id, _ => PowerSourceRef.of(source).map(nothing(_))))
    else Vector(Decide(firstDecisionId, actor, DecisionQuery.ChooseOne(
      options(found),
      heading = Some("Dream Thief: choose a facedown adviser to swap"))))

  private def first(ready: ReadyGame, pending: PendingTree): Option[Slot] =
    PowerAnswers.one(pending, firstDecisionId).flatMap(ref =>
      facedown(ready).find(_.ref == ref))

  private def askSecond(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    first(ready, pending).toVector.map(chosen => Decide(secondDecisionId,
      actor, DecisionQuery.ChooseOne(
        options(facedown(ready).filter(_.owner != chosen.owner)),
        heading = Some("Dream Thief: choose another player's facedown " +
          "adviser to swap it with"))))

  /** No first answer means fewer than two players held a facedown adviser. */
  private def swap(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, firstDecisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => swapOf(ready, pending, ref)

  private def swapOf(ready: ReadyGame, pending: PendingTree,
      firstRef: DecisionOptionRef): Either[OathViolation, Vector[CoreOperation]] =
    for
      one <- facedown(ready).find(_.ref == firstRef).toRight(OathViolation
        .InvalidEventOrder(s"${firstRef.wireId} is not a facedown adviser"))
      answered <- PowerAnswers.one(pending, secondDecisionId)
        .toRight(PowerAnswers.missing(secondDecisionId))
      other <- facedown(ready).find(held => held.ref == answered &&
        held.owner != one.owner).toRight(OathViolation.InvalidEventOrder(
          s"${answered.wireId} is not another player's facedown adviser"))
    yield Vector(Swap(one.card, PositionedLocation(Location.PlayArea(
      one.owner)), other.card, PositionedLocation(Location.PlayArea(
      other.owner))))

  /** The two cards, read where they stood before the swap. No answer means
    * nothing could be swapped, whose line the first `Branch` already wrote. */
  private def swappedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    firstRef <- NoteSupport.answer(states, firstDecisionId)
    secondRef <- NoteSupport.answer(states, secondDecisionId)
    step <- states.previous
    one <- facedown(step._1).find(_.ref == firstRef)
    other <- facedown(step._1).find(_.ref == secondRef)
  yield swapped(card, NoteArg.Player(actor), NoteArg.Player(one.owner),
    NoteArg.Card(one.card), NoteArg.Player(other.owner),
    NoteArg.Card(other.card))
