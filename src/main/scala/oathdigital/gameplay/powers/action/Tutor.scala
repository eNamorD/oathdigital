package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object TutorCard extends Denizen(DenizenId("69"), "Tutor", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.tutor"),
    persistent = false, cost = Cost(favor = 1, secret = 1),
    text = "**ACTION:** Gain [secret]")
  val powers: Vector[PrintedPower] = Vector(power)

/** Tutor (card 69, adviser-only), ACTION: place 1 favor and 1 secret on this
  * card, then gain 1 secret from the shared bank, as Elders does. Its own
  * line restates the gain in place of the generic Gain line.
  */
case object Tutor extends PaidAction("denizen.tutor",
    Cost(favor = 1, secret = 1)):
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Gain.Secrets(player, 1),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Secret,
      NoteSupport.secrets), covers = true))))
