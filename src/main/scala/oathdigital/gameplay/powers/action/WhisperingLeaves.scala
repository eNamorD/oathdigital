package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

object WhisperingLeavesCard extends Denizen(DenizenId("211"), "Whispering Leaves", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.whispering-leaves"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Choose a player whose pawn is at your site. " +
      "They **must** place [favor] [favor] on this card.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Whispering Leaves (card 211, adviser-only), ACTION: place 1 secret on this
  * card, then choose a player whose pawn is at your site. They must place 2
  * favor on this card.
  *
  * The candidates are every player at the player's site, the player
  * included, in seat order, so the question is always asked. Pawns do not
  * move while the cost is paid, so the decision is a plain `Decide`. The
  * chosen player gives 2 favor from their board, or all they have, with a
  * `Give` onto Whispering Leaves; nothing is asked of them. The favor stays
  * on the card until Rest returns it to the Beast bank, as any favor on a
  * card does.
  */
case object WhisperingLeaves extends PaidAction("denizen.whispering-leaves",
    Cost(secret = 1)):
  val decisionId: String = "power.whispering-leaves.target"
  val Placed: Int = 2
  /** "{Blue} placed {n favor} on it." */
  val placed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" placed "), NotePart.Arg(1), NotePart.Text(" on it.")))
  /** "{Blue} had no favor to place." */
  val broke: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" had no favor to place.")))
  override def noteKeys: Vector[NoteKey] = Vector(placed, broke)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Denizen(leaves) => Right(Sequence(Vector[Operation](
      Decide(decisionId, player, DecisionQuery.ChooseOne(
        targets(ready, player).map(target => DecisionOption.Player(
          DecisionOptionRef.Player(target))),
        heading = Some("Whispering Leaves: choose a player at your site to " +
          "place 2 favor on it"))),
      BuildOps((live, pending) => give(live, player, leaves, pending)),
      Note(id, placedNote(_, source)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a denizen source"))

  /** Every player whose pawn is at `actor`'s site, in seat order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(_.pawnSite.contains(site))
        .map(_.player))

  private def give(ready: ReadyGame, actor: PlayerId, leaves: DenizenId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      target <- targets(ready, actor).find(DecisionOptionRef.Player(_) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a player at the actor's site"))
      state <- PlayerFacts.player(ready, target)
    yield
      val amount = math.min(Placed, state.board.favor)
      if amount == 0 then Vector.empty
      else Vector(Give(Piece.Favor(amount), target, Location.PlayArea(target),
        Location.OnCard(leaves)))

  /** The favor the chosen player lost in the step before the note. With
    * nothing to give no step ran, and the step before changed no favor. */
  private def placedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    case DecisionOptionRef.Player(target) <-
      NoteSupport.answer(states, decisionId)
  yield
    val amount = -states.previous.fold(0)(NoteSupport.favor(_, target))
    if amount > 0 then placed(card, NoteArg.Player(target),
      NoteArg.Amount(amount, NoteUnit.Favor))
    else broke(card, NoteArg.Player(target))
