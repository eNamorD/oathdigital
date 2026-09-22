package oathdigital.application

import oathdigital.model._

final case class FirstGameBootstrapConfig(
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId
)

final case class BootstrapPlanFailure(message: String)

/** A generated Chronicle plus the config it was actually dealt against --
  * `GeneratedFirstGamePlanFactory` shuffles seating and picks a new first
  * player before generating, so the config a caller passed in is stale the
  * moment `build` returns; `resolvedConfig` is the one to deal `SetupOrders`
  * from (2026-09-21 Chronicle design, slice 2).
  */
final case class FirstGamePlan(
    chronicle: Chronicle,
    resolvedConfig: FirstGameBootstrapConfig
)

trait FirstGamePlanFactory {
  def build(
      config: FirstGameBootstrapConfig
  ): Either[BootstrapPlanFailure, FirstGamePlan]
}
