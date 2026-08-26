package oathdigital.gameplay

import oathdigital.catalog.{CatalogHandlerInventory, ExecutableCatalog}
import oathdigital.model._

sealed trait MajorActionKind extends Product with Serializable { def key: String }
object MajorActionKind {
  case object Travel extends MajorActionKind { val key = "travel" }
  case object Search extends MajorActionKind { val key = "search" }
  case object Campaign extends MajorActionKind { val key = "campaign" }
  case object Muster extends MajorActionKind { val key = "muster" }
  case object Trade extends MajorActionKind { val key = "trade" }
  case object Forge extends MajorActionKind { val key = "forge" }
  case object Recover extends MajorActionKind { val key = "recover" }
  case object Wake extends MajorActionKind { val key = "wake" }
  case object Rest extends MajorActionKind { val key = "rest" }
  case object WhenPlayed extends MajorActionKind { val key = "when-played" }
  case object ActionBoundary extends MajorActionKind { val key = "action-boundary" }

  val values = Vector(Travel, Search, Campaign, Muster, Trade, Forge, Recover,
    Wake, Rest, WhenPlayed, ActionBoundary)
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

sealed trait RuleBehavior extends Product with Serializable
object RuleBehavior {
  case object OptionalModifier extends RuleBehavior
  case object Mandatory extends RuleBehavior
  case object Triggered extends RuleBehavior
  case object BattlePlan extends RuleBehavior
  case object Inherent extends RuleBehavior
  case object Irrelevant extends RuleBehavior
}

final case class RuleClassification(action: MajorActionKind, timing: RuleTiming,
    behavior: RuleBehavior, implemented: Boolean)
final case class OrderedRuleInvocation(source: RuleSourceRef, handlerId: String)
final case class IgnoredRuleDiagnostic(source: RuleSourceRef, handlerId: String,
    action: MajorActionKind, timing: RuleTiming, reason: String)

/** Reviewed pre-alpha policy. Catalog vocabulary is pinned independently of
  * runtime presence; a changed vocabulary never enters the fallback path.
  */
object MajorActionPowerShell {
  val AuditedCatalogFingerprint =
    "70b57be7a3a4751e81d5235e033fdb62d1773f1e90fa2354d1c275e3e9d12f97"

  private val economy = Set(
    "denizen.initiation-rite", "denizen.map-library", "denizen.animal-playmates",
    "denizen.the-old-oak", "denizen.birdsong", "denizen.small-friends",
    "denizen.vow-of-poverty", "denizen.vow-of-beastkin", "denizen.downtrodden",
    "denizen.rowdy-pub", "denizen.pressgangs", "denizen.curfew",
    "denizen.knights-errant", "denizen.golem-legions", "denizen.defame",
    "denizen.friendly-familiar", "denizen.old-songs", "denizen.village-idiot",
    "denizen.skilled-merchants", "denizen.moving-market", "denizen.mounted-library",
    "relic.cup-of-plenty", "relic.spiteful-mirror", "edifice.e26.intact",
    "legacy.beloved")
  private val rest = Set("denizen.vow-of-poverty", "denizen.naysayers",
    "denizen.silver-tongue", "denizen.insomnia", "denizen.vow-of-obedience")
  private val recover = Set("denizen.relic-worship", "edifice.e13.ruined",
    "edifice.e17.intact", "edifice.e17.ruined")
  private val whenPlayed = Set(
    "denizen.dazzle", "denizen.revelation", "denizen.threatening-roar",
    "denizen.animal-host", "denizen.a-small-favor", "denizen.key-to-the-city",
    "denizen.charlatan", "denizen.blackmail", "denizen.dissent",
    "denizen.false-prophet", "denizen.family-heirloom", "denizen.fabled-feast",
    "denizen.salad-days", "denizen.the-gathering", "denizen.faithful-friend",
    "denizen.great-herd", "denizen.pilgrimage", "denizen.twin-brother",
    "denizen.garrison", "denizen.royal-tax", "denizen.bewitch",
    "denizen.wizard-s-conclave", "denizen.long-lost-heir", "denizen.true-oath",
    "denizen.autumn-wind", "denizen.shifting-fog", "denizen.royal-ambitions",
    "denizen.riots", "denizen.bandit-chief", "denizen.reliquary-raid",
    "denizen.bandit-prince", "denizen.a-round-of-ale", "denizen.favored-son",
    "denizen.town-meeting", "denizen.ancient-pact", "denizen.search-party",
    "denizen.call-for-help")

  def requireAudited(catalog: ExecutableCatalog): Either[OathViolation, Unit] =
    Either.cond(CatalogHandlerInventory.fingerprint(catalog) ==
      AuditedCatalogFingerprint, (), OathViolation.UnsupportedRuleCatalog(
      AuditedCatalogFingerprint, CatalogHandlerInventory.fingerprint(catalog)))

  def classify(handlerId: String, action: MajorActionKind)
      : RuleClassification = {
    val inherent = Set("site.broken-peaks.mountain", "site.desolate-shore.coast",
      "site.fair-isle.coast", "site.fair-isle.island", "site.green-shore.coast",
      "site.headwaters.mountain", "site.hidden-place.mountain",
      "site.mines.mountain", "site.narrow-pass.pass", "site.rocky-coast.coast",
      "site.sunken-isles.coast", "site.sunken-isles.island",
      "site.tidal-marshes.coast")
    if (action == MajorActionKind.Travel && inherent(handlerId))
      RuleClassification(action, RuleTiming.Inherent, RuleBehavior.Inherent,
        implemented = true)
    else if (action == MajorActionKind.Campaign && Set("denizen.outriders",
        "relic.brass-army", "denizen.watchdog")(handlerId))
      RuleClassification(action, RuleTiming.BattlePlan, RuleBehavior.BattlePlan,
        implemented = true)
    else if (action == MajorActionKind.Campaign &&
        handlerId == "relic.bag-of-siegeworks")
      RuleClassification(action, RuleTiming.BattlePlan, RuleBehavior.BattlePlan,
        implemented = false)
    else if (action == MajorActionKind.Campaign &&
        handlerId == "denizen.vow-of-peace")
      RuleClassification(action, RuleTiming.Persistent, RuleBehavior.Mandatory,
        implemented = true)
    else if ((action == MajorActionKind.Muster || action == MajorActionKind.Trade) &&
        economy(handlerId))
      RuleClassification(action, RuleTiming.Start, RuleBehavior.OptionalModifier,
        implemented = false)
    else if (action == MajorActionKind.Rest && (rest(handlerId) ||
        handlerId.startsWith("banner.") || handlerId == "foundation.altered"))
      RuleClassification(action, RuleTiming.Trigger, RuleBehavior.Triggered,
        implemented = false)
    else if (action == MajorActionKind.Recover && recover(handlerId))
      RuleClassification(action, RuleTiming.Persistent, RuleBehavior.Mandatory,
        implemented = false)
    else if (action == MajorActionKind.WhenPlayed && whenPlayed(handlerId))
      RuleClassification(action, RuleTiming.Trigger, RuleBehavior.Triggered,
        implemented = false)
    else if ((action == MajorActionKind.Wake ||
        action == MajorActionKind.ActionBoundary) &&
        (handlerId.startsWith("banner.") || handlerId == "foundation.altered"))
      RuleClassification(action, RuleTiming.Trigger, RuleBehavior.Triggered,
        implemented = false)
    else RuleClassification(action, RuleTiming.Start, RuleBehavior.Irrelevant,
      implemented = false)
  }

  def ignored(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      action: MajorActionKind): Either[OathViolation, Vector[IgnoredRuleDiagnostic]] =
    requireAudited(catalog).map { _ =>
      accessible(RuleSourceIndex.enumerate(catalog, ready), ready, actor, action).flatMap { item =>
        item.handlerIds.flatMap { handler =>
          val c = classify(handler, action)
          Option.when(!c.implemented && (c.behavior == RuleBehavior.Mandatory ||
              c.behavior == RuleBehavior.Triggered))(IgnoredRuleDiagnostic(
            item.source, handler, action, c.timing,
            "reviewed-unimplemented-pre-alpha-fallback"))
        }
      }.sortBy(d => (d.source.stableKey, d.handlerId))
    }

  def options(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      action: MajorActionKind): Either[OathViolation, Vector[OrderedRuleInvocation]] =
    requireAudited(catalog).map { _ =>
      accessible(RuleSourceIndex.enumerate(catalog, ready), ready, actor, action).flatMap { item =>
        item.handlerIds.flatMap { handler =>
          val c = classify(handler, action)
          Option.when(c.implemented && c.behavior == RuleBehavior.OptionalModifier)(
            OrderedRuleInvocation(item.source, handler))
        }
      }.sortBy(i => (i.source.stableKey, i.handlerId))
    }

  private def accessible(items: Vector[IndexedRuleSource], ready: ReadyGame,
      actor: PlayerId, action: MajorActionKind): Vector[IndexedRuleSource] = {
    val player = ready.game.current.players.find(_.player == actor)
    val pawn = player.flatMap(_.pawnSite)
    items.filter { item => item.source match {
      case RuleSourceRef.Site(id) => pawn.contains(id)
      case RuleSourceRef.SiteCard(id, _) => pawn.contains(id) && item.face == RuleSourceFace.FaceUp
      case RuleSourceRef.Edifice(id, _) => pawn.contains(id) && item.face == RuleSourceFace.Intact
      case RuleSourceRef.Adviser(owner, _) => owner == actor &&
        (item.face == RuleSourceFace.FaceUp || (action == MajorActionKind.WhenPlayed &&
          item.face == RuleSourceFace.FaceDown))
      case RuleSourceRef.Relic(owner, _) => owner == actor && item.face == RuleSourceFace.FaceUp
      case RuleSourceRef.Banner(_) => true
      case RuleSourceRef.Foundation(_) => true
      case RuleSourceRef.Legacy(lineage, _) => player.exists(_.lineage == lineage) && item.face == RuleSourceFace.Active
      case _ => false
    }}
  }

}
