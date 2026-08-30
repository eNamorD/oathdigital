package oathdigital.gameplay

/** Stable action labels used by modifier requests and durable fallback events.
  * Resolver relevance is determined by PowerWindow, never by these labels.
  */
sealed trait MajorActionKind extends Product with Serializable { def key: String }
object MajorActionKind {
  case object Travel extends MajorActionKind { val key = "travel" }
  case object Search extends MajorActionKind { val key = "search" }
  case object Campaign extends MajorActionKind { val key = "campaign" }
  case object Muster extends MajorActionKind { val key = "muster" }
  case object Trade extends MajorActionKind { val key = "trade" }
  case object Forge extends MajorActionKind { val key = "forge" }
  case object Recover extends MajorActionKind { val key = "recover" }
  case object Challenge extends MajorActionKind { val key = "challenge" }
  case object Wake extends MajorActionKind { val key = "wake" }
  case object Rest extends MajorActionKind { val key = "rest" }
  case object WhenPlayed extends MajorActionKind { val key = "when-played" }
  case object ActionBoundary extends MajorActionKind { val key = "action-boundary" }

  val values = Vector(Travel, Search, Campaign, Muster, Trade, Forge, Recover,
    Challenge, Wake, Rest, WhenPlayed, ActionBoundary)
  def fromKey(key: String): Option[MajorActionKind] = values.find(_.key == key)
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
    action: MajorActionKind, timing: RuleTiming, reason: String)
