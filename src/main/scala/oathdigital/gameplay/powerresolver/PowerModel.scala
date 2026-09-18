package oathdigital.gameplay.powerresolver

import oathdigital.model._

final case class PowerInspection(
    applicable: Boolean,
    eligiblePlayer: Option[PlayerId] = None,
    decisionPlayer: Option[PlayerId] = None
)

/** Action and phase modules add final case classes extending this marker.
  * Their typed costs, targets, contributions, and effects remain owned there.
  */
trait PowerFacts extends Product with Serializable
case object NoFacts extends PowerFacts

final case class PowerContext(window: PowerWindow, source: RuleSourceRef,
    facts: PowerFacts)

trait PowerHandler extends Serializable {
  def window: PowerWindow
  def resolution: PowerResolution
  def inspect(context: PowerContext): PowerInspection
  def implemented: Boolean
}

trait Power extends Serializable {
  def id: PowerId
  def modifier: Option[MajorActionType]
  def handlers: Vector[PowerHandler]
}

object Power {
  def validate(power: Power): Unit = {
    require(power.handlers.nonEmpty, s"power ${power.id} must declare a handler")
    val windows = power.handlers.map(_.window)
    require(windows.distinct.size == windows.size,
      s"power ${power.id} must not repeat a window")
    power.modifier.foreach { action => require(windows.forall(
      _.associatedMajorAction.contains(action)),
      s"power ${power.id} modifier ${action.key} contradicts its windows") }
  }
}

final case class PowerInvocation(source: RuleSourceRef, powerId: PowerId,
    inspection: PowerInspection)

final case class PowerDiagnostic(source: RuleSourceRef, powerId: PowerId,
    window: PowerWindow, reason: String)

final case class PowerResolutionResult(
    offered: Vector[PowerInvocation],
    automatic: Vector[PowerInvocation],
    diagnostics: Vector[PowerDiagnostic]
)

sealed trait PowerResolverError extends Product with Serializable
object PowerResolverError {
  final case class UnknownAbility(source: RuleSourceRef, powerId: PowerId)
      extends PowerResolverError
}
