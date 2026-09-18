package oathdigital.gameplay

import oathdigital.model._

trait TypedRuleHandler {
  def resolve(
      activation: RuleActivation,
      context: RuleQueryContext
  ): RuleOutcome
}

/** Explicit registry for handlers activated by a caller. Activations resolve by
  * priority, then stable source identity, then handler ID. This total ordering is
  * the precedence contract used by command handling, projections, and replay.
  */
final class RuleRegistry private (
    handlers: Map[String, TypedRuleHandler]
) {
  def lookup(handlerId: String): Option[TypedRuleHandler] = handlers.get(handlerId)

  def resolve(
      activations: Vector[RuleActivation],
      context: RuleQueryContext
  ): Vector[ResolvedRule] =
    activations.sortBy(value =>
      (value.priority, value.source.stableKey, value.handlerId)).map { activation =>
      ResolvedRule(
        activation,
        lookup(activation.handlerId)
          .map(_.resolve(activation, context))
          .getOrElse(RuleOutcome.UnsupportedRelevantRule(activation.handlerId))
      )
    }
}

object RuleRegistry {
  def apply(entries: (String, TypedRuleHandler)*): RuleRegistry = {
    require(entries.map(_._1).distinct.size == entries.size,
      "rule handler IDs must be unique")
    new RuleRegistry(entries.toMap)
  }
}

/** Travel-only stub retained for Negotiation's explicit blocking boundary:
  * its relevant-handler activations are never registered, so resolving them
  * against this empty registry yields `UnsupportedRelevantRule` exactly as the
  * retired travel handler set did. The travel terrain path itself now runs
  * through `ContributingPower` transforms at the TravelCost window (see
  * powers/travel/TravelSitePowers.scala).
  */
object RuntimeRuleRegistry {
  val default: RuleRegistry = RuleRegistry()
}
