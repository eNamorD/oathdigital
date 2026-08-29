package oathdigital.gameplay.powerresolver

import oathdigital.gameplay.RuleSourceRef
import oathdigital.model.PlayerId

sealed trait MajorActionType extends Product with Serializable { def key: String }
object MajorActionType {
  case object Search extends MajorActionType { val key = "search" }
  case object Travel extends MajorActionType { val key = "travel" }
  case object Campaign extends MajorActionType { val key = "campaign" }
  case object Muster extends MajorActionType { val key = "muster" }
  case object Trade extends MajorActionType { val key = "trade" }
  case object Forge extends MajorActionType { val key = "forge" }
  case object Recover extends MajorActionType { val key = "recover" }
  case object Challenge extends MajorActionType { val key = "challenge" }

  val values: Vector[MajorActionType] = Vector(
    Search, Travel, Campaign, Muster, Trade, Forge, Recover, Challenge)
}

/** A procedure point, not a persistence or relevance category. */
sealed trait PowerWindow extends Product with Serializable { def key: String }
object PowerWindow {
  case object ActionEligibility extends PowerWindow { val key = "action.eligibility" }
  case object ActionChosen extends PowerWindow { val key = "action.chosen" }
  case object WakeTakeWealth extends PowerWindow { val key = "wake.take-wealth" }
  case object SearchEligibility extends PowerWindow { val key = "search.eligibility" }
  case object SearchCost extends PowerWindow { val key = "search.cost" }
  case object SearchBeforeDraw extends PowerWindow { val key = "search.before-draw" }
  case object SearchPlayToSite extends PowerWindow { val key = "search.play-to-site" }
  case object SearchPlayFacedownAdviser extends PowerWindow {
    val key = "search.play-facedown-adviser"
  }
  case object TravelCost extends PowerWindow { val key = "travel.cost" }
  case object CampaignBeforeTargets extends PowerWindow {
    val key = "campaign.before-targets"
  }
  case object CampaignAttackerBattlePlans extends PowerWindow {
    val key = "campaign.attacker-battle-plans"
  }
  case object CampaignDefenderBattlePlans extends PowerWindow {
    val key = "campaign.defender-battle-plans"
  }
  case object CampaignAfterOutcome extends PowerWindow {
    val key = "campaign.after-outcome"
  }
  case object MusterCost extends PowerWindow { val key = "muster.cost" }
  case object TradeCost extends PowerWindow { val key = "trade.cost" }
  case object ForgeCost extends PowerWindow { val key = "forge.cost" }
  case object RecoverEligibility extends PowerWindow { val key = "recover.eligibility" }
  case object RecoverBeforeFirstRoll extends PowerWindow {
    val key = "recover.before-first-roll"
  }
  case object RecoverAfterRelic extends PowerWindow { val key = "recover.after-relic" }
  case object RestStart extends PowerWindow { val key = "rest.start" }
  case object RestReturnFavor extends PowerWindow { val key = "rest.return-favor" }
  case object RestReturnSecrets extends PowerWindow { val key = "rest.return-secrets" }
  case object RestEnd extends PowerWindow { val key = "rest.end" }
  case object NegotiationOffer extends PowerWindow { val key = "negotiation.offer" }
}

sealed trait PowerResolution extends Product with Serializable
object PowerResolution {
  case object PlayerSelected extends PowerResolution
  case object Automatic extends PowerResolution
}

final case class PowerDefinition(
    id: String,
    modifier: Option[MajorActionType],
    windows: Vector[PowerWindow],
    resolution: PowerResolution
) {
  require(id.trim.nonEmpty, "power ID must not be blank")
  require(windows.nonEmpty, s"power $id must declare at least one window")
  require(windows.distinct.size == windows.size,
    s"power $id must not repeat a window")
  modifier.foreach { action =>
    require(windows.forall(PowerDefinition.belongsTo(action, _)),
      s"power $id modifier ${action.key} contradicts its windows")
  }
}

object PowerDefinition {
  private def belongsTo(action: MajorActionType, window: PowerWindow): Boolean = {
    val prefix = action.key + "."
    window.key.startsWith(prefix) || window == PowerWindow.ActionEligibility ||
      window == PowerWindow.ActionChosen
  }
}

sealed trait PowerEffect extends Product with Serializable
object PowerEffect {
  final case class Typed(name: String, values: Map[String, String] = Map.empty)
      extends PowerEffect {
    require(name.trim.nonEmpty, "typed effect name must not be blank")
  }
}

final case class PowerInspection(
    applicable: Boolean,
    eligiblePlayer: Option[PlayerId] = None,
    decisionPlayer: Option[PlayerId] = None,
    cost: Vector[PowerEffect] = Vector.empty,
    targets: Vector[String] = Vector.empty,
    effects: Vector[PowerEffect] = Vector.empty
)

final case class PowerContext(window: PowerWindow, source: RuleSourceRef,
    facts: Any)

trait PowerHandler {
  def inspect(context: PowerContext): PowerInspection
}

final case class RegisteredPower(
    definition: PowerDefinition,
    handler: Option[PowerHandler]
)

final case class PowerInvocation(source: RuleSourceRef, powerId: String,
    inspection: PowerInspection)

final case class PowerDiagnostic(source: RuleSourceRef, powerId: String,
    window: PowerWindow, reason: String)

final case class PowerResolutionResult(
    offered: Vector[PowerInvocation],
    automatic: Vector[PowerInvocation],
    diagnostics: Vector[PowerDiagnostic]
)

sealed trait PowerResolverError extends Product with Serializable
object PowerResolverError {
  final case class UnknownAbility(source: RuleSourceRef, powerId: String)
      extends PowerResolverError
}
