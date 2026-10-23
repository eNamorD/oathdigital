package oathdigital.model

/** Stable action labels. Resolver relevance is determined by PowerWindow,
  * never by these labels.
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
