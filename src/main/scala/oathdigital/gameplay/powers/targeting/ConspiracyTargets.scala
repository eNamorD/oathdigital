package oathdigital.gameplay.powers.targeting

import oathdigital.model._

/** Narrows a played Conspiracy's target decision for a rule that protects
  * some of its targets (the Circlet of Command, the Forgotten Vault).
  *
  * An `OptionRestriction` does not fit there: a decision left with no option
  * must be dropped, so that the Conspiracy plays and takes nothing. The rule
  * narrows the decision with a transform instead. No hide hook runs for a
  * transform, so the rule's line is a `Note` put before the decision whenever
  * an option was dropped, and in the decision's place when every option was.
  */
private[targeting] object ConspiracyTargets:
  /** `operations` without the options `protects` names, after `power`'s
    * `note` when any was dropped. */
  def narrowed(power: PowerId, operations: Vector[Operation],
      protects: DecisionOptionRef => Boolean, note: => Option[PowerNote])
      : Vector[Operation] =
    val kept = operations.flatMap:
      case decide: Decide => decide.query match
        case one: DecisionQuery.ChooseOne =>
          val options = one.options.filterNot(option => protects(option.ref))
          if options.isEmpty then Vector.empty
          else Vector(decide.copy(query = one.copy(options = options)))
        case _ => Vector(decide)
      case other => Vector(other)
    if kept == operations then operations
    else note.map(said => Note(power, _ => Some(said))).toVector ++ kept
