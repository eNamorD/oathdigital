package oathdigital.gameplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model.PlayerId
import oathdigital.model.{IgnoredRuleDiagnostic, ActionKind, OathViolation, OrderedRuleInvocation, PowerWindow, ReadyGame, RuleSourceRef, RuleTiming}

/** Compatibility projection from precise power windows into the current
  * command/event protocol. Matching and applicability are owned exclusively by
  * PowerResolver; these methods only translate established public shapes.
  */
object PowerRuntime {
  def requireAudited(catalog: ExecutableCatalog): Either[OathViolation, Unit] =
    ReviewedPowerCatalog.requireAudited(catalog)

  def options(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      action: ActionKind): Either[OathViolation, Vector[OrderedRuleInvocation]] =
    resolve(catalog, ready, actor, window(action)).map(_.offered.map(invocation =>
      OrderedRuleInvocation(invocation.source, invocation.powerId.value)))

  def ignored(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      action: ActionKind): Either[OathViolation, Vector[IgnoredRuleDiagnostic]] =
    resolve(catalog, ready, actor, window(action)).map(_.diagnostics.map(value =>
      diagnostic(action, value)))

  def ignoredAtSource(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, action: ActionKind, source: RuleSourceRef)
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

  private def window(action: ActionKind): PowerWindow = action match {
    case ActionKind.Search => PowerWindow.SearchModifierSelection
    case ActionKind.Travel => PowerWindow.TravelModifierSelection
    case ActionKind.Campaign => PowerWindow.CampaignModifierSelection
    case ActionKind.Muster => PowerWindow.MusterModifierSelection
    case ActionKind.Trade => PowerWindow.TradeModifierSelection
    case ActionKind.Forge => PowerWindow.ForgeModifierSelection
    case ActionKind.Recover => PowerWindow.RecoverBeforeFirstRoll
    case ActionKind.Challenge => PowerWindow.ChallengeModifierSelection
    case ActionKind.Rest => PowerWindow.RestStart
    case ActionKind.WhenPlayed => PowerWindow.ActionCardPlayed
    case ActionKind.Wake => PowerWindow.WakeBoundary
    case ActionKind.ActionBoundary => PowerWindow.ActionAfterMajorAction
  }

  private def diagnostic(action: ActionKind, value: PowerDiagnostic) =
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
