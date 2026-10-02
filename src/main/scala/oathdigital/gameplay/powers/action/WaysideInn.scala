package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object WaysideInnCard extends Denizen(DenizenId("47"), "Wayside Inn", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.wayside-inn"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Gain 2 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wayside Inn (card 47), ACTION: place 1 favor on this card, then gain
  * 2 Supply. Its own line reads the Supply the track allowed.
  */
case object WaysideInn extends PaidAction(WaysideInnCard.power):
  val Supply: Int = 2
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
