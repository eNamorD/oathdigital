package oathdigital.gameplay.powers.action

import oathdigital.catalog.holding.ShiftingMapCard
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Shifting Map (relic R17), ACTION: place 1 secret on this relic, then gain
  * 1 Supply, up to the track's maximum. Its own line reads the Supply the
  * track allowed, as Wayside Inn's does.
  */
case object ShiftingMap extends PaidAction(ShiftingMapCard.power):
  val Supply: Int = 1
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
