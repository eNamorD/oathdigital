package oathdigital.gameplay.phases

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathState, OathTransition, OathViolation,
  RestPowerEvent}
import oathdigital.gameplay.powers.rest.RestPowerIntegration
import oathdigital.model._

sealed trait RestCommand extends Product with Serializable
object RestCommand {
  final case class ResolvePower(playerId: PlayerId, decision: DecisionId,
      allocations: Vector[FavorAllocation], destinationBank: Suit)
      extends RestCommand
  final case class DeclinePower(playerId: PlayerId, decision: DecisionId)
      extends RestCommand
}

/** Legacy League Treaty resolution only. Nothing starts a legacy League
  * Treaty decision since Rest commands moved onto the walker; Task 8 deletes
  * this file.
  */
object Rest {
  def handle(catalog: ExecutableCatalog, state: OathState, command: RestCommand)
      : Either[OathViolation, OathTransition] = command match {
    case RestCommand.ResolvePower(playerId, decision, allocations, bank) =>
      RestPowerIntegration.resolveLeagueTreaty(catalog, state, playerId,
        decision, allocations, bank)
    case RestCommand.DeclinePower(playerId, decision) =>
      RestPowerIntegration.decline(catalog, state, playerId, decision)
  }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: RestPowerEvent)
      : Either[OathViolation, OathState] =
    RestPowerIntegration.evolve(catalog, state, event)
}
