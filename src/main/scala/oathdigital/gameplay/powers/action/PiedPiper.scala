package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.actions.AdviserFace
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object PiedPiperCard extends Denizen(DenizenId("182"), "Pied Piper", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.pied-piper"),
    persistent = false, cost = Cost(secret = 1),
    text = "This card ignores the adviser limit. **ACTION:** Move this " +
      "card to any other player's advisers. Take [favor] [favor] " +
      "from them.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Pied Piper (card 182, adviser-only): "This card ignores the adviser
  * limit. ACTION: Move this card to any other player's advisers. Take
  * [favor] [favor] from them."
  *
  * Faceup it does not count toward its holder's limit. A transform at
  * `SearchPlayAdviser` marks it (`CardPlayRules.ignoreLimit`) whenever the
  * acting player holds it or plays it, and card play checks the advisers as
  * they would be after the play. Facedown it has no power and counts.
  * `AdviserLimit` reads `ignored`.
  *
  * The ACTION places 1 secret on this card, then asks which other player
  * takes it: any player, at any site. The card moves into their advisers
  * with its secret, even past their limit, since it does not count there
  * either. Then up to 2 favor is taken from them, best effort. The move is
  * a required `Give`, so a restriction that refuses it fails the action
  * rather than skipping it.
  */
case object PiedPiper extends PaidAction(PiedPiperCard.power)
    with ContributingPower:
  val decisionId: String = "power.pied-piper.target"
  val Taken: Int = 2
  private val notes = NoteSet(id)
  /** "Moved to {Blue}'s advisers and took {2 favor} from them." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("Moved to "), NotePart.Arg(0),
    NotePart.Text("'s advisers and took "), NotePart.Arg(1),
    NotePart.Text(" from them.")))
  /** "Moved to {Blue}'s advisers. {Blue} had no favor to take." */
  val broke: NoteKey = notes.key("used.empty", Vector(
    NotePart.Text("Moved to "), NotePart.Arg(0),
    NotePart.Text("'s advisers. "), NotePart.Arg(1),
    NotePart.Text(" had no favor to take.")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(this.id.value)
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] = Set(decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if tree.card == PiedPiperCard.id ||
              holds(ctx.state, ctx.actor) =>
          tree.adjust(children)(_.ignoreLimit(
            AdviserFace(PiedPiperCard.id, Orientation.FaceUp)))
        case _ => children
      })))

  /** The advisers of `player` that Pied Piper keeps from counting: itself,
    * faceup. */
  def ignored(ready: ReadyGame, player: PlayerId): Vector[AdviserFace] =
    advisers(ready, player).collect {
      case DenizenState(card, Orientation.FaceUp, _)
          if card == PiedPiperCard.id =>
        AdviserFace(card, Orientation.FaceUp)
    }

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => lead(live, player, pending), required = true),
    Note(this.id, ledNote(_, player, source)))))

  private def advisers(ready: ReadyGame, player: PlayerId)
      : Vector[AdviserState] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    advisers(ready, player).exists(_.id == PiedPiperCard.id)

  private def receivers(ready: ReadyGame, actor: PlayerId)
      : Vector[PlayerState] =
    ready.game.current.players.filter(_.player != actor)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      receivers(ready, actor).map(p =>
        DecisionOption.Player(DecisionOptionRef.Player(p.player))),
      heading = Some("Pied Piper: move it to another player's advisers"))))

  private def lead(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      receiver <- receivers(ready, actor)
        .find(p => DecisionOptionRef.Player(p.player) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a player Pied Piper can move to"))
    yield
      val give = Give(Piece.Card(PiedPiperCard.id), actor,
        Location.PlayArea(actor), Location.PlayArea(receiver.player),
        required = true)
      val taken = math.min(Taken, receiver.board.favor)
      if taken == 0 then Vector(give)
      else Vector(give, Take(Piece.Favor(taken), actor,
        Location.PlayArea(receiver.player), Location.PlayArea(actor)))

  private def ledNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[LogNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId).collect {
        case DecisionOptionRef.Player(receiver) =>
          val amount = states.previous.fold(0)(NoteSupport.favor(_, actor))
          if amount > 0 then moved(card, NoteArg.Player(receiver),
            NoteArg.Amount(amount, NoteUnit.Favor))
          else broke(card, NoteArg.Player(receiver), NoteArg.Player(receiver))
      })
