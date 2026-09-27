package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Whistle (relic R08), ACTION: place 1 secret on this relic, take the pawn
  * of another player who is at a different site, place it at your site, and
  * move the secret from the Whistle to that player's board.
  *
  * The decision is a live `Branch` that is empty when no player qualifies. The
  * cost is paid whatever happens, so with nobody to pull the secret stays on
  * the Whistle, and the empty-card rule keeps it unusable until it is gone.
  */
case object Whistle extends PaidAction("relic.whistle", Cost(secret = 1)):
  val decisionId: String = "power.whistle.target"
  val pulled: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Placed "),
    NotePart.Arg(0), NotePart.Text(" at "), NotePart.Arg(1),
    NotePart.Text(" and gave "), NotePart.Arg(0),
    NotePart.Text(" the Whistle's secret.")))
  /** Its line when no other player stands at another site. */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No pawn could be pulled.")))
  override def noteKeys: Vector[NoteKey] = Vector(pulled, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Relic(whistle) => Right(Sequence(Vector[Operation](
      Branch((state, _) => ask(state, player)),
      BuildOps((state, pending) => pull(state, player, whistle, pending)),
      Note(id, pullNote(_, source)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))

  private def pullNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).flatMap(card =>
    NoteSupport.answer(states, decisionId) match
      case Some(DecisionOptionRef.Player(target)) =>
        PawnMoves.pawnSite(states.now, target).toOption.map(site =>
          pulled(card, NoteArg.Player(target), NoteArg.Site(site)))
      case _ => Some(nobody(card)))

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    val targets = PawnMoves.atOtherSites(ready, player)
    if targets.isEmpty then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
      targets.map(target => DecisionOption.Player(
        DecisionOptionRef.Player(target))),
      heading = Some("Whistle: choose the player whose pawn you pull to " +
        "your site"))))

  private def pull(ready: ReadyGame, player: PlayerId, whistle: RelicId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    if PawnMoves.atOtherSites(ready, player).isEmpty then Right(Vector.empty)
    else for
      here <- PawnMoves.pawnSite(ready, player)
      target <- PowerAnswers.one(pending, decisionId).collect {
        case DecisionOptionRef.Player(id) => id
      }.toRight(PowerAnswers.missing(decisionId))
      from <- PawnMoves.pawnSite(ready, target)
    yield Vector[CoreOperation](
      Move(Piece.Pawn(target), PositionedLocation(Location.Site(from)),
        PositionedLocation(Location.Site(here))),
      Move(Piece.Secrets(1), PositionedLocation(Location.OnCard(whistle)),
        PositionedLocation(Location.PlayArea(target))))
