package oathdigital.gameplay.powerresolver

import oathdigital.gameplay.RuleSourceRef
import oathdigital.model.PlayerId

final case class PowerId(value: String) {
  require(value.trim.nonEmpty, "power ID must not be blank")
}

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
  case object SearchActionEligibility extends PowerWindow { val key = "search.action-eligibility" }
  case object SearchModifierSelection extends PowerWindow { val key = "search.modifier-selection" }
  case object TravelActionEligibility extends PowerWindow { val key = "travel.action-eligibility" }
  case object TravelModifierSelection extends PowerWindow { val key = "travel.modifier-selection" }
  case object CampaignActionEligibility extends PowerWindow { val key = "campaign.action-eligibility" }
  case object CampaignModifierSelection extends PowerWindow { val key = "campaign.modifier-selection" }
  case object MusterActionEligibility extends PowerWindow { val key = "muster.action-eligibility" }
  case object MusterModifierSelection extends PowerWindow { val key = "muster.modifier-selection" }
  case object TradeActionEligibility extends PowerWindow { val key = "trade.action-eligibility" }
  case object TradeModifierSelection extends PowerWindow { val key = "trade.modifier-selection" }
  case object ForgeActionEligibility extends PowerWindow { val key = "forge.action-eligibility" }
  case object ForgeModifierSelection extends PowerWindow { val key = "forge.modifier-selection" }
  case object RecoverActionEligibility extends PowerWindow { val key = "recover.action-eligibility" }
  case object RecoverModifierSelection extends PowerWindow { val key = "recover.modifier-selection" }
  case object ChallengeActionEligibility extends PowerWindow { val key = "challenge.action-eligibility" }
  case object ChallengeModifierSelection extends PowerWindow { val key = "challenge.modifier-selection" }
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
    id: PowerId,
    modifier: Option[MajorActionType],
    windows: Vector[PowerWindow],
    resolution: PowerResolution
) {
  /** Vector plus these invariants intentionally avoids adding a collection
    * dependency solely for NonEmptyVector during this foundation gate.
    */
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
    window.key.startsWith(prefix)
  }
}

final case class PowerInspection(
    applicable: Boolean,
    eligiblePlayer: Option[PlayerId] = None,
    decisionPlayer: Option[PlayerId] = None
)

/** Action and phase modules add final case classes extending this marker.
  * Their typed costs, targets, contributions, and effects remain owned there.
  */
trait PowerFacts extends Product with Serializable

final case class PowerContext(window: PowerWindow, source: RuleSourceRef,
    facts: PowerFacts)

trait PowerHandler {
  def inspect(context: PowerContext): PowerInspection
}

final case class RegisteredPower(
    definition: PowerDefinition,
    handler: Option[PowerHandler]
)

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
