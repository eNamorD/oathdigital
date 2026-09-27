package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Charming Friend (card 131, adviser-only), ACTION: place 1 secret on this
  * card, then take 1 favor from a player whose pawn is at the player's site.
  *
  * The shape is Sleight of Hand's. The targets are read live, after the cost
  * is paid, and the question is asked whenever there is one. With none, the
  * cost stays paid and nothing else happens. A chosen player with no favor
  * is still a legal choice and gives nothing. The take is a `Take`, so a
  * restriction on taking applies to it.
  */
case object CharmingFriend extends PaidAction("denizen.charming-friend",
    Cost(secret = 1)):
  val decisionId: String = "power.charming-friend.target"
  val Taken: Int = 1
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player could be robbed.")))
  /** "{Blue} had no favor to take." */
  val broke: NoteKey = NoteKey("used.empty", Vector(NotePart.Arg(0),
    NotePart.Text(" had no favor to take.")))
  override def noteKeys: Vector[NoteKey] =
    Vector(NoteSupport.took, nobody, broke)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => charm(live, player, pending)),
    Note(id, charmNote(_, player, source)))))

  /** No answer means nobody was at the site. */
  private def charmNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Player(target)) =>
          val amount = states.previous.fold(0)(NoteSupport.favor(_, actor))
          if amount > 0 then NoteSupport.took(card, NoteArg.Player(actor),
            NoteArg.Amount(amount, NoteUnit.Favor), NoteArg.Player(target))
          else broke(card, NoteArg.Player(target))
        case _ => nobody(card))

  private def targets(ready: ReadyGame, actor: PlayerId): Vector[PlayerState] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site)))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    val found = targets(ready, actor)
    if found.isEmpty then Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      found.map(p => DecisionOption.Player(DecisionOptionRef.Player(p.player))),
      heading = Some(
        "Charming Friend: take a favor from a player at your site"))))

  private def charm(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = targets(ready, actor)
    if found.isEmpty then Right(Vector.empty)
    else for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      target <- found.find(p => DecisionOptionRef.Player(p.player) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a legal Charming Friend target"))
    yield
      if target.board.favor == 0 then Vector.empty
      else Vector(Take(Piece.Favor(Taken), actor,
        Location.PlayArea(target.player), Location.PlayArea(actor)))
