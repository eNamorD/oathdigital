package oathdigital.protocol

/** Actorless, cross-platform command vocabulary. All identifiers remain opaque
  * strings until the application mapping boundary validates them.
  */
sealed trait GameIntent extends Product with Serializable

object GameIntent {
  final case class PlacePawn(siteId: String) extends GameIntent
  final case class TakeWealth(resource: String) extends GameIntent
  case object EndWake extends GameIntent
  case object BeginRest extends GameIntent
  case object FinishRest extends GameIntent
  final case class Travel(destinationSiteId: String) extends GameIntent
  final case class Muster(target: EconomyTarget) extends GameIntent
  final case class Trade(target: EconomyTarget, resource: String) extends GameIntent
  final case class BeginSearch(source: SearchSource) extends GameIntent
  case object BeginRecover extends GameIntent
  case object BeginForge extends GameIntent
  final case class CompleteForge(decisionId: String,
      assignments: Vector[ForgeAssignment]) extends GameIntent
  final case class BeginChallenge(banner: String) extends GameIntent
  final case class ChooseChallengeSecretSite(decisionId: String, siteId: String)
      extends GameIntent
  final case class CompleteChallenge(decisionId: String, amount: Int) extends GameIntent
  final case class PlaceBannerResource(banner: String, amount: Int) extends GameIntent
  final case class ResolveFacedownAdviser(adviser: WorldCard,
      placement: Option[Placement]) extends GameIntent
  final case class RevealVision(visionId: String) extends GameIntent
  final case class PlayConspiracy(target: Option[ConspiracyTarget]) extends GameIntent
  final case class ChooseConspiracySecretSite(decisionId: String, siteId: String)
      extends GameIntent
  case object PeekSiteRelics extends GameIntent
  final case class RevealOwnedRelic(relicId: String) extends GameIntent
  final case class MoveWarbands(toSite: Boolean, amount: Int) extends GameIntent
  final case class BeginNegotiation(participantPlayerIds: Vector[String]) extends GameIntent
  final case class ReplaceNegotiationTerms(decisionId: String, terms: NegotiationTerms)
      extends GameIntent
  final case class AcceptNegotiation(decisionId: String) extends GameIntent
  final case class DeclineNegotiation(decisionId: String) extends GameIntent
  final case class AddRecoverDice(decisionId: String) extends GameIntent
  final case class StopRecover(decisionId: String) extends GameIntent
  final case class BeginCampaignConquest(targetSiteIds: Vector[String],
      attackDiceCount: Int) extends GameIntent
  final case class BeginCampaignRaid(targets: Vector[CampaignRaidTarget],
      attackDiceCount: Int) extends GameIntent
  final case class ChooseCampaignPlan(decisionId: String, source: CampaignPlanSource)
      extends GameIntent
  final case class FinishCampaignPlans(decisionId: String) extends GameIntent
  final case class ChooseCampaignSacrifice(decisionId: String, count: Int) extends GameIntent
  final case class PlaceCampaignForce(decisionId: String,
      allocations: Vector[CampaignForceAllocation]) extends GameIntent
  final case class RelocateCampaignRaidPawn(decisionId: String,
      destinationSiteId: String) extends GameIntent
  final case class ChooseOathkeeperRecipient(decisionId: String,
      recipientPlayerId: String) extends GameIntent
  final case class ResolveCardDecision(decisionId: String,
      resolution: DecisionResolution) extends GameIntent
}

final case class EconomyTarget(kind: String, id: String)
final case class SearchSource(source: String, region: Option[String])
final case class WorldCard(kind: String, id: String)
final case class CardRef(kind: String, id: String)
final case class Placement(kind: String, replace: Option[CardRef])
final case class ForgeAssignment(siteId: String, denizenId: String, resource: String)
final case class CampaignForceAllocation(siteId: String, count: Int)

sealed trait ConspiracyTarget extends Product with Serializable
object ConspiracyTarget {
  final case class RelicSlot(ownerPlayerId: String, slot: Int) extends ConspiracyTarget
  final case class Banner(ownerPlayerId: String, banner: String) extends ConspiracyTarget
}

sealed trait CampaignRaidTarget extends Product with Serializable
object CampaignRaidTarget {
  final case class Pawn(playerId: String) extends CampaignRaidTarget
  final case class Relic(playerId: String, relicId: String) extends CampaignRaidTarget
  final case class Banner(playerId: String, banner: String) extends CampaignRaidTarget
}

sealed trait CampaignPlanSource extends Product with Serializable
object CampaignPlanSource {
  final case class Adviser(playerId: String, cardId: String) extends CampaignPlanSource
  final case class SiteCard(siteId: String, cardId: String) extends CampaignPlanSource
  final case class Relic(playerId: String, cardId: String) extends CampaignPlanSource
  final case class Title(playerId: String) extends CampaignPlanSource
}

final case class NegotiationTerms(
    transfers: Vector[NegotiationTransfer],
    disclosures: Vector[NegotiationDisclosure]
)
final case class NegotiationTransfer(
    recipientPlayerId: String, favor: Int, relicIds: Vector[String])
final case class NegotiationDisclosure(
    recipientPlayerId: String, information: NegotiationInformation)

sealed trait NegotiationInformation extends Product with Serializable
object NegotiationInformation {
  final case class Adviser(ownerPlayerId: String, card: WorldCard)
      extends NegotiationInformation
  final case class HeldRelic(ownerPlayerId: String, relicId: String)
      extends NegotiationInformation
  final case class SiteRelic(siteId: String, relicId: String)
      extends NegotiationInformation
}

sealed trait DecisionResolution extends Product with Serializable
object DecisionResolution {
  final case class StartingAdviser(adviserId: String) extends DecisionResolution
  final case class Search(kept: WorldCard, discardedInOrder: Vector[WorldCard],
      placement: Placement) extends DecisionResolution
  final case class TakeFacedownRelic(relicId: String) extends DecisionResolution
}
