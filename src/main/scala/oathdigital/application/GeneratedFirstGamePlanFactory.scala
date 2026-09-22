package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model.FirstGameSetupPlan

/**
 * Production first-game plan derivation: draws a random Chronicle through
 * `FirstGameChronicleGenerator` and bridges it into a `FirstGameSetupPlan`
 * via `ChronicleFirstGamePlan` (2026-09-21 Chronicle design, slice 1).
 * Trusted-game provisioning, development game creation, and authenticated
 * bootstrap all use this.
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
      : Either[BootstrapPlanFailure, FirstGameSetupPlan] =
    for {
      registry <- ReviewedPowerCatalog.registry(catalog)
        .left.map(violation => BootstrapPlanFailure(violation.toString))
      chronicle <- FirstGameChronicleGenerator.generate(catalog, registry, random, policy)
        .left.map(failure => BootstrapPlanFailure(failure.toString))
      plan <- ChronicleFirstGamePlan.build(catalog, chronicle, shuffledSeating(config))
        .left.map(failure => BootstrapPlanFailure(failure.toString))
    } yield plan

  private def shuffledSeating(config: FirstGameBootstrapConfig)
      : FirstGameBootstrapConfig = {
    val seats = random.shuffle(config.participants)
    seats.headOption.fold(config)(first =>
      FirstGameBootstrapConfig(seats, first.playerId))
  }
}
