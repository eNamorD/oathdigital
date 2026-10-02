package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.gameplay.powers.cardplay.FavorBankChoice
import oathdigital.model._

object SpiritSnareCard extends Denizen(DenizenId("33"), "Spirit Snare", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.spirit-snare"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Take [favor] from any one favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Spirit Snare (card 33), ACTION: place 1 secret on this card, then take 1
  * favor from a favor bank the player chooses. One stocked bank is taken
  * without asking. With every bank empty the cost is paid and nothing else
  * happens, so the power stays usable, unlike Vow of Obedience's REST.
  *
  * Its take line covers the generic one. The empty-banks line covers nothing.
  * The `Branch` picks the line with the take, as Book Binders' does, so the
  * empty-banks line never reads a step the take did not run.
  */
case object SpiritSnare extends PaidAction(SpiritSnareCard.power):
  val empty: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(NoteSupport.took, empty)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = SpiritSnare.choiceDecisionId(ready, player)
    Right(Branch((state, _) => {
      val take = FavorBankChoice.take(state, player, 1, choice,
        "Spirit Snare: take a favor from a bank")
      if take.isEmpty then
        Vector(Note(id, _ => PowerSourceRef.of(source).map(empty(_))))
      else take :+ Note(id, NoteSupport.tookNote(source, player),
        covers = true)
    }))

  /** The power is used at most once a turn. */
  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"spirit-snare-${ready.game.current.tracks.round}-${player.value}"
