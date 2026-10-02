package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts,
  PowerAnswers}
import oathdigital.model._

object SecondChanceCard extends Denizen(DenizenId("181"), "Second Chance", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.second-chance"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Kill one warband on the board of a player who " +
      "has an [suit-order] or [suit-discord] adviser to gain one " +
      "warband.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Second Chance (card 181), ACTION: place 1 secret on this card, then kill
  * one warband on the board of a player who has an Order or Discord adviser,
  * to gain one warband.
  *
  * The candidates are the players with a faceup Order or Discord denizen
  * adviser, the player included, in seat order. With none, the cost stays
  * paid and one line says so. The kill takes one warband from the chosen
  * board. The warband is gained only when one was killed, and in its own
  * step after the kill: a kill on the player's own board returns the warband
  * to the bank the gain takes from. The gain is best effort, so an empty
  * supply gives nothing.
  */
final case class SecondChance private (catalog: ExecutableCatalog)
    extends PaidAction(SecondChanceCard.power):
  import SecondChance._

  override def noteKeys: Vector[NoteKey] =
    Vector(gained, killedOnly, spared, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player, source)),
    Branch((live, pending) => strike(live, player, source, pending)))))

  /** The players with a faceup Order or Discord adviser, in seat order. */
  private def candidates(ready: ReadyGame): Vector[PlayerId] =
    ready.game.current.players.filter(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) =>
        catalog.suitOf(card).exists(Suits.contains)
      case _ => false
    }).map(_.player)

  private def ask(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef): Vector[Operation] =
    candidates(ready) match
      case Vector() =>
        Vector(Note(this.id, _ => PowerSourceRef.of(source).map(nobody(_))))
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(p => DecisionOption.Player(DecisionOptionRef.Player(p))),
        heading = Some("Second Chance: kill a warband on the board of a " +
          "player with a faceup Order or Discord adviser"))))

  /** Read once the target is chosen: with a warband on its board, the kill,
    * then the gain as its own step, then the line; with none, the line. */
  private def strike(ready: ReadyGame, actor: PlayerId,
      source: DecisionOptionRef, pending: PendingTree): Vector[Operation] =
    PowerAnswers.one(pending, decisionId).toVector.flatMap {
      case DecisionOptionRef.Player(target) =>
        val held = PlayerFacts.player(ready, target)
          .fold(_ => 0, _.board.warbands)
        if held == 0 then Vector(Note(this.id, _ => PowerSourceRef.of(source)
          .map(spared(_, NoteArg.Player(target)))))
        else Vector(
          BuildOps((live, _) => PlayerFacts.forceKind(live, target).map(kind =>
            Vector(Kill(Piece.Warbands(kind, Killed),
              PositionedLocation(Location.PlayArea(target)))))),
          BuildOps((live, _) => PlayerFacts.forceKind(live, actor).map(kind =>
            Vector(Gain.Warbands(actor, kind, Gained)))),
          Note(this.id, gainNote(_, actor, target, source)))
      case _ => Vector.empty
    }

  /** The player's warband change in the step before the note: the gain's,
    * or nothing when the empty supply skipped it. */
  private def gainNote(states: NoteStates, actor: PlayerId, target: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map { card =>
      val got = states.previous.fold(0)(NoteSupport.warbands(_, actor))
      if got > 0 then gained(card, NoteArg.Number(Killed),
        NoteArg.Player(target), NoteArg.Player(actor),
        NoteArg.Amount(got, NoteUnit.Warband))
      else killedOnly(card, NoteArg.Number(Killed), NoteArg.Player(target))
    }

object SecondChance:
  val id: PowerId = PowerId("denizen.second-chance")
  val decisionId: String = "power.second-chance.target"
  val Killed: Int = 1
  val Gained: Int = 1
  private val Suits: Set[Suit] = Set(Suit.Order, Suit.Discord)
  /** "Killed {n} {Blue} warband, and {Red} gained {1 warband}." */
  val gained: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband, and ", " warbands, and "), NotePart.Arg(2),
    NotePart.Text(" gained "), NotePart.Arg(3), NotePart.Text(".")))
  /** "Killed {n} {Blue} warband." */
  val killedOnly: NoteKey = NoteSupport.killedKey("used.killed")
  /** "{Blue} had no warband to kill." */
  val spared: NoteKey = NoteKey("used.spared", Vector(NotePart.Arg(0),
    NotePart.Text(" had no warband to kill.")))
  /** "No player had a faceup Order or Discord adviser." */
  val nobody: NoteKey = NoteKey("used.none", Vector(NotePart.Text(
    "No player had a faceup Order or Discord adviser.")))

  def forCatalog(catalog: ExecutableCatalog): Option[SecondChance] =
    CatalogCards.denizen(catalog, id).map(_ => new SecondChance(catalog))
