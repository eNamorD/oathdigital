package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object EldersCard extends Denizen(DenizenId("26"), "Elders", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.elders"),
    persistent = false, cost = Cost(favor = 2),
    text = "**ACTION:** Gain [secret].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Elders (card 26), ACTION: place 2 favor on this card, then gain 1 secret
  * from the shared bank, which holds an unlimited supply. Its own line
  * restates the gain in place of the generic Gain line.
  */
case object Elders extends PaidAction("denizen.elders", Cost(favor = 2)):
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Gain.Secrets(player, 1),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Secret,
      NoteSupport.secrets), covers = true))))
