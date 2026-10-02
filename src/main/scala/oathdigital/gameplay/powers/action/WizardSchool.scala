package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object WizardSchoolCard extends Denizen(DenizenId("34"), "Wizard School", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.wizard-school"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Gain [secret], then end your Act Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wizard School (card 34, site-only), ACTION: place 1 favor on this card,
  * gain 1 secret, then end the Act phase with `EnterPhase(Rest)`, as Murky
  * Fountain ends it. The phase always ends, so its line needs no read of
  * the state.
  */
case object WizardSchool extends PaidAction(WizardSchoolCard.power):
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  val ended: NoteKey = NoteKey("ended", Vector(NotePart.Arg(0),
    NotePart.Text("'s Act phase ended.")))
  override def noteKeys: Vector[NoteKey] = Vector(gained, ended)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Gain.Secrets(player, 1),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Secret,
      NoteSupport.secrets), covers = true),
    EnterPhase(Phase.Rest),
    Note(id, _ => PowerSourceRef.of(source).map(ended(_,
      NoteArg.Player(player)))))))
