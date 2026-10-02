package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object EnchantressCard extends Denizen(DenizenId("96"), "Enchantress", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.enchantress"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Swap this card with any faceup adviser.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Enchantress (card 96, adviser-only), ACTION: place 1 secret on this card,
  * then swap it with any faceup adviser.
  *
  * The candidates are the faceup denizen advisers of every other player, in
  * seat then adviser order, read live after the cost is paid. The swap is one
  * `Swap` between the two play areas: both cards stay faceup and carry their
  * favor and secrets, Enchantress' new secret included. It runs as a required
  * batch, so the Locked restriction, which refuses swapping a locked card,
  * hides a locked adviser from the choice. The question passes when nothing
  * is left. With no candidate the cost stays paid and one line says so.
  */
case object Enchantress extends PaidAction("denizen.enchantress",
    Cost(secret = 1)):
  val decisionId: String = "power.enchantress.adviser"
  /** "{Red} swapped it for {Blue}'s {card}." */
  val swapped: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" swapped it for "), NotePart.Arg(1), NotePart.Text("'s "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "No faceup adviser could be swapped." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No faceup adviser could be swapped.")))
  override def noteKeys: Vector[NoteKey] = Vector(swapped, nothing)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Denizen(card) => Right(Sequence(Vector[Operation](
      Branch((live, _) => ask(live, player)),
      BuildOps((live, pending) => swap(live, player, card, pending),
        required = true),
      Note(id, swappedNote(_, player, source)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a denizen source"))

  private final case class Candidate(owner: PlayerId, card: DenizenId)

  /** Every other player's faceup denizen advisers. */
  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] =
    for
      other <- ready.game.current.players if other.player != actor
      card <- other.advisers.collect {
        case DenizenState(id, Orientation.FaceUp, _) => id }
    yield Candidate(other.player, card)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(c => DecisionOption.Denizen(DecisionOptionRef.Denizen(c.card))),
        heading = Some("Enchantress: swap it with another player's faceup " +
          "adviser")), passWhenEmpty = true))

  /** No answer means no adviser could be swapped. */
  private def swap(ready: ReadyGame, actor: PlayerId, enchantress: DenizenId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => candidates(ready, actor)
        .find(c => DecisionOptionRef.Denizen(c.card) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a faceup adviser Enchantress can swap with"))
        .map(chosen => Vector(Swap(enchantress,
          PositionedLocation(Location.PlayArea(actor)), chosen.card,
          PositionedLocation(Location.PlayArea(chosen.owner)))))

  /** The card taken and its owner, read where it stood before the swap. */
  private def swappedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      case DecisionOptionRef.Denizen(target) <-
        NoteSupport.answer(states, decisionId)
      step <- states.previous
      chosen <- candidates(step._1, actor).find(_.card == target)
    yield swapped(card, NoteArg.Player(actor), NoteArg.Player(chosen.owner),
      NoteArg.Card(target))).getOrElse(nothing(card)))
