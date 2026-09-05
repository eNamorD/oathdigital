package oathdigital.gameplay

import oathdigital.model._

/** Stable identity for a runtime rule source. PowerUseRef remains the narrower,
  * wire-compatible identity for use-limited powers.
  */
sealed trait RuleSourceRef extends Product with Serializable {
  def stableKey: String
}
object RuleSourceRef {
  def parse(stableKey: String): Option[RuleSourceRef] = {
    def splitTyped(prefix: String, kind: String) = {
      val body = stableKey.stripPrefix(prefix)
      val marker = s":$kind:"
      val at = body.indexOf(marker)
      Option.when(stableKey.startsWith(prefix) && at >= 0)(
        body.take(at) -> body.drop(at + marker.length))
    }
    def splitLast(prefix: String) = {
      val body = stableKey.stripPrefix(prefix)
      val at = body.lastIndexOf(':')
      Option.when(stableKey.startsWith(prefix) && at >= 0)(
        body.take(at) -> body.drop(at + 1))
    }
    if (stableKey.startsWith("site-card:"))
      splitTyped("site-card:", "denizen").map { case (site, id) =>
        SiteCard(SiteId(site), DenizenId(id))
      }.orElse(splitTyped("site-card:", "vision").map { case (site, id) =>
        SiteCard(SiteId(site), VisionId(id))
      })
    else stableKey.split(":", 4).toVector match {
      case Vector("adviser", player, "denizen", id) =>
        Some(Adviser(PlayerId(player), DenizenId(id)))
      case Vector("adviser", player, "vision", id) =>
        Some(Adviser(PlayerId(player), VisionId(id)))
      case _ if stableKey.startsWith("site:") => Some(Site(SiteId(
        stableKey.stripPrefix("site:"))))
      case _ if stableKey.startsWith("relic:") => stableKey
        .stripPrefix("relic:").split(":", 2).toVector match {
          case Vector(player, id) => Some(Relic(PlayerId(player), RelicId(id)))
          case _ => None
        }
      case _ if stableKey.startsWith("site-relic:") =>
        splitTyped("site-relic:", "relic").map { case (site, id) =>
          SiteRelic(SiteId(site), RelicId(s"relic:$id"))
        }.orElse(splitLast("site-relic:").map { case (site, id) =>
          SiteRelic(SiteId(site), RelicId(id)) })
      case _ if stableKey.startsWith("edifice:") =>
        splitTyped("edifice:", "edifice").map { case (site, id) =>
          Edifice(SiteId(site), EdificeId(s"edifice:$id"))
        }.orElse(splitLast("edifice:").map { case (site, id) =>
          Edifice(SiteId(site), EdificeId(id)) })
      case _ if stableKey.startsWith("banner:") =>
        Some(Banner(stableKey.stripPrefix("banner:")))
      case Vector("foundation", number) => scala.util.Try(number.toInt).toOption
        .flatMap(n => FoundationNumber.all.find(_.value == n)).map(Foundation)
      case _ if stableKey.startsWith("legacy:") => stableKey
        .stripPrefix("legacy:").split(":", 2).toVector match {
          case Vector(lineage, id) => Some(Legacy(LineageId(lineage), LegacyId(id)))
          case _ => None
        }
      case _ if stableKey.startsWith("game:") =>
        Some(GameRule(stableKey.stripPrefix("game:")))
      case _ => None
    }
  }
  final case class Site(id: SiteId) extends RuleSourceRef {
    def stableKey: String = s"site:${id.value}"
  }
  final case class SiteCard(siteId: SiteId, id: CardId) extends RuleSourceRef {
    def stableKey: String = s"site-card:${siteId.value}:${id.kind}:${id.value}"
  }
  final case class Adviser(playerId: PlayerId, id: CardId) extends RuleSourceRef {
    def stableKey: String = s"adviser:${playerId.value}:${id.kind}:${id.value}"
  }
  final case class Relic(playerId: PlayerId, id: RelicId) extends RuleSourceRef {
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
  }
  final case class SiteRelic(siteId: SiteId, id: RelicId) extends RuleSourceRef {
    def stableKey: String = s"site-relic:${siteId.value}:${id.value}"
  }
  final case class Edifice(siteId: SiteId, id: EdificeId) extends RuleSourceRef {
    def stableKey: String = s"edifice:${siteId.value}:${id.value}"
  }
  final case class Banner(id: String) extends RuleSourceRef {
    def stableKey: String = s"banner:$id"
  }
  final case class Foundation(number: FoundationNumber) extends RuleSourceRef {
    def stableKey: String = s"foundation:${number.value}"
  }
  final case class Legacy(lineageId: LineageId, id: LegacyId)
      extends RuleSourceRef {
    def stableKey: String = s"legacy:${lineageId.value}:${id.value}"
  }
  final case class GameRule(id: String) extends RuleSourceRef {
    def stableKey: String = s"game:$id"
  }
}

sealed trait RuleQueryContext extends Product with Serializable
object RuleQueryContext {
  final case class Campaign(
      ready: ReadyGame,
      player: PlayerState,
      target: SiteId,
      window: CampaignTimingWindow
  ) extends RuleQueryContext

  final case class TakeWealth(
      ready: ReadyGame,
      player: PlayerState,
      siteId: SiteId,
      resource: WakeResource
  ) extends RuleQueryContext

  final case class Negotiation(ready: ReadyGame, participant: PlayerState,
      site: SiteId, participants: Vector[PlayerId]) extends RuleQueryContext
}

/** Printed Campaign order, kept explicit even where the bounded Conquest has
  * no executable choice at a window yet. Handler discovery and replay use the
  * same stable ordering rather than inferring timing from a card name.
  */
sealed trait CampaignTimingWindow extends Product with Serializable {
  def order: Int
}
object CampaignTimingWindow {
  case object TargetAndForceFormation extends CampaignTimingWindow { val order = 0 }
  case object AttackerBattlePlans extends CampaignTimingWindow { val order = 1 }
  case object AttackRollAndSkullLosses extends CampaignTimingWindow { val order = 2 }
  case object AttackerSacrifice extends CampaignTimingWindow { val order = 3 }
  case object DefenderBattlePlansAndRoll extends CampaignTimingWindow { val order = 4 }
  case object Outcome extends CampaignTimingWindow { val order = 5 }
  case object ConquestPlacement extends CampaignTimingWindow { val order = 6 }
  case object RaidResolution extends CampaignTimingWindow { val order = 7 }
  case object RaidPawnRelocation extends CampaignTimingWindow { val order = 8 }
  case object RemainingEndVictoryDefeatEffects extends CampaignTimingWindow { val order = 9 }

  val ordered: Vector[CampaignTimingWindow] = Vector(
    TargetAndForceFormation, AttackerBattlePlans, AttackRollAndSkullLosses,
    AttackerSacrifice, DefenderBattlePlansAndRoll, Outcome,
    ConquestPlacement, RaidResolution, RaidPawnRelocation,
    RemainingEndVictoryDefeatEffects)
}

final case class RuleActivation(
    source: RuleSourceRef,
    handlerId: String,
    priority: Int
)

sealed trait RuleOutcome extends Product with Serializable
object RuleOutcome {
  case object Allow extends RuleOutcome
  final case class Block(violation: OathViolation) extends RuleOutcome
  final case class UnsupportedRelevantRule(handlerId: String)
      extends RuleOutcome
}

final case class ResolvedRule(
    activation: RuleActivation,
    outcome: RuleOutcome
)

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
  * through TravelCost window powers (see powers/travel/TravelCostWindow.scala).
  */
object RuntimeRuleRegistry {
  val default: RuleRegistry = RuleRegistry()
}
