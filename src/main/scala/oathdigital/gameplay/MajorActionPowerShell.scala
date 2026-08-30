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
  *
  * @deprecated Production and test callers use PowerRuntime. This dead
  * compatibility implementation remains only until its wire-label types move
  * to a neutral source file.
  */
@deprecated("use PowerRuntime; retained only with current wire-label types", "pre-alpha")
object MajorActionPowerShell {
  val AuditedCatalogFingerprint =
    "7e333f6b4bdd033e2c1e76c3b4f8889c7d44cb5325f8d7da32ba514291b154e2"

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
  private val reviewedSyntheticHandlers = Set(
    "banner.peoples-favor.grand-council",
    "banner.darkest-secret.festival",
    "foundation.altered")

  def requireAudited(catalog: ExecutableCatalog): Either[OathViolation, Unit] =
    Either.cond(CatalogHandlerInventory.fingerprint(catalog) ==
      AuditedCatalogFingerprint, (), OathViolation.UnsupportedRuleCatalog(
      AuditedCatalogFingerprint, CatalogHandlerInventory.fingerprint(catalog)))

  def classify(handlerId: String, action: MajorActionKind)
      : RuleClassification = action match {
    case MajorActionKind.Travel => classifyTravel(handlerId)
    case MajorActionKind.Campaign => classifyCampaign(handlerId)
    case MajorActionKind.Muster | MajorActionKind.Trade =>
      if (economy(handlerId)) RuleClassification(action, RuleTiming.Start,
        RuleBehavior.OptionalModifier, implemented = false)
      else irrelevant(action)
    case MajorActionKind.Recover =>
      if (recover(handlerId)) RuleClassification(action, RuleTiming.Persistent,
        RuleBehavior.Mandatory, implemented = false) else irrelevant(action)
    case MajorActionKind.Rest =>
      if (rest(handlerId) || reviewedSyntheticHandlers(handlerId) ||
          handlerId == "foundation.altered") RuleClassification(action,
        RuleTiming.Trigger, RuleBehavior.Triggered, implemented = false)
      else irrelevant(action)
    case MajorActionKind.WhenPlayed =>
      if (whenPlayed(handlerId)) RuleClassification(action, RuleTiming.Trigger,
        RuleBehavior.Triggered, implemented = false) else irrelevant(action)
    case MajorActionKind.Wake | MajorActionKind.ActionBoundary =>
      if (reviewedSyntheticHandlers(handlerId))
        RuleClassification(action, RuleTiming.Trigger, RuleBehavior.Triggered,
          implemented = false) else irrelevant(action)
    case MajorActionKind.Search | MajorActionKind.Forge => irrelevant(action)
  }

  private def classifyTravel(handlerId: String) = {
    val inherent = Set("site.broken-peaks.mountain", "site.desolate-shore.coast",
      "site.fair-isle.coast", "site.fair-isle.island", "site.green-shore.coast",
      "site.headwaters.mountain", "site.hidden-place.mountain",
      "site.mines.mountain", "site.narrow-pass.pass", "site.rocky-coast.coast",
      "site.sunken-isles.coast", "site.sunken-isles.island",
      "site.tidal-marshes.coast")
    if (inherent(handlerId))
      RuleClassification(MajorActionKind.Travel, RuleTiming.Inherent, RuleBehavior.Inherent,
        implemented = true)
    else irrelevant(MajorActionKind.Travel)
  }

  private def classifyCampaign(handlerId: String) =
    if (Set("denizen.outriders",
        "relic.brass-army.campaign", "denizen.watchdog")(handlerId))
      RuleClassification(MajorActionKind.Campaign, RuleTiming.BattlePlan,
        RuleBehavior.BattlePlan,
        implemented = true)
    else if (handlerId == "relic.bag-of-siegeworks")
      RuleClassification(MajorActionKind.Campaign, RuleTiming.BattlePlan,
        RuleBehavior.BattlePlan,
        implemented = false)
    else if (handlerId == "denizen.vow-of-peace")
      RuleClassification(MajorActionKind.Campaign, RuleTiming.Persistent,
        RuleBehavior.Mandatory,
        implemented = true)
    else irrelevant(MajorActionKind.Campaign)

  private def irrelevant(action: MajorActionKind) = RuleClassification(action,
    RuleTiming.Start, RuleBehavior.Irrelevant, implemented = false)

  private def checkedClassification(catalog: ExecutableCatalog, handler: String,
      action: MajorActionKind): Either[OathViolation, RuleClassification] =
    if (CatalogHandlerInventory.handlerIds(catalog).contains(handler) ||
        reviewedSyntheticHandlers(handler))
      Right(classify(handler, action))
    else Left(OathViolation.UnsupportedRuleCatalog(AuditedCatalogFingerprint,
      s"unclassified-handler-action:${action.key}:$handler"))

  private[gameplay] def classifyAudited(catalog: ExecutableCatalog,
      handler: String, action: MajorActionKind) = for {
    _ <- requireAudited(catalog)
    classification <- checkedClassification(catalog, handler, action)
  } yield classification

  def ignored(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      action: MajorActionKind): Either[OathViolation, Vector[IgnoredRuleDiagnostic]] =
    requireAudited(catalog).map { _ =>
      diagnostics(catalog, accessible(RuleSourceIndex.enumerate(catalog, ready), ready,
        actor, action), action)
    }

  def ignoredAtSource(catalog: ExecutableCatalog, ready: ReadyGame,
      action: MajorActionKind, source: RuleSourceRef)
      : Either[OathViolation, Vector[IgnoredRuleDiagnostic]] =
    requireAudited(catalog).map(_ => diagnostics(catalog,
      RuleSourceIndex.enumerate(catalog, ready).filter(_.source == source), action))

  def options(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      action: MajorActionKind): Either[OathViolation, Vector[OrderedRuleInvocation]] =
    requireAudited(catalog).map { _ =>
      accessible(RuleSourceIndex.enumerate(catalog, ready), ready, actor, action).flatMap { item =>
        item.handlerIds.flatMap { handler =>
          val c = checkedClassification(catalog, handler, action).toOption.get
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

  private def diagnostics(catalog: ExecutableCatalog,
      items: Vector[IndexedRuleSource], action: MajorActionKind) =
    items.flatMap { item => item.handlerIds.flatMap { handler =>
      val c = checkedClassification(catalog, handler, action).toOption.get
      Option.when(!c.implemented && (c.behavior == RuleBehavior.Mandatory ||
          c.behavior == RuleBehavior.Triggered))(IgnoredRuleDiagnostic(
        item.source, handler, action, c.timing,
        "reviewed-unimplemented-pre-alpha-fallback"))
    }}.sortBy(d => (d.source.stableKey, d.handlerId))

}
