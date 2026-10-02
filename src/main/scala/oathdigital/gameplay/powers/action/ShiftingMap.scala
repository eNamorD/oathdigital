package oathdigital.gameplay.powers.action

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object ShiftingMapCard extends Relic(RelicId("R17"), "Shifting Map", value = 15, defense = 1):
  val power = PrintedPower(PowerId("relic.shifting-map"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Gain 1 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

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
