package oathdigital.gameplay.powerresolver

import oathdigital.model._

/** The question an automatic power's use waits on when only waivable
  * restrictions refuse it (automatic power consent design, "The power-use
  * step"). Its owner is the power's user, who never answers it: the
  * refusing cards' rulers decide through consent, and their answer
  * answers it. It is never shown, and the log tells it through the
  * consent lines. */
object PowerUseStep:
  private val Prefix = "power-use."

  /** The answer that lets the power work. */
  val use: DecisionOptionRef.Button = DecisionOptionRef.Button("use")
  /** The answer that deactivates it. */
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")

  def decisionId(power: PowerId): String = s"$Prefix${power.value}"
  def isDecision(decisionId: String): Boolean =
    decisionId.startsWith(Prefix)

  /** The latest answer to `power`'s step in `answered`. */
  def answer(answered: Vector[Answered], power: PowerId)
      : Option[DecisionOptionRef] = answered.reverseIterator.collectFirst {
    case Answered(id, DecisionAnswer.ChooseOneAnswer(ref), _)
        if id == decisionId(power) => ref }

  private val notes = NoteSet.unowned("power-use step")
  /** "{card} stays deactivated.", under the refusing card, when a refusal
    * remembered this turn deactivates the power unasked. Each waivable
    * power-use restriction borrows it. */
  val staysDeactivated: NoteKey = notes.key("stays-deactivated", Vector(
    NotePart.Arg(0), NotePart.Text(" stays deactivated.")))
