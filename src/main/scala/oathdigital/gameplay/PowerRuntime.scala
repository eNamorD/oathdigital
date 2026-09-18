package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model.PlayerId
import oathdigital.model.{IgnoredRuleDiagnostic, MajorActionKind, OathViolation, OrderedRuleInvocation, ReadyGame, RuleSourceRef, RuleTiming}

/** Compatibility projection from precise power windows into the current
  * command/event protocol. Matching and applicability are owned exclusively by
  * PowerResolver; these methods only translate established public shapes.
  */
object PowerRuntime {
  def requireAudited(catalog: ExecutableCatalog): Either[OathViolation, Unit] =
    ReviewedPowerCatalog.requireAudited(catalog)

  def options(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      action: MajorActionKind): Either[OathViolation, Vector[OrderedRuleInvocation]] =
    resolve(catalog, ready, actor, window(action)).map(_.offered.map(invocation =>
      OrderedRuleInvocation(invocation.source, invocation.powerId.value)))

  def ignored(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      action: MajorActionKind): Either[OathViolation, Vector[IgnoredRuleDiagnostic]] =
    resolve(catalog, ready, actor, window(action)).map(_.diagnostics.map(value =>
      diagnostic(action, value)))

  def ignoredAtSource(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, action: MajorActionKind, source: RuleSourceRef)
      : Either[OathViolation, Vector[IgnoredRuleDiagnostic]] =
    resolve(catalog, ready, actor, window(action),
      Some(source)).map(_.diagnostics.map(value => diagnostic(action, value)))

  private def resolve(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, powerWindow: PowerWindow,
      only: Option[RuleSourceRef] = None)
      : Either[OathViolation, PowerResolutionResult] = for {
    resolver <- ReviewedPowerCatalog.resolver(catalog)
    sources = ReviewedPowerCatalog.sources(catalog, ready)
      .filter(value => only.forall(_ == value._1))
    result <- resolver.resolve(powerWindow, sources,
      ReviewedPowerCatalog.facts(catalog, ready, actor)).left.map {
      case PowerResolverError.UnknownAbility(source, id) =>
        OathViolation.UnsupportedRuleCatalog(
          ReviewedPowerCatalog.AuditedCatalogFingerprint,
          s"unclassified-handler:${source.stableKey}:${id.value}")
    }
  } yield result

  private def window(action: MajorActionKind): PowerWindow = action match {
    case MajorActionKind.Search => PowerWindow.SearchModifierSelection
    case MajorActionKind.Travel => PowerWindow.TravelModifierSelection
    case MajorActionKind.Campaign => PowerWindow.CampaignModifierSelection
    case MajorActionKind.Muster => PowerWindow.MusterModifierSelection
    case MajorActionKind.Trade => PowerWindow.TradeModifierSelection
    case MajorActionKind.Forge => PowerWindow.ForgeModifierSelection
    case MajorActionKind.Recover => PowerWindow.RecoverBeforeFirstRoll
    case MajorActionKind.Challenge => PowerWindow.ChallengeModifierSelection
    case MajorActionKind.Rest => PowerWindow.RestStart
    case MajorActionKind.WhenPlayed => PowerWindow.ActionCardPlayed
    case MajorActionKind.Wake => PowerWindow.WakeBoundary
    case MajorActionKind.ActionBoundary => PowerWindow.ActionAfterMajorAction
  }

  private def diagnostic(action: MajorActionKind, value: PowerDiagnostic) =
    IgnoredRuleDiagnostic(value.source, value.powerId.value, action,
      timing(value.window), value.reason)

  private def timing(window: PowerWindow): RuleTiming = window match {
    case PowerWindow.RecoverBeforeFirstRoll => RuleTiming.Persistent
    case PowerWindow.CampaignAttackerBattlePlans |
        PowerWindow.CampaignDefenderBattlePlans => RuleTiming.BattlePlan
    case PowerWindow.TravelCost => RuleTiming.Inherent
    case PowerWindow.RestStart | PowerWindow.ActionCardPlayed |
        PowerWindow.WakeBoundary | PowerWindow.ActionAfterMajorAction =>
      RuleTiming.Trigger
    case _ => RuleTiming.Start
  }
}
