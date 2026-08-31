package oathdigital.application

import oathdigital.gameplay.{OrderedRuleInvocation, TradeResource, WakeResource}
import oathdigital.gameplay.setup.FirstGameSetupPlan
import oathdigital.model._

sealed trait GameCommand extends Product with Serializable
object GameCommand {
  final case class WithModifiers(command: GameCommand,
      ordered: Vector[OrderedRuleInvocation]) extends GameCommand
  final case class Begin(plan: FirstGameSetupPlan) extends GameCommand
  final case class PlacePawn(playerId: PlayerId, siteId: SiteId)
      extends GameCommand
  /** Internal setup adapter retained for rules tests; transports use ResolveCardDecision. */
  final case class ChooseAdviser(playerId: PlayerId, adviserId: DenizenId)
      extends GameCommand
  final case class TakeWealth(playerId: PlayerId, resource: WakeResource)
      extends GameCommand
  final case class EndWake(playerId: PlayerId) extends GameCommand
  final case class Travel(playerId: PlayerId, destinationSiteId: SiteId)
      extends GameCommand
  final case class Muster(playerId: PlayerId, target: EconomyTargetRef)
      extends GameCommand
  final case class Trade(playerId: PlayerId, target: EconomyTargetRef,
      resource: TradeResource) extends GameCommand
  final case class BeginSearch(playerId: PlayerId, source: SearchSource)
      extends GameCommand
  final case class BeginRecover(playerId: PlayerId) extends GameCommand
  final case class BeginForge(playerId: PlayerId) extends GameCommand
  final case class CompleteForge(playerId: PlayerId, decision: DecisionId,
      assignments: Vector[ForgeResourceAssignment]) extends GameCommand
  final case class BeginChallenge(playerId: PlayerId, banner: Banner) extends GameCommand
  final case class ChooseChallengeSecretSite(playerId: PlayerId, decision: DecisionId,
      site: SiteId) extends GameCommand
  final case class CompleteChallenge(playerId: PlayerId, decision: DecisionId,
      amount: Int) extends GameCommand
  final case class PlaceBannerResource(playerId: PlayerId, banner: Banner,
      amount: Int) extends GameCommand
  final case class ResolveFacedownAdviser(playerId: PlayerId, adviser: WorldCardId,
      placement: Option[SearchPlacement]) extends GameCommand
  final case class PeekSiteRelics(playerId: PlayerId) extends GameCommand
  final case class RevealOwnedRelic(playerId: PlayerId, relic: RelicId)
      extends GameCommand
  final case class MoveWarbands(playerId: PlayerId, toSite: Boolean, amount: Int)
      extends GameCommand
  final case class RevealVision(playerId: PlayerId, visionId: VisionId)
      extends GameCommand
  final case class PlayConspiracy(playerId: PlayerId,
      target: Option[ConspiracyTargetRef]) extends GameCommand
  final case class ChooseConspiracySecretSite(playerId: PlayerId,
      decision: DecisionId, siteId: SiteId) extends GameCommand
  final case class BeginNegotiation(playerId: PlayerId,
      participants: Vector[PlayerId]) extends GameCommand
  final case class ReplaceNegotiationTerms(playerId: PlayerId,
      decision: DecisionId, terms: NegotiationTerms) extends GameCommand
  final case class AcceptNegotiation(playerId: PlayerId, decision: DecisionId)
      extends GameCommand
  final case class DeclineNegotiation(playerId: PlayerId, decision: DecisionId)
      extends GameCommand
  final case class AddRecoverDice(playerId: PlayerId, decision: DecisionId)
      extends GameCommand
  final case class StopRecover(playerId: PlayerId, decision: DecisionId)
      extends GameCommand
  final case class BeginCampaignConquest(playerId: PlayerId, targetSiteIds: Vector[SiteId],
      attackDiceCount: Int) extends GameCommand
  final case class BeginCampaignRaid(playerId: PlayerId,
      targets: Vector[CampaignRaidTarget], attackDiceCount: Int) extends GameCommand
  object BeginCampaignConquest {
    def apply(playerId: PlayerId, targetSiteId: SiteId,
        attackDiceCount: Int): BeginCampaignConquest =
      new BeginCampaignConquest(playerId, Vector(targetSiteId), attackDiceCount)
  }
  final case class ChooseCampaignPlan(playerId: PlayerId, decision: DecisionId,
      source: PendingProcedure.CampaignPlanSource) extends GameCommand
  final case class FinishCampaignPlans(playerId: PlayerId, decision: DecisionId)
      extends GameCommand
  final case class ChooseCampaignSacrifice(playerId: PlayerId, decision: DecisionId,
      count: Int) extends GameCommand
  final case class PlaceCampaignForce(playerId: PlayerId, decision: DecisionId,
      allocations: Vector[CampaignForceAllocation]) extends GameCommand
  final case class RelocateCampaignRaidPawn(playerId: PlayerId,
      decision: DecisionId, destinationSiteId: SiteId) extends GameCommand
  final case class ChooseOathkeeperRecipient(playerId: PlayerId,
      decision: DecisionId, recipient: PlayerId) extends GameCommand
  /** Internal Search adapter retained for rules tests; transports use ResolveCardDecision. */
  final case class CompleteSearch(
      playerId: PlayerId,
      decision: DecisionId,
      kept: WorldCardId,
      discardedInOrder: Vector[WorldCardId],
      placement: SearchPlacement
  ) extends GameCommand
  final case class ResolveCardDecision(
      playerId: PlayerId,
      decision: DecisionId,
      resolution: CardDecisionResolution
  ) extends GameCommand
  final case class BeginRest(playerId: PlayerId) extends GameCommand
  final case class FinishRest(playerId: PlayerId) extends GameCommand
  final case class ResolveRestPower(playerId: PlayerId, decision: DecisionId,
      allocations: Vector[FavorAllocation], destinationBank: Suit)
      extends GameCommand
  final case class DeclineRestPower(playerId: PlayerId, decision: DecisionId)
      extends GameCommand
}

sealed trait CardDecisionResolution extends Product with Serializable
object CardDecisionResolution {
  final case class StartingAdviser(adviserId: DenizenId)
      extends CardDecisionResolution
  final case class Search(
      kept: WorldCardId,
      discardedInOrder: Vector[WorldCardId],
      placement: SearchPlacement
  ) extends CardDecisionResolution
  final case class TakeFacedownRelic(relicId: RelicId)
      extends CardDecisionResolution
}
