package oathdigital.gameplay.powers.action

import oathdigital.catalog.holding.DemonTailCard
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Demon Tail (relic R46), ACTION: burn 3 secrets, then gain 2 Supply, up to
  * the track's maximum. Its own line reads the Supply the track allowed, as
  * Wayside Inn's does.
  */
case object DemonTail extends PaidAction(DemonTailCard.power):
  val Supply: Int = 2
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
