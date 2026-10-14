package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object WardOfSilenceCard extends Denizen(DenizenId("202"), "Ward of Silence",
    Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.ward-of-silence"),
    persistent = false, cost = Cost.free,
    text = "X [secret] **ACTION:** Choose a player whose pawn is at " +
      "your site. Flip X [secret] on their board facedown.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Ward of Silence (card 202), ACTION: "X [secret] Choose a player whose
  * pawn is at your site. Flip X [secret] on their board facedown."
  *
  * The printed cost is free, so the X secrets are paid inside the action,
  * onto the card, once X is known. The user chooses another player whose
  * pawn is at their site; one with no faceup secret is shown unavailable
  * with "No faceup secrets". X runs from one to the smaller of the user's
  * faceup secrets and the target's. The payment and the flip run in one
  * batch.
  *
  * It is usable when its card could take one secret (a faceup secret, and
  * the empty-card rule, which a free printed cost does not check) and
  * another player at the site has a faceup secret. Otherwise the Act menu
  * shows it unavailable with "Nothing to do now".
  *
  * Its line is the action's: "Flipped {2 secrets} on {Blue}'s board
  * facedown." It narrates both questions.
  */
case object WardOfSilence extends PaidAction(WardOfSilenceCard.power):
  val cardId: DenizenId = WardOfSilenceCard.id
  val targetDecisionId: String = "power.ward-of-silence.target"
  val amountDecisionId: String = "power.ward-of-silence.amount"
  val noFaceupSecrets: String = "No faceup secrets"
  private val notes = NoteSet(id)
  /** "Flipped {n secrets} on {Blue}'s board facedown." */
  val flipped: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("Flipped "), NotePart.Arg(0), NotePart.Text(" on "),
    NotePart.Arg(1), NotePart.Text("'s board facedown.")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] =
    Set(targetDecisionId, amountDecisionId)

  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    Costs.affordable(ready, player, Location.OnCard(cardId),
      Cost(secret = 1)) && targets(ready, player).exists(faceUp(_) > 0)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askTarget(live, player)),
    Branch((live, pending) => askAmount(live, player, pending)),
    BuildOps((live, pending) => ward(live, player, pending)),
    Note(id, flippedNote(_, source)))))

  private def faceUp(player: PlayerState): Int = player.board.faceUpSecrets

  /** The other players whose pawns are at `actor`'s site. */
  private def targets(ready: ReadyGame, actor: PlayerId)
      : Vector[PlayerState] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site)))

  private def askTarget(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] =
    val found = targets(ready, actor)
    if !found.exists(faceUp(_) > 0) then Vector.empty
    else Vector(Decide(targetDecisionId, actor, DecisionQuery.ChooseOne(
      found.map(option), heading = Some(
        "Ward of Silence: flip secrets of a player at your site"))))

  private def option(target: PlayerState): DecisionOption =
    val shown = DecisionOption.Player(DecisionOptionRef.Player(target.player))
    if faceUp(target) > 0 then shown
    else DecisionOption.Unavailable(shown,
      OptionReason.Written(noFaceupSecrets))

  /** The answered target, while it has a faceup secret. */
  private def chosen(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Option[PlayerState] =
    PowerAnswers.one(pending, targetDecisionId).flatMap(ref =>
      targets(ready, actor).find(p =>
        DecisionOptionRef.Player(p.player) == ref && faceUp(p) > 0))

  /** The most X can be: the smaller of the two faceup counts. */
  private def most(ready: ReadyGame, actor: PlayerId, target: PlayerState)
      : Int = ready.game.current.players.find(_.player == actor)
    .fold(0)(user => math.min(faceUp(user), faceUp(target)))

  private def askAmount(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    chosen(ready, actor, pending).toVector.map(target =>
      Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(1,
        most(ready, actor, target), Some("Ward of Silence: place X " +
          "secrets on this card to flip X of their secrets facedown"),
        "Flip")))

  private def ward(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for
    target <- chosen(ready, actor, pending)
      .toRight(PowerAnswers.missing(targetDecisionId))
    amount <- PowerAnswers.amount(pending, amountDecisionId)
      .toRight(PowerAnswers.missing(amountDecisionId))
    _ <- Either.cond(amount >= 1 && amount <= most(ready, actor, target), (),
      OathViolation.InvalidEventOrder(
        s"Ward of Silence cannot flip $amount secrets"))
  yield Vector[CoreOperation](
    PayCost(actor, Location.OnCard(cardId), Cost(secret = amount)),
    FlipSecrets(target.player, amount, SecretSide.FaceUp,
      SecretSide.FaceDown))

  private def faceDown(ready: ReadyGame, player: PlayerId): Int =
    NoteSupport.board(ready, player).fold(0)(_.faceDownSecrets)

  /** The secrets the step before flipped on the target's board. */
  private def flippedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    target <- NoteSupport.answer(states, targetDecisionId).collect {
      case DecisionOptionRef.Player(player) => player }
    step <- states.previous
    count = faceDown(step._2, target) - faceDown(step._1, target)
    if count > 0
  yield flipped(card, NoteArg.Amount(count, NoteUnit.Secret),
    NoteArg.Player(target))
