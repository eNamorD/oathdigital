package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay._
import oathdigital.gameplay.powerresolver.{PowerFacts, PowerHandler,
  PowerInspector, PowerResolution, PowerWindow}
import oathdigital.model._

/** Typed Rest procedure seam. Concrete powers own their decision payloads,
  * commands, events, validation, and effects; integration owns hook ordering.
  */
trait RestPowerHandler extends PowerHandler {
  def prepare(input: RestPowerPreparation)
      : Either[OathViolation, Option[RestPowerDecisionStarted]]
  def decline(input: RestPowerDeclinePreparation)
      : Either[OathViolation, RestPowerDecisionCompleted]
  def evolve(catalog: ExecutableCatalog, state: OathState,
      event: RestPowerEvent): Either[OathViolation, OathState]
}

trait LeagueTreatyPowerHandler extends RestPowerHandler {
  def resolve(input: LeagueTreatyResolutionPreparation)
      : Either[OathViolation, RestPowerDecisionCompleted]
}

object LeagueTreatyPowerHandler {
  /** Function bundle for League Treaty's typed Rest decision. Procedure
    * integration depends on this interface, while the power declares only its
    * focused callbacks.
    */
  def functional(windowValue: PowerWindow, inspectPower: PowerInspector)(
      preparePower: RestPowerPreparation =>
        Either[OathViolation, Option[RestPowerDecisionStarted]],
      resolvePower: LeagueTreatyResolutionPreparation =>
        Either[OathViolation, RestPowerDecisionCompleted],
      declinePower: RestPowerDeclinePreparation =>
        Either[OathViolation, RestPowerDecisionCompleted],
      evolvePower: (ExecutableCatalog, OathState, RestPowerEvent) =>
        Either[OathViolation, OathState])
      : LeagueTreatyPowerHandler = new LeagueTreatyPowerHandler {
    val window = windowValue
    val resolution = PowerResolution.PlayerSelected
    val implemented = true

    def inspect(context: powerresolver.PowerContext)
        : powerresolver.PowerInspection = inspectPower(context)
    def prepare(input: RestPowerPreparation) = preparePower(input)
    def resolve(input: LeagueTreatyResolutionPreparation) = resolvePower(input)
    def decline(input: RestPowerDeclinePreparation) = declinePower(input)
    def evolve(catalog: ExecutableCatalog, state: OathState,
        event: RestPowerEvent) = evolvePower(catalog, state, event)
  }
}

final case class RestPowerFacts(
    catalog: ExecutableCatalog,
    ready: ReadyGame,
    restActor: PlayerId,
    sources: Map[RuleSourceRef, IndexedRuleSource]
) extends PowerFacts

final case class RestPowerPreparation(
    catalog: ExecutableCatalog,
    ready: ReadyGame,
    restActor: PlayerId,
    invocation: RestPowerInvocationRef,
    remaining: Vector[RestPowerInvocationRef])

final case class RestPowerDeclinePreparation(
    catalog: ExecutableCatalog,
    ready: ReadyGame,
    actor: PlayerId,
    pending: PendingProcedure.RestPowerDecision)

final case class LeagueTreatyResolutionPreparation(
    catalog: ExecutableCatalog,
    ready: ReadyGame,
    actor: PlayerId,
    pending: PendingProcedure.RestPowerDecision,
    allocations: Vector[FavorAllocation],
    destinationBank: Suit)
