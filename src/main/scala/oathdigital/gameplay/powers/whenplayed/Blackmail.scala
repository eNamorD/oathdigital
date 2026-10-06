package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.gameplay.powers.targeting.LostTongue
import oathdigital.model._

object BlackmailCard extends Denizen(DenizenId("82"), "Blackmail", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.blackmail"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** choose a relic held by a player whose pawn " +
      "is at your site. You take the relic unless they give you " +
      "[favor] [favor] [favor].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Blackmail (card 82), WHEN PLAYED: choose a relic held by a player whose
  * pawn is at your site. You take the relic unless they give you 3 favor.
  *
  * The relics are read live: those of every other player whose pawn stands
  * at the actor's pawn site, in seat then relic order, faceup or facedown. A
  * faceup relic is offered as itself and a facedown one by its slot, which
  * shows every viewer only "{owner} facedown relic". A relic Lost Tongue
  * keeps the actor from taking is not offered. Its holder then chooses, as
  * the owner of the question, to give 3 favor or hand the relic over; a
  * holder with fewer than 3 favor is not asked, and the relic is taken. The
  * favor moves by a `Give` and the relic by a `Take`. With no relic to
  * choose, one line says so.
  *
  * No generic line exists for either move, so the lines cover nothing.
  */
final case class Blackmail private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = BlackmailCard.id
  import Blackmail._
  def id: PowerId = Blackmail.id

  private val lostTongue = LostTongue.forCatalog(catalog)

  override def noteKeys: Vector[NoteKey] = Vector(paid, took, none)
  override def narratedDecisions: Set[String] =
    Set(relicDecisionId, answerDecisionId)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      Branch((live, _) => askRelic(live, actor)),
      Branch((live, pending) => askHolder(live, actor, pending)),
      Branch((live, pending) => settle(live, actor, pending)))

  /** One relic the actor may choose: its holder, its slot and its state. */
  private final case class Target(owner: PlayerId, slot: Int,
      relic: RelicState):
    def ref: DecisionOptionRef = relic.orientation match
      case Orientation.FaceUp => DecisionOptionRef.Relic(relic.id)
      case Orientation.FaceDown => DecisionOptionRef.RelicSlot(owner, slot)
    def option: DecisionOption = relic.orientation match
      case Orientation.FaceUp =>
        DecisionOption.Relic(DecisionOptionRef.Relic(relic.id))
      case Orientation.FaceDown =>
        DecisionOption.RelicSlot(DecisionOptionRef.RelicSlot(owner, slot))

  /** The relics of the other players at the actor's site that Lost Tongue
    * leaves takeable, in seat then relic order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[Target] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site => for
      other <- ready.game.current.players
      if other.player != actor && other.pawnSite.contains(site) &&
        !lostTongue.protects(ready, actor, other.player)
      (relic, slot) <- other.relics.zipWithIndex
    yield Target(other.player, slot, relic))

  private def askRelic(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    targets(ready, actor) match
      case Vector() => Vector(Note(id, _ => Some(none(cardSource))))
      case found => Vector(Decide(relicDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(_.option), heading = Some(
          "Blackmail: choose a relic held by a player at your site"))))

  private def chosen(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[Target] = PowerAnswers.one(pending, relicDecisionId)
    .flatMap(ref => targets(ready, actor).find(_.ref == ref))

  /** The holder's question, when they hold the price. */
  private def askHolder(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    chosen(ready, actor, pending).filter(target =>
        NoteSupport.board(ready, target.owner).exists(_.favor >= Price))
      .toVector.map(target => Decide(answerDecisionId, target.owner,
        DecisionQuery.ChooseOne(Vector(
          DecisionOption.Button(pay, s"Give $Price favor"),
          DecisionOption.Button(handOver, "Hand over the relic")),
          heading = Some(s"Blackmail: give $Price favor to keep your relic, " +
            "or hand it over"))))

  /** The favor given, or the relic taken, then its line. */
  private def settle(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[Operation] = chosen(ready, actor, pending).toVector.flatMap {
    target =>
      if PowerAnswers.one(pending, answerDecisionId).contains(pay) then Vector(
        BuildOps((_, _) => Right(Vector(Give(Piece.Favor(Price), target.owner,
          Location.PlayArea(target.owner), Location.PlayArea(actor))))),
        Note(id, paidNote(_, actor, target)))
      else Vector(
        BuildOps((_, _) => Right(Vector(Take(Piece.Card(target.relic.id),
          actor, Location.PlayArea(target.owner),
          Location.PlayArea(actor))))),
        Note(id, tookNote(_, actor, target)))
  }

  /** The favor the actor gained in the step before. */
  private def paidNote(states: NoteStates, actor: PlayerId, target: Target)
      : Option[PowerNote] = for
    step <- states.previous
    gained = NoteSupport.favor(step, actor)
    if gained > 0
  yield paid(cardSource, NoteArg.Player(target.owner),
    NoteArg.Amount(gained, NoteUnit.Favor), NoteArg.Card(target.relic.id))

  /** That the actor now holds the relic. */
  private def tookNote(states: NoteStates, actor: PlayerId, target: Target)
      : Option[PowerNote] = Option.when(states.now.game.current.players
    .exists(held => held.player == actor &&
      held.relics.exists(_.id == target.relic.id)))(
    took(cardSource, NoteArg.Card(target.relic.id),
      NoteArg.Player(target.owner)))

object Blackmail:
  val id: PowerId = BlackmailCard.power.id
  val relicDecisionId: String = "cardplay.blackmail.relic"
  val answerDecisionId: String = "cardplay.blackmail.answer"
  /** The favor that keeps the relic. */
  val Price: Int = 3
  val pay: DecisionOptionRef.Button = DecisionOptionRef.Button("pay")
  val handOver: DecisionOptionRef.Button =
    DecisionOptionRef.Button("hand-over")
  /** "{Blue} gave {3 favor} to keep {relic}." */
  val paid: NoteKey = NoteKey("paid", Vector(NotePart.Arg(0),
    NotePart.Text(" gave "), NotePart.Arg(1), NotePart.Text(" to keep "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Took {relic} from {Blue}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Text("Took "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "No relic could be taken." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No relic could be taken.")))

  def forCatalog(catalog: ExecutableCatalog): Blackmail =
    new Blackmail(catalog)
