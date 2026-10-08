package oathdigital.testkit

import oathdigital.model._

/** The violations the rules refuse a power use or a decision answer with,
  * spelled once so a suite names the refusal instead of copying its wording.
  * Each builds the exact value the rules return. */
object Refusals:
  /** `decision` was answered with an option it does not offer: the look-ahead
    * hid it, the card is out of reach, or the answer is out of range. */
  def notOffered(decision: String): OathViolation =
    OathViolation.InvalidEventOrder(
      s"decision $decision does not offer the selected option")

  /** `power` was used from `source`, which holds it, but it has nothing to do
    * there now. */
  def nothingToDo(power: PowerId, source: DecisionOptionRef): OathViolation =
    OathViolation.InvalidEventOrder(s"${power.value} has nothing to do from " +
      s"${source.kind}/${source.wireId}")

  /** `power` was used from `source`, which the player cannot use it from: not
    * held, facedown, or out of reach. */
  def inaccessibleSource(power: PowerId, source: DecisionOptionRef)
      : OathViolation = OathViolation.InvalidEventOrder(
    s"${source.kind}/${source.wireId} is not an accessible source of " +
      power.value)

  /** The selected modifier `power` cannot be used for this `action` ("search",
    * "travel", "recover"): its card is out of reach or facedown, or its cost
    * cannot be paid. */
  def notApplicable(power: PowerId, action: String): OathViolation =
    OathViolation.InvalidEventOrder(
      s"power ${power.value} is not applicable to this $action")
