package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.ReviewedPowerCatalog
import oathdigital.model.FirstGameSetupPlan

/**
 * Production first-game plan derivation: draws a random Chronicle through
 * `FirstGameChronicleGenerator` and bridges it into a `FirstGameSetupPlan`
 * via `ChronicleFirstGamePlan` (2026-09-21 Chronicle design, slice 1).
 * Trusted-game provisioning and authenticated bootstrap use this; the
 * dev-only loopback routes keep the deterministic
 * `DevelopmentFirstGamePlanFactory`.
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
      plan <- ChronicleFirstGamePlan.build(catalog, chronicle, config)
        .left.map(failure => BootstrapPlanFailure(failure.toString))
    } yield plan
}
