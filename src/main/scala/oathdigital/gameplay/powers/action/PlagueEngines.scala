package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object PlagueEnginesCard extends Denizen(DenizenId("65"), "Plague Engines", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.plague-engines"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Each player _(even you)_ places one [favor] per " +
      "site they rule into the [suit-arcane] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Plague Engines (card 65), ACTION: place 1 secret on this card and burn
  * 1, then each player, even you, places 1 favor per site they rule into
  * the Arcane bank.
  *
  * In seat order from the player, each player gives from their board 1
  * favor per site they rule, or all they have, with a `Give`. Nothing is
  * asked. One line per player who paid; with none, one line says so.
  */
case object PlagueEngines extends PaidAction("denizen.plague-engines",
    Cost(secret = 1, secretBurnt = 1)):
  /** "{Blue} put {n favor} into {the Arcane bank}." */
  val paid: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" put "), NotePart.Arg(1), NotePart.Text(" into "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "No player put favor into {the Arcane bank}." */
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No player put favor into "), NotePart.Arg(0),
    NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(paid, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val order = seated(ready, player)
    Right(Sequence(Vector[Operation](
      BuildOps((live, _) => Right(order.flatMap(give(live, _))))) ++
      order.map(payer => Note(id, paidNote(_, payer, source))) :+
      Note(id, nobodyNote(_, source))))

  /** Every player in seat order, from `player`. */
  private def seated(ready: ReadyGame, player: PlayerId): Vector[PlayerId] =
    val all = ready.game.current.players.map(_.player)
    val at = all.indexOf(player).max(0)
    all.drop(at) ++ all.take(at)

  private def give(ready: ReadyGame, payer: PlayerId): Option[CoreOperation] =
    val owed = PowerAccess.ruledSites(ready, payer).size
      .min(NoteSupport.board(ready, payer).fold(0)(_.favor))
    Option.when(owed > 0)(Give(Piece.Favor(owed), payer,
      Location.PlayArea(payer), Location.FavorBank(Suit.Arcane)))

  /** What `payer` gave in the step before the note. */
  private def paidNote(states: NoteStates, payer: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    gave = -NoteSupport.favor(step, payer)
    if gave > 0
  yield paid(card, NoteArg.Player(payer), NoteArg.Amount(gave, NoteUnit.Favor),
    NoteArg.Bank(Suit.Arcane))

  /** The line when the Arcane bank gained nothing in the step before it. */
  private def nobodyNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    if states.previous.forall((before, after) =>
      after.banks.favor.getOrElse(Suit.Arcane, 0) <=
        before.banks.favor.getOrElse(Suit.Arcane, 0))
  yield nobody(card, NoteArg.Bank(Suit.Arcane))
