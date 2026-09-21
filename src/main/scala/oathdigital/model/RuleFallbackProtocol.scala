package oathdigital.model

/** Stable action labels used by modifier requests and durable fallback events.
  * Resolver relevance is determined by PowerWindow, never by these labels.
  */
sealed trait ActionKind extends Product with Serializable { def key: String }
object ActionKind {
  case object Travel extends ActionKind { val key = "travel" }
  case object Search extends ActionKind { val key = "search" }
  case object Campaign extends ActionKind { val key = "campaign" }
  case object Muster extends ActionKind { val key = "muster" }
  case object Trade extends ActionKind { val key = "trade" }
  case object Forge extends ActionKind { val key = "forge" }
  case object Recover extends ActionKind { val key = "recover" }
  case object Challenge extends ActionKind { val key = "challenge" }
  case object Wake extends ActionKind { val key = "wake" }
  case object Rest extends ActionKind { val key = "rest" }
  case object WhenPlayed extends ActionKind { val key = "when-played" }
  case object ActionBoundary extends ActionKind { val key = "action-boundary" }
  case object Negotiation extends ActionKind { val key = "negotiation" }

  val values = Vector(Travel, Search, Campaign, Muster, Trade, Forge, Recover,
    Challenge, Wake, Rest, WhenPlayed, ActionBoundary, Negotiation)
  def fromKey(key: String): Option[ActionKind] = values.find(_.key == key)
}

sealed trait RuleTiming extends Product with Serializable { def key: String }
object RuleTiming {
  case object Start extends RuleTiming { val key = "start" }
  case object Persistent extends RuleTiming { val key = "persistent" }
  case object Trigger extends RuleTiming { val key = "trigger" }
  case object BattlePlan extends RuleTiming { val key = "battle-plan" }
  case object Inherent extends RuleTiming { val key = "inherent" }
}

final case class OrderedRuleInvocation(source: RuleSourceRef, handlerId: String)
final case class IgnoredRuleDiagnostic(source: RuleSourceRef, handlerId: String,
    action: ActionKind, timing: RuleTiming, reason: String)
