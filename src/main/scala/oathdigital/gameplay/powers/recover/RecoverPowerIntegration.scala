package oathdigital.gameplay.powers.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay._
import oathdigital.gameplay.actions.{RecoverModifierContribution,
  RecoverPowerHandler, RecoverPowerPreparation, RecoverRules}
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model.{PlayerId, PlayerState, PowerId, RelicId, SiteId}

/** Aggregate boundary between exact-window resolution and Recover-owned typed
  * executable handlers. It contains no catalog-ID switch.
  */
object RecoverPowerIntegration {
  def validatePotential(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, siteId: SiteId): Either[OathViolation, Unit] =
    RecoverRules.validate(catalog, ready, player, siteId).orElse(for {
      _ <- RecoverRules.validatePotential(catalog, ready, player, siteId)
      resolver <- ReviewedPowerCatalog.resolver(catalog)
      result <- resolver.resolve(PowerWindow.RecoverActionEligibility,
        ReviewedPowerCatalog.sources(catalog, ready),
        ReviewedPowerCatalog.facts(catalog, ready, player.player)).left.map {
        case PowerResolverError.UnknownAbility(source, id) =>
          OathViolation.UnsupportedRuleCatalog(
            ReviewedPowerCatalog.AuditedCatalogFingerprint,
            s"unclassified-handler:${source.stableKey}:${id.value}")
      }
      _ <- Either.cond(result.automatic.nonEmpty, (),
        OathViolation.RecoverUnavailable(
          "site has no facedown relic or usable Recover modifier"))
    } yield ())

  def prepare(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      ordered: Vector[OrderedRuleInvocation], drawRelic: () =>
        Either[OathViolation, RelicId])
      : Either[OathViolation, Option[RecoverModifierContribution]] = ordered match {
    case Vector() => Right(None)
    case Vector(invocation) => for {
      registry <- ReviewedPowerCatalog.registry(catalog)
      handler <- registry.handler(PowerId(invocation.handlerId),
        PowerWindow.RecoverBeforeFirstRoll).toRight(
        OathViolation.InvalidModifierInvocation(
          s"unknown Recover modifier ${invocation.handlerId}"))
      executable <- handler match {
        case value: RecoverPowerHandler => Right(value)
        case _ => Left(OathViolation.InvalidModifierInvocation(
          s"Recover modifier ${invocation.handlerId} has no executable handler"))
      }
      relic <- drawRelic()
      contribution <- executable.prepare(RecoverPowerPreparation(catalog,
        ready, actor, invocation.source, relic))
    } yield Some(contribution)
    case _ => Left(OathViolation.InvalidModifierInvocation(
      "Recover supports one modifier at a time"))
  }
}
