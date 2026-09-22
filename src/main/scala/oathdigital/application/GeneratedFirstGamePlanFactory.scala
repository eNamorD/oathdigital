package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model.Chronicle

/**
 * Production first-game Chronicle derivation: draws a random Chronicle
 * through `FirstGameChronicleGenerator` (2026-09-21 Chronicle design,
 * slice 2). Trusted-game provisioning, development game creation, and
 * authenticated bootstrap all use this now that the dev-only loopback route
 * and its deterministic `DevelopmentFirstGamePlanFactory` are retired.
 *
 * The seating order is shuffled too, and the first seat becomes the first
 * player, so the requested `firstPlayer` is ignored. The shuffled order is
 * recorded in the plan, so replay stays RNG-free.
 */
final class GeneratedFirstGamePlanFactory(
    catalog: ExecutableCatalog,
    random: ChronicleRandomPort = ChronicleRandomPort.random,
    policy: ShufflePolicy = ShufflePolicy.implementedFirst
) extends FirstGamePlanFactory {
  override def build(config: FirstGameBootstrapConfig)
      : Either[BootstrapPlanFailure, FirstGamePlan] = {
    val resolvedConfig = shuffledSeating(config)
    for {
      registry <- ReviewedPowerCatalog.registry(catalog)
        .left.map(violation => BootstrapPlanFailure(violation.toString))
      chronicle <- FirstGameChronicleGenerator.generate(catalog, registry, random, policy)
        .left.map(failure => BootstrapPlanFailure(failure.toString))
    } yield FirstGamePlan(chronicle, resolvedConfig)
  }

  private def shuffledSeating(config: FirstGameBootstrapConfig)
      : FirstGameBootstrapConfig = {
    val seats = random.shuffle(config.participants)
    seats.headOption.fold(config)(first =>
      FirstGameBootstrapConfig(seats, first.playerId))
  }
}
