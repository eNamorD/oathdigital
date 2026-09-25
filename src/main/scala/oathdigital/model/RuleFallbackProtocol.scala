package oathdigital.model

/** Stable action labels used by modifier requests and durable fallback events.
  * Resolver relevance is determined by PowerWindow, never by these labels.
  */
enum ActionKind(val key: String):
  case Travel extends ActionKind("travel")
  case Search extends ActionKind("search")
  case Campaign extends ActionKind("campaign")
  case Muster extends ActionKind("muster")
  case Trade extends ActionKind("trade")
  case Forge extends ActionKind("forge")
  case Recover extends ActionKind("recover")
  case Challenge extends ActionKind("challenge")
  case Wake extends ActionKind("wake")
  case Rest extends ActionKind("rest")
  case WhenPlayed extends ActionKind("when-played")
  case ActionBoundary extends ActionKind("action-boundary")
  case Negotiation extends ActionKind("negotiation")
object ActionKind:
  val all: Vector[ActionKind] = Vector(Travel, Search, Campaign, Muster, Trade,
    Forge, Recover, Challenge, Wake, Rest, WhenPlayed, ActionBoundary, Negotiation)
  def fromKey(key: String): Option[ActionKind] = all.find(_.key == key)

enum RuleTiming(val key: String):
  case Start extends RuleTiming("start")
  case Persistent extends RuleTiming("persistent")
  case Trigger extends RuleTiming("trigger")
  case BattlePlan extends RuleTiming("battle-plan")
  case Inherent extends RuleTiming("inherent")

final case class OrderedRuleInvocation(source: RuleSourceRef, handlerId: String)
final case class IgnoredRuleDiagnostic(source: RuleSourceRef, handlerId: String,
    action: ActionKind, timing: RuleTiming, reason: String)
