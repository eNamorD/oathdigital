package oathdigital.application

import oathdigital.gameplay.{TradeResource, WakeResource}
import oathdigital.model._

sealed trait GameIntent extends Product with Serializable
object GameIntent {
  final case class PlacePawn(siteId: SiteId) extends GameIntent
  final case class ChooseAdviser(adviserId: DenizenId) extends GameIntent
  final case class TakeWealth(resource: WakeResource) extends GameIntent
  case object EndWake extends GameIntent
  case object BeginRest extends GameIntent
  case object FinishRest extends GameIntent
  final case class Travel(destinationSiteId: SiteId) extends GameIntent
  final case class Muster(target: EconomyTargetRef) extends GameIntent
  final case class Trade(target: EconomyTargetRef, resource: TradeResource)
      extends GameIntent
  final case class BeginSearch(source: SearchSource) extends GameIntent
  case object BeginRecover extends GameIntent
  case object BeginForge extends GameIntent
  final case class CompleteForge(decision: DecisionId,
      assignments: Vector[ForgeResourceAssignment]) extends GameIntent
  final case class BeginChallenge(banner: Banner) extends GameIntent
  final case class ChooseChallengeSecretSite(decision: DecisionId, site: SiteId) extends GameIntent
  final case class CompleteChallenge(decision: DecisionId, amount: Int) extends GameIntent
  final case class PlaceBannerResource(banner: Banner, amount: Int) extends GameIntent
  final case class DiscardFacedownAdviser(adviser: WorldCardId) extends GameIntent
  final case class PlayFacedownAdviser(adviser: WorldCardId,
      placement: SearchPlacement) extends GameIntent
  final case class RevealVision(vision: VisionId) extends GameIntent
  final case class PlayConspiracy(target: Option[ConspiracyTargetRef]) extends GameIntent
  final case class ChooseConspiracySecretSite(decision: DecisionId, site: SiteId)
      extends GameIntent
  case object PeekSiteRelics extends GameIntent
  final case class RevealOwnedRelic(relic: RelicId) extends GameIntent
  final case class MoveWarbands(toSite: Boolean, amount: Int) extends GameIntent
  final case class BeginNegotiation(participants: Vector[PlayerId]) extends GameIntent
  final case class ReplaceNegotiationTerms(decision: DecisionId,
      terms: NegotiationTerms) extends GameIntent
  final case class AcceptNegotiation(decision: DecisionId) extends GameIntent
  final case class DeclineNegotiation(decision: DecisionId) extends GameIntent
  final case class AddRecoverDice(decision: DecisionId) extends GameIntent
  final case class StopRecover(decision: DecisionId) extends GameIntent
  final case class BeginCampaignConquest(
      targetSiteIds: Vector[SiteId],
      attackDiceCount: Int
  ) extends GameIntent
  final case class BeginCampaignRaid(
      targets: Vector[CampaignRaidTarget], attackDiceCount: Int) extends GameIntent
  object BeginCampaignConquest {
    def apply(targetSiteId: SiteId, attackDiceCount: Int): BeginCampaignConquest =
      new BeginCampaignConquest(Vector(targetSiteId), attackDiceCount)
  }
  final case class ChooseCampaignPlan(
      decision: DecisionId,
      source: PendingProcedure.CampaignPlanSource
  ) extends GameIntent
  final case class FinishCampaignPlans(decision: DecisionId) extends GameIntent
  final case class ChooseCampaignSacrifice(
      decision: DecisionId,
      count: Int
  ) extends GameIntent
  final case class PlaceCampaignForce(
      decision: DecisionId,
      allocations: Vector[CampaignForceAllocation]
  ) extends GameIntent
  final case class RelocateCampaignRaidPawn(
      decision: DecisionId, destinationSiteId: SiteId) extends GameIntent
  final case class ChooseOathkeeperRecipient(
      decision: DecisionId, recipient: PlayerId) extends GameIntent
  final case class CompleteSearch(
      decision: DecisionId,
      kept: WorldCardId,
      discardedInOrder: Vector[WorldCardId],
      placement: SearchPlacement
  ) extends GameIntent
  final case class ResolveCardDecision(
      decision: DecisionId,
      resolution: CardDecisionResolution
  ) extends GameIntent
}
