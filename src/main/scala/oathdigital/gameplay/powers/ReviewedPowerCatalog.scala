package oathdigital.gameplay.powers

import oathdigital.catalog.{CatalogHandlerInventory, ExecutableCatalog}
import oathdigital.gameplay._
import oathdigital.gameplay.powerresolver._
import oathdigital.model.{PlayerId, PowerId}

object ReviewedPowerCatalog {
  val AuditedCatalogFingerprint: String =
    "7e333f6b4bdd033e2c1e76c3b4f8889c7d44cb5325f8d7da32ba514291b154e2"

  private val syntheticIds = Set(
    PowerId("banner.peoples-favor.grand-council"),
    PowerId("banner.darkest-secret.festival"),
    PowerId("foundation.altered"))

  val registrations: Vector[RegisteredPower] =
    ActionPowers.registrations ++ WakePowers.registrations ++
      SearchPowers.registrations ++ TravelPowers.registrations ++
      CampaignPowers.registrations ++ MusterPowers.registrations ++
      TradePowers.registrations ++ ForgePowers.registrations ++
      RecoverPowers.registrations ++ RestPowers.registrations ++
      NegotiationPowers.registrations

  def requireAudited(catalog: ExecutableCatalog): Either[OathViolation, Unit] = {
    val actual = CatalogHandlerInventory.fingerprint(catalog)
    Either.cond(actual == AuditedCatalogFingerprint, (),
      OathViolation.UnsupportedRuleCatalog(AuditedCatalogFingerprint, actual))
  }

  def resolver(catalog: ExecutableCatalog): Either[OathViolation, PowerResolver] =
    requireAudited(catalog).map { _ =>
      val audited = CatalogHandlerInventory.handlerIds(catalog)
        .map(PowerId).toSet ++ syntheticIds
      new PowerResolver(PowerRegistry.withAudited(audited,
        registrations: _*))
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
