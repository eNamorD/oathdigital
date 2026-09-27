package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.gameplay.powers.cardplay.FavorBankChoice
import oathdigital.model._

/** Spirit Snare (card 33), ACTION: place 1 secret on this card, then take 1
  * favor from a favor bank the player chooses. One stocked bank is taken
  * without asking. With every bank empty the cost is paid and nothing else
  * happens, so the power stays usable, unlike Vow of Obedience's REST.
  *
  * Its take line covers the generic one. The empty-banks line is written
  * only when no take line was, and covers nothing.
  */
case object SpiritSnare extends PaidAction("denizen.spirit-snare",
    Cost(secret = 1)):
  val empty: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  override def noteKeys: Vector[NoteKey] = Vector(NoteSupport.took, empty)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = SpiritSnare.choiceDecisionId(ready, player)
    val took = NoteSupport.tookNote(source, player)
    Right(Sequence(Vector(
      Branch((state, _) => FavorBankChoice.take(state, player, 1, choice,
        "Spirit Snare: take a favor from a bank")),
      Note(id, took, covers = true),
      Note(id, states => if took(states).nonEmpty then None
        else PowerSourceRef.of(source).map(empty(_))))))

  /** The power is used at most once a turn. */
  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"spirit-snare-${ready.game.current.tracks.round}-${player.value}"
