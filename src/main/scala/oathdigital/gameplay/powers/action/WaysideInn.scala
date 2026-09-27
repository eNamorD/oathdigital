package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Wayside Inn (card 47), ACTION: place 1 favor on this card, then gain
  * 2 Supply. Its own line reads the Supply the track allowed.
  */
case object WaysideInn extends PaidAction("denizen.wayside-inn",
    Cost(favor = 1)):
  val Supply: Int = 2
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
