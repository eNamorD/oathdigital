package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object SpoiledSuppliesCard extends Denizen(DenizenId("228"), "Spoiled Supplies", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.spoiled-supplies"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Enemies with a pawn at your site each lose 1 " +
      "Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Spoiled Supplies (card 228), ACTION: place 1 favor on this card, then
  * every enemy whose pawn is at the player's site loses 1 Supply. Every game
  * is all-Exile, so every other player is an enemy. Nothing is asked.
  *
  * A player with no Supply is skipped rather than charged. Each loss is its
  * own `SpendSupply` followed by its own note, so each line reads the step
  * it restates. With nobody losing any, one line says so.
  */
case object SpoiledSupplies extends PaidAction(SpoiledSuppliesCard.power):
  val Loss: Int = 1
  /** "{Blue} lost {1 Supply}." */
  val lost: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" lost "), NotePart.Arg(1), NotePart.Text(".")))
  val nobody: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No enemy lost Supply.")))
  override def noteKeys: Vector[NoteKey] = Vector(lost, nobody)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Branch((live, _) => {
    val hit = enemies(live, player).filter(_.board.supply.supply > 0)
    if hit.isEmpty then
      Vector(Note(id, _ => PowerSourceRef.of(source).map(nobody(_))))
    else hit.flatMap(target => Vector[Operation](
      SpendSupply(target.player, Loss, required = false),
      Note(id, lostNote(_, target.player, source))))
  }))

  /** The Supply `target` lost in the step before the note. */
  private def lostNote(states: NoteStates, target: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    amount = -NoteSupport.supply(step, target)
    if amount > 0
  yield lost(card, NoteArg.Player(target), NoteArg.Amount(amount,
    NoteUnit.Supply))

  private def enemies(ready: ReadyGame, actor: PlayerId): Vector[PlayerState] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site)))
