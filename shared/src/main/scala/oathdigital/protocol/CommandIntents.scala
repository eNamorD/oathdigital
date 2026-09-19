package oathdigital.protocol

/** Actorless, cross-platform command vocabulary. All identifiers remain opaque
  * strings until the application mapping boundary validates them.
  */
sealed trait GameIntent extends Product with Serializable

object GameIntent {
  final case class PlacePawn(siteId: String) extends GameIntent
  case object EndWake extends GameIntent
  case object BeginRest extends GameIntent
  case object FinishRest extends GameIntent
  final case class UsePower(powerId: String, source: WalkerStartArgWire)
      extends GameIntent
  final case class BeginChallenge(banner: String) extends GameIntent
  final case class ChooseChallengeSecretSite(decisionId: String, siteId: String)
      extends GameIntent
  final case class CompleteChallenge(decisionId: String, amount: Int) extends GameIntent
  final case class PlaceBannerResource(banner: String, amount: Int) extends GameIntent
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
  final case class ResolveCardDecision(decisionId: String,
      resolution: DecisionResolution) extends GameIntent
  /** Starts a walker procedure. `action` is the engine's persisted `StartableRef`
    * wire key (e.g. `"recover"`); `modifiers` is the ordered list of opaque
    * player-selected power ids chosen before the walk begins -- validated
    * engine-side against the audited catalog, never interpreted here.
    *
    * `startArgs` is what the player selected before the action started, for
    * an action whose tree needs it. Empty for every action that derives its
    * whole tree from the actor's pawn site, which is Recover and Forge;
    * Travel carries its destination here, which is why there is no longer a
    * `Travel` intent of its own. The engine rejects a selection an action did
    * not ask for.
    */
  final case class StartWalker(action: String, modifiers: Vector[String],
      startArgs: Vector[WalkerStartArgWire] = Vector.empty) extends GameIntent
  /** Answers the currently parked Roll node for `pool`. Carries no die
    * faces: those are generated application-side once the parked pool is
    * validated against this command.
    */
  final case class RollWalker(pool: String) extends GameIntent
  final case class ResolveWalker(decisionId: String,
      payload: DecisionAnswerWire) extends GameIntent
}

/** One game-object reference in a walker procedure's start selection, spelled
  * exactly as a decision answer spells one: a kind and an id, decoded by the
  * same `DecisionOptionRef.fromWire` the engine decodes an answer with.
  *
  * Deliberately NOT a case per action. The protocol has no business knowing
  * that a Travel start names a destination -- only the action itself does,
  * and it checks the shape when it builds its tree.
  */
final case class WalkerStartArgWire(optionKind: String, optionId: String)

final case class WorldCard(kind: String, id: String)
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
}

/** Wire form of a walker decision answer, generic over the engine's
  * `DecisionQuery` shapes rather than over any action's own vocabulary.
  *
  * Every option is named by the kind/id pair the engine's option references
  * carry, so neither case here knows that Recover or Forge exists, and a new
  * walker procedure adds no case. Both fields stay opaque strings until the
  * application mapping boundary resolves them, exactly like every other
  * identifier in this protocol.
  */
sealed trait DecisionAnswerWire extends Product with Serializable
object DecisionAnswerWire {
  /** Answers a choose-one decision with the single option selected. */
  final case class ChooseOneWire(optionKind: String, optionId: String)
      extends DecisionAnswerWire

  /** Answers a partition decision: every offered option, each placed in one
    * declared section.
    */
  final case class PartitionWire(placements: Vector[DecisionPlacementWire])
      extends DecisionAnswerWire

  final case class DistributeWire(amounts: Vector[DistributeAmountWire])
      extends DecisionAnswerWire
}

/** One option placed in one section of a [[DecisionAnswerWire.PartitionWire]].
  */
final case class DecisionPlacementWire(optionKind: String, optionId: String,
    sectionKey: String)

final case class DistributeAmountWire(optionKind: String, optionId: String,
    amount: Int)
