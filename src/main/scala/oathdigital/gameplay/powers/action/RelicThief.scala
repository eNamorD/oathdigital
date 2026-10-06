package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.gameplay.powers.targeting.LostTongue
import oathdigital.model._

object RelicThiefCard extends Denizen(DenizenId("95"), "Relic Thief", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.relic-thief"),
    persistent = false, cost = Cost(favor = 1, secret = 1),
    text = "**ACTION:** Choose a faceup relic held by a player whose " +
      "pawn is at your site. Roll [defense-die] equal to its " +
      "defense. If you roll no [shield], take it.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Relic Thief (card 95, adviser-only), ACTION: place 1 favor and 1 secret
  * on this card, then choose a faceup relic held by another player whose
  * pawn is at your site, roll defense dice equal to its defense, and take it
  * if no shield is rolled.
  *
  * The relics are read live after the cost, in seat then relic order. A
  * relic Lost Tongue keeps the player from taking is not offered: the search
  * stops at the roll, so the `Take` its restriction refuses after the roll
  * would not hide the relic, and the command would fail after the dice. The
  * roll is automatic, from the walker's dice, on its own pool. A relic of
  * defense 0 rolls nothing and is taken. "No shield" is a shield total of
  * 0, so a Doubler alone is no shield. The take is one `Take`. With no relic
  * to choose, the cost stays paid and one line says so.
  *
  * No generic line exists for the take, so the line covers nothing, and the
  * roll keeps its "Rolled" line.
  */
final case class RelicThief private (catalog: ExecutableCatalog)
    extends PaidAction(RelicThiefCard.power):
  import RelicThief._

  private val lostTongue = LostTongue.forCatalog(catalog)

  override def noteKeys: Vector[NoteKey] = Vector(took, kept, nothing)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    Branch((live, pending) => roll(live, player, pending)),
    BuildOps((live, pending) => Right(steal(live, player, pending))),
    Note(this.id, stolenNote(_, player, source)))))

  private final case class Target(owner: PlayerId, relic: RelicId):
    def ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)

  /** The faceup relics of the other players at the player's site that Lost
    * Tongue leaves takeable, in seat then relic order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[Target] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site => for
      other <- ready.game.current.players
      if other.player != actor && other.pawnSite.contains(site) &&
        !lostTongue.protects(ready, actor, other.player)
      relic <- other.relics.collect {
        case RelicState(id, Orientation.FaceUp, _) => id }
    yield Target(other.player, relic))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    targets(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(target =>
          DecisionOption.Relic(DecisionOptionRef.Relic(target.relic))),
        heading = Some("Relic Thief: choose a faceup relic held by a player " +
          "at your site"))))

  private def chosen(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[Target] = PowerAnswers.one(pending, decisionId)
    .flatMap(ref => targets(ready, actor).find(_.ref == ref))

  private def defense(relic: RelicId): Int =
    catalog.relic(relic).fold(0)(_.defense)

  /** The chosen relic's defense dice, rolled at once; none at defense 0. */
  private def roll(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[Operation] = chosen(ready, actor, pending)
    .map(target => defense(target.relic)).filter(_ > 0).toVector
    .flatMap(dice => Vector[Operation](ModifyDicePool(pool, dice),
      Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic)))

  /** The take, when the relic rolled nothing or the roll shows no shield. */
  private def steal(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[CoreOperation] = chosen(ready, actor, pending).filter(target =>
    defense(target.relic) == 0 || RollResults.score(ready, pool) == 0)
    .toVector.map(target => Take(Piece.Card(target.relic), actor,
      Location.PlayArea(target.owner), Location.PlayArea(actor)))

  /** Whether the relic changed hands: the player holds it now, taken from
    * whoever held it before the step, or its holder kept it. */
  private def stolenNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Relic(relic)) =>
          holderOf(states.now, relic) match
            case Some(holder) if holder == actor => states.previous
              .flatMap { case (before, _) => holderOf(before, relic) }
              .fold(nothing(card))(owner =>
                took(card, NoteArg.Card(relic), NoteArg.Player(owner)))
            case Some(owner) =>
              kept(card, NoteArg.Player(owner), NoteArg.Card(relic))
            case None => nothing(card)
        case _ => nothing(card))

  private def holderOf(ready: ReadyGame, relic: RelicId): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists(_.id == relic))
      .map(_.player)

object RelicThief:
  val id: PowerId = RelicThiefCard.power.id
  val decisionId: String = "power.relic-thief.relic"
  /** Its own dice pool. */
  val pool: PoolKey = PoolKey("relic-thief")
  /** "Rolled no shield and took {relic} from {Blue}." */
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Rolled no shield and took "), NotePart.Arg(0),
    NotePart.Text(" from "), NotePart.Arg(1), NotePart.Text(".")))
  /** "Rolled a shield; {Blue} kept {relic}." */
  val kept: NoteKey = NoteKey("used.kept", Vector(
    NotePart.Text("Rolled a shield; "), NotePart.Arg(0),
    NotePart.Text(" kept "), NotePart.Arg(1), NotePart.Text(".")))
  /** "No faceup relic could be taken." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No faceup relic could be taken.")))

  def forCatalog(catalog: ExecutableCatalog): RelicThief =
    new RelicThief(catalog)
