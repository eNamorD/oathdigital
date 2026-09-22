package oathdigital.gameplay.powers

import oathdigital.catalog.{CatalogHandlerInventory, ExecutableCatalog}
import oathdigital.gameplay._
import oathdigital.gameplay.powerresolver._
import oathdigital.model.{OathViolation, PlayerId, PowerId, ReadyGame, RuleSourceRef}

object ReviewedPowerCatalog {
  val AuditedCatalogFingerprint: String =
    "7e333f6b4bdd033e2c1e76c3b4f8889c7d44cb5325f8d7da32ba514291b154e2"

  private val syntheticIds = Set(
    PowerId("banner.peoples-favor.grand-council"),
    PowerId("banner.peoples-favor.mob"),
    PowerId("banner.darkest-secret.festival"),
    PowerId("banner.darkest-secret.wandering-flame.move"),
    PowerId("banner.darkest-secret.wandering-flame.place"),
    PowerId("foundation.altered"))

  val powers: Vector[Power] =
    ActionPowers.powers ++ WakePowers.powers ++
      TravelPowers.powers ++ CampaignPowers.powers ++ MusterPowers.powers ++
      TradePowers.powers ++ ForgePowers.powers ++ RecoverPowers.powers ++
      RestPowers.powers ++ NegotiationPowers.powers

  def requireAudited(catalog: ExecutableCatalog): Either[OathViolation, Unit] = {
    val actual = CatalogHandlerInventory.fingerprint(catalog)
    Either.cond(actual == AuditedCatalogFingerprint, (),
      OathViolation.UnsupportedRuleCatalog(AuditedCatalogFingerprint, actual))
  }

  def resolver(catalog: ExecutableCatalog): Either[OathViolation, PowerResolver] =
    requireAudited(catalog).map { _ =>
      val audited = CatalogHandlerInventory.handlerIds(catalog)
        .map(PowerId).toSet ++ syntheticIds
      new PowerResolver(PowerRegistry.withAudited(audited, effective(catalog): _*))
    }

  def registry(catalog: ExecutableCatalog): Either[OathViolation, PowerRegistry] =
    requireAudited(catalog).map { _ =>
      val audited = CatalogHandlerInventory.handlerIds(catalog).map(PowerId).toSet ++
        syntheticIds
      PowerRegistry.withAudited(audited, effective(catalog): _*)
    }

  /** `powers`, with every handler's `implemented` widened by
    * [[PowerImplementationStatus]]: a handler still declared unimplemented
    * here reports implemented once its power's id is covered by
    * `WalkerPowerCatalog` or `PhasePowerCatalog` for this catalog, so a
    * legacy stub nobody deleted after its power ported elsewhere stops
    * producing a stale "ignored rule" diagnostic.
    */
  private def effective(catalog: ExecutableCatalog): Vector[Power] = {
    val status = PowerImplementationStatus.implemented(catalog)
    powers.map { power =>
      new Power {
        def id: PowerId = power.id
        def modifier = power.modifier
        def handlers: Vector[PowerHandler] = power.handlers.map { handler =>
          new PowerHandler {
            def window = handler.window
            def resolution = handler.resolution
            def inspect(context: PowerContext) = handler.inspect(context)
            def implemented: Boolean = handler.implemented || status(power.id)
          }
        }
      }
    }
  }

  def facts(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId)
      : ReviewedPowerFacts = {
    val sources = RuleSourceIndex.enumerate(catalog, ready)
    ReviewedPowerFacts(catalog, ready, actor,
      sources.map(source => source.source -> source).toMap)
  }

  def sources(catalog: ExecutableCatalog, ready: ReadyGame)
      : Vector[(RuleSourceRef, Vector[PowerId])] =
    RuleSourceIndex.enumerate(catalog, ready).map(source =>
      source.source -> source.powerIds)
}
