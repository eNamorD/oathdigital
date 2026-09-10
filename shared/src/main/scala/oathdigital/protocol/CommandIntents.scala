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
  final case class ResolveRestPower(decisionId: String,
      allocations: Vector[RestFavorAllocation], destinationBank: String)
      extends GameIntent
  final case class DeclineRestPower(decisionId: String) extends GameIntent
  final case class Travel(destinationSiteId: String) extends GameIntent
  final case class Muster(target: EconomyTarget) extends GameIntent
  final case class Trade(target: EconomyTarget, resource: String) extends GameIntent
  final case class BeginSearch(source: SearchSource) extends GameIntent
  final case class BeginChallenge(banner: String) extends GameIntent
  final case class ChooseChallengeSecretSite(decisionId: String, siteId: String)
      extends GameIntent
  final case class CompleteChallenge(decisionId: String, amount: Int) extends GameIntent
  final case class PlaceBannerResource(banner: String, amount: Int) extends GameIntent
  final case class ResolveFacedownAdviser(adviser: WorldCard,
      placement: Option[Placement]) extends GameIntent
  final case class RevealVision(visionId: String) extends GameIntent
  final case class PlayConspiracy(target: Option[ConspiracyTarget]) extends GameIntent
  case object PeekSiteRelics extends GameIntent
  final case class RevealOwnedRelic(relicId: String) extends GameIntent
  final case class MoveWarbands(toSite: Boolean, amount: Int) extends GameIntent
  final case class BeginNegotiation(participantPlayerIds: Vector[String]) extends GameIntent
  final case class ReplaceNegotiationTerms(decisionId: String, terms: NegotiationTerms)
      extends GameIntent
  final case class AcceptNegotiation(decisionId: String) extends GameIntent
  final case class DeclineNegotiation(decisionId: String) extends GameIntent
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
  /** Starts a walker action. `action` is the engine's persisted `ActionRef`
    * wire key (e.g. `"recover"`); `modifiers` is the ordered list of opaque
    * player-selected power ids chosen before the walk begins -- validated
    * engine-side against the audited catalog, never interpreted here.
    */
  final case class StartWalker(action: String, modifiers: Vector[String])
      extends GameIntent
  /** Answers the currently parked Roll node for `pool`. Carries no die
    * faces: those are generated application-side once the parked pool is
    * validated against this command.
    */
  final case class RollWalker(pool: String) extends GameIntent
  final case class ResolveWalker(decisionId: String,
      payload: DecisionPayloadWire) extends GameIntent
}

final case class EconomyTarget(kind: String, id: String)
final case class SearchSource(source: String, region: Option[String])
final case class WorldCard(kind: String, id: String)
final case class CardRef(kind: String, id: String)
final case class Placement(kind: String, replace: Option[CardRef])
final case class ForgeAssignment(siteId: String, denizenId: String, resource: String)
final case class CampaignForceAllocation(siteId: String, count: Int)
final case class RestFavorSource(kind: String, siteId: String, sourceId: String)
final case class RestFavorAllocation(source: RestFavorSource, amount: Int)

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
}

/** Wire form of the engine's open `DecisionPayload` trait, bounded to the
  * payloads the registered walker actions declare -- Recover's two and
  * Forge's assignment. An action or power that adds a walker decision
  * widens this family the same way it widens `DecisionPayload` itself --
  * the engine stays generic over both.
  */
sealed trait DecisionPayloadWire extends Product with Serializable
object DecisionPayloadWire {
  /** `choice` is `"continue"` or `"stop"`; validated at the application
    * mapping boundary, not here, matching every other enum-shaped field in
    * this protocol (e.g. `TakeWealth`'s `resource`).
    */
  final case class RecoverChoiceWire(choice: String) extends DecisionPayloadWire
  final case class RecoverRelicWire(relicId: String) extends DecisionPayloadWire
  /** Answers Forge's `"forge.assignment"` decision. Reuses the
    * [[ForgeAssignment]] row the (now walker-driven) Forge assignment has
    * always ridden on, so there is one wire spelling of "this denizen gets
    * this resource" rather than two; `resource` is validated at the
    * application mapping boundary, like every other enum-shaped field here.
    */
  final case class ForgeAssignmentWire(assignments: Vector[ForgeAssignment])
      extends DecisionPayloadWire
}
