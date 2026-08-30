package oathdigital.gameplay.powerresolver

import oathdigital.gameplay.RuleSourceRef
import oathdigital.model.{PlayerId, PowerId}

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

/** A procedure point, not a persistence or relevance category. The stable key
  * is presentation/serialization data; semantic validation uses the typed
  * major-action association.
  */
sealed trait PowerWindow extends Product with Serializable {
  def key: String
  def associatedMajorAction: Option[MajorActionType]
}
object PowerWindow {
  sealed trait SearchWindow extends PowerWindow { final val associatedMajorAction = Some(MajorActionType.Search) }
  sealed trait TravelWindow extends PowerWindow { final val associatedMajorAction = Some(MajorActionType.Travel) }
  sealed trait CampaignWindow extends PowerWindow { final val associatedMajorAction = Some(MajorActionType.Campaign) }
  sealed trait MusterWindow extends PowerWindow { final val associatedMajorAction = Some(MajorActionType.Muster) }
  sealed trait TradeWindow extends PowerWindow { final val associatedMajorAction = Some(MajorActionType.Trade) }
  sealed trait ForgeWindow extends PowerWindow { final val associatedMajorAction = Some(MajorActionType.Forge) }
  sealed trait RecoverWindow extends PowerWindow { final val associatedMajorAction = Some(MajorActionType.Recover) }
  sealed trait ChallengeWindow extends PowerWindow { final val associatedMajorAction = Some(MajorActionType.Challenge) }
  sealed trait OtherWindow extends PowerWindow { final val associatedMajorAction = None }

  case object SearchActionEligibility extends SearchWindow { val key = "search.action-eligibility" }
  case object SearchModifierSelection extends SearchWindow { val key = "search.modifier-selection" }
  case object TravelActionEligibility extends TravelWindow { val key = "travel.action-eligibility" }
  case object TravelModifierSelection extends TravelWindow { val key = "travel.modifier-selection" }
  case object CampaignActionEligibility extends CampaignWindow { val key = "campaign.action-eligibility" }
  case object CampaignModifierSelection extends CampaignWindow { val key = "campaign.modifier-selection" }
  case object MusterActionEligibility extends MusterWindow { val key = "muster.action-eligibility" }
  case object MusterModifierSelection extends MusterWindow { val key = "muster.modifier-selection" }
  case object TradeActionEligibility extends TradeWindow { val key = "trade.action-eligibility" }
  case object TradeModifierSelection extends TradeWindow { val key = "trade.modifier-selection" }
  case object ForgeActionEligibility extends ForgeWindow { val key = "forge.action-eligibility" }
  case object ForgeModifierSelection extends ForgeWindow { val key = "forge.modifier-selection" }
  case object RecoverActionEligibility extends RecoverWindow { val key = "recover.action-eligibility" }
  case object RecoverModifierSelection extends RecoverWindow { val key = "recover.modifier-selection" }
  case object ChallengeActionEligibility extends ChallengeWindow { val key = "challenge.action-eligibility" }
  case object ChallengeModifierSelection extends ChallengeWindow { val key = "challenge.modifier-selection" }
  case object WakeTakeWealth extends OtherWindow { val key = "wake.take-wealth" }
  case object WakeBoundary extends OtherWindow { val key = "wake.boundary" }
  case object ActionCardPlayed extends OtherWindow { val key = "action.card-played" }
  case object ActionAfterMajorAction extends OtherWindow {
    val key = "action.after-major-action"
  }
  case object SearchEligibility extends SearchWindow { val key = "search.eligibility" }
  case object SearchCost extends SearchWindow { val key = "search.cost" }
  case object SearchBeforeDraw extends SearchWindow { val key = "search.before-draw" }
  case object SearchPlayToSite extends SearchWindow { val key = "search.play-to-site" }
  case object SearchPlayFacedownAdviser extends SearchWindow {
    val key = "search.play-facedown-adviser"
  }
  case object TravelCost extends TravelWindow { val key = "travel.cost" }
  case object CampaignBeforeTargets extends CampaignWindow {
    val key = "campaign.before-targets"
  }
  case object CampaignAttackerBattlePlans extends CampaignWindow {
    val key = "campaign.attacker-battle-plans"
  }
  case object CampaignDefenderBattlePlans extends CampaignWindow {
    val key = "campaign.defender-battle-plans"
  }
  case object CampaignAfterOutcome extends CampaignWindow {
    val key = "campaign.after-outcome"
  }
  case object MusterCost extends MusterWindow { val key = "muster.cost" }
  case object TradeCost extends TradeWindow { val key = "trade.cost" }
  case object ForgeCost extends ForgeWindow { val key = "forge.cost" }
  case object RecoverEligibility extends RecoverWindow { val key = "recover.eligibility" }
  case object RecoverBeforeFirstRoll extends RecoverWindow {
    val key = "recover.before-first-roll"
  }
  case object RecoverAfterRelic extends RecoverWindow { val key = "recover.after-relic" }
  case object RestStart extends OtherWindow { val key = "rest.start" }
  case object RestReturnFavor extends OtherWindow { val key = "rest.return-favor" }
  case object RestReturnSecrets extends OtherWindow { val key = "rest.return-secrets" }
  case object RestEnd extends OtherWindow { val key = "rest.end" }
  case object NegotiationOffer extends OtherWindow { val key = "negotiation.offer" }
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
  private def belongsTo(action: MajorActionType, window: PowerWindow): Boolean =
    window.associatedMajorAction.contains(action)
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
case object NoFacts extends PowerFacts

final case class PowerContext(window: PowerWindow, source: RuleSourceRef,
    facts: PowerFacts)

trait PowerInspector {
  def inspect(context: PowerContext): PowerInspection
}

/** Marker for procedure-owned executable mechanics. Concrete action modules
  * define and interpret their own typed handler/effect vocabulary.
  */
trait PowerHandler

final case class RegisteredPower(
    definition: PowerDefinition,
    inspector: PowerInspector,
    implementation: Option[PowerHandler]
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
