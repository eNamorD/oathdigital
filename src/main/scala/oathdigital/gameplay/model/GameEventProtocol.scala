package oathdigital.gameplay

import oathdigital.model._
import oathdigital.gameplay.operations.{Cost, RelicPlacement}
import oathdigital.gameplay.setup.FirstGameSetupPlan

sealed trait OathEvent extends Product with Serializable

/** Walker (procedure-walker) event family root (Task 3).
  *
  * Open, not `sealed`: the concrete per-node cases live in
  * `gameplay/walker/WalkerEvents.scala` and later tasks extend per-node
  * payloads from other files, which a sealed root (same-file subclasses only)
  * would forbid — mirroring the `Operation` root decision in Task 1. The one
  * shared shape every walker event carries is the acting player.
  */
trait WalkerEvent extends OathEvent {
  def actor: PlayerId
}
sealed trait RecoverPowerEvent extends OathEvent {
  def playerId: PlayerId
  def decision: DecisionId
  def powerId: PowerId
  def source: RuleSourceRef.SiteCard
  final def siteId: SiteId = source.siteId
}
sealed trait RestPowerEvent extends OathEvent {
  def restActor: PlayerId
  def decision: DecisionId
  def powerId: PowerId
}
sealed trait RestPowerDecisionStarted extends RestPowerEvent {
  def decisionOwner: PlayerId
  def remaining: Vector[RestPowerInvocationRef]
}
sealed trait RestPowerDecisionCompleted extends RestPowerEvent
object OathEvent {
  final case class IgnoredRulesRecorded(
      playerId: PlayerId,
      action: MajorActionKind,
      diagnostics: Vector[IgnoredRuleDiagnostic]
  ) extends OathEvent {
    require(diagnostics.nonEmpty, "ignored-rule event must not be empty")
  }
  final case class FirstGameStarted(plan: FirstGameSetupPlan)
      extends OathEvent
  final case class GamePawnPlaced(playerId: PlayerId, siteId: SiteId)
      extends OathEvent
  final case class StartingAdviserChosen(
      playerId: PlayerId,
      adviserId: DenizenId
  ) extends OathEvent
  case object FirstGameCompleted extends OathEvent
  final case class WealthTaken(
      playerId: PlayerId,
      siteId: SiteId,
      resource: WakeResource
  ) extends OathEvent
  final case class WakeEnded(playerId: PlayerId) extends OathEvent
  final case class Traveled(
      playerId: PlayerId,
      sourceSiteId: SiteId,
      destinationSiteId: SiteId,
      supplySpent: Int
  ) extends OathEvent
  final case class Mustered(
      playerId: PlayerId,
      siteId: SiteId,
      target: EconomyTargetRef,
      suit: Suit,
      supplySpent: Int,
      warbandsGained: Int
  ) extends OathEvent
  final case class Traded(
      playerId: PlayerId,
      siteId: SiteId,
      target: EconomyTargetRef,
      suit: Suit,
      resource: TradeResource,
      supplySpent: Int,
      gained: Int
  ) extends OathEvent
  final case class SearchStarted(
      playerId: PlayerId,
      decision: DecisionId,
      source: SearchSource,
      origin: Region,
      supplySpent: Int,
      drawn: Vector[WorldCardId]
  ) extends OathEvent
  final case class SearchCompleted(
      playerId: PlayerId,
      decision: DecisionId,
      kept: WorldCardId,
      discardedInOrder: Vector[WorldCardId],
      placement: SearchPlacement,
      favorGained: Int = 0,
      discardedWorld: Vector[WorldCardId] = Vector.empty,
      discardedEdifices: Vector[EdificeId] = Vector.empty
  ) extends OathEvent
  final case class RecoverRolled(
      playerId: PlayerId, decision: DecisionId, siteId: SiteId,
      supplySpent: Int, dice: Vector[DefenseDieFace]
  ) extends OathEvent
  final case class CatacombsResolved(playerId: PlayerId, decision: DecisionId,
      powerId: PowerId, source: RuleSourceRef.SiteCard, cost: Cost,
      placement: RelicPlacement) extends RecoverPowerEvent
  final case class RecoverStopped(playerId: PlayerId, decision: DecisionId)
      extends OathEvent
  final case class RelicRecovered(
      playerId: PlayerId, decision: DecisionId, siteId: SiteId, relicId: RelicId
  ) extends OathEvent
  final case class ForgeStarted(
      playerId: PlayerId, decision: DecisionId, siteId: SiteId,
      targets: Vector[SiteDenizenTarget], cost: Tokens, supplySpent: Int
  ) extends OathEvent
  final case class ForgeCompleted(
      playerId: PlayerId, decision: DecisionId, siteId: SiteId,
      assignments: Vector[ForgeResourceAssignment], relicId: RelicId
  ) extends OathEvent
  final case class BannerChallengeStarted(
      playerId: PlayerId, decision: DecisionId, banner: Banner,
      priorHolder: Option[PlayerId], priorResources: Int, supplySpent: Int,
      automaticFavorReturns: Vector[Suit] = Vector.empty,
      automaticSecretSites: Vector[SiteId] = Vector.empty) extends OathEvent
  final case class BannerRibbonChoiceMade(
      playerId: PlayerId, decision: DecisionId, banner: Banner,
      secretSite: SiteId,
      automaticSecretSites: Vector[SiteId] = Vector.empty) extends OathEvent
  final case class BannerChallengeCompleted(
      playerId: PlayerId, decision: DecisionId, banner: Banner,
      priorHolder: Option[PlayerId], priorResources: Int,
      placedResources: Int, favorReturnOrder: Vector[Suit],
      secretSiteOrder: Vector[SiteId], secretsReturnedToHolder: Int)
      extends OathEvent
  final case class BannerResourcePlaced(
      playerId: PlayerId, banner: Banner, amount: Int) extends OathEvent
  final case class FacedownAdviserDiscarded(
      playerId: PlayerId, adviserId: WorldCardId, destination: Region)
      extends OathEvent
  final case class FacedownAdviserPlayed(
      playerId: PlayerId, adviserId: WorldCardId, placement: SearchPlacement,
      favorGained: Int, discardedWorld: Vector[WorldCardId],
      discardedEdifices: Vector[EdificeId]) extends OathEvent
  final case class VisionRevealed(
      playerId: PlayerId, visionId: VisionId, replaced: Option[VisionId],
      destination: Region) extends OathEvent
  final case class ConspiracyStarted(
      playerId: PlayerId, decision: DecisionId, source: VisionId,
      target: Option[ConspiracyTarget], automaticFavorReturns: Vector[Suit])
      extends OathEvent
  final case class ConspiracyCompleted(
      playerId: PlayerId, decision: DecisionId, source: VisionId,
      target: Option[ConspiracyTarget], favorReturnOrder: Vector[Suit])
      extends OathEvent
  final case class SiteRelicsPeeked(
      playerId: PlayerId, siteId: SiteId, relics: Vector[RelicId])
      extends OathEvent
  final case class OwnedRelicRevealed(playerId: PlayerId, relicId: RelicId)
      extends OathEvent
  final case class WarbandsMoved(
      playerId: PlayerId, siteId: SiteId, toSite: Boolean, amount: Int,
      priorBoardWarbands: Int, priorSiteWarbands: Int) extends OathEvent
  final case class NegotiationStarted(
      playerId: PlayerId, decision: DecisionId, siteId: SiteId,
      participants: Vector[PlayerId]) extends OathEvent
  final case class NegotiationTermsReplaced(
      playerId: PlayerId, decision: DecisionId, terms: NegotiationTerms)
      extends OathEvent
  final case class NegotiationAccepted(
      playerId: PlayerId, decision: DecisionId) extends OathEvent
  final case class NegotiationDeclined(
      playerId: PlayerId, decision: DecisionId) extends OathEvent
  final case class NegotiationCompleted(
      playerId: PlayerId, decision: DecisionId,
      participants: Vector[PlayerId], terms: Map[PlayerId, NegotiationTerms])
      extends OathEvent
  final case class CampaignStarted(
      playerId: PlayerId, decision: DecisionId, targetSites: Vector[SiteId],
      defender: CampaignDefender,
      supplySpent: Int, force: Int,
      kind: CampaignKind = CampaignKind.Conquest,
      raidTargets: Vector[CampaignRaidTarget] = Vector.empty
  ) extends OathEvent {
    require(kind match {
      case CampaignKind.Conquest => targetSites.nonEmpty && raidTargets.isEmpty
      case CampaignKind.Raid => targetSites.isEmpty &&
        CampaignRaidTarget.isCanonical(raidTargets)
    }, "CampaignStarted targets must match their kind and canonical order")
  }
  object CampaignStarted {
    def apply(playerId: PlayerId, decision: DecisionId, siteId: SiteId,
        supplySpent: Int, force: Int): CampaignStarted =
      new CampaignStarted(playerId, decision, Vector(siteId),
        CampaignDefender.Bandits, supplySpent, force)
  }
  final case class CampaignPlanChosen(
      playerId: PlayerId, decision: DecisionId,
      source: PendingProcedure.CampaignPlanSource,
      handlerId: String, side: PendingProcedure.CampaignPlanSide,
      costs: Vector[PendingProcedure.CampaignPlanCost],
      effects: Vector[PendingProcedure.CampaignPlanEffect]
  ) extends OathEvent {
    def revealed: Boolean = effects.contains(
      PendingProcedure.CampaignPlanEffect.RevealSource)
    def ignoreAttackSkulls: Boolean = effects.contains(
      PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls)
    def addedAttackDice: Int = effects.collect {
      case PendingProcedure.CampaignPlanEffect.AddAttackDice(n) => n
    }.sum
  }
  final case class CampaignPlansFinished(
      playerId: PlayerId, decision: DecisionId,
      side: PendingProcedure.CampaignPlanSide,
      orderedSources: Vector[PendingProcedure.CampaignPlanSource],
      orderedHandlerIds: Vector[String],
      effects: Vector[PendingProcedure.CampaignPlanEffect],
      attackDice: Vector[AttackDieFace], attack: Int, skullLosses: Int
  ) extends OathEvent {
    def ignoreAttackSkulls: Boolean = effects.contains(
      PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls)
    def addedAttackDice: Int = effects.collect {
      case PendingProcedure.CampaignPlanEffect.AddAttackDice(n) => n
    }.sum
  }
  final case class CampaignSacrificed(
      playerId: PlayerId, decision: DecisionId, sacrificed: Int,
      defenseDice: Vector[DefenseDieFace], attack: Int, defense: Int,
      skullLosses: Int, victorious: Boolean,
      losingForcePolicyId: Option[String] = None,
      losingForces: Vector[CampaignLosingForceEffect] = Vector.empty
  ) extends OathEvent
  final case class CampaignConquered(
      playerId: PlayerId, decision: DecisionId,
      losingForcePolicyId: String,
      losingForces: Vector[CampaignLosingForceEffect],
      allocations: Vector[CampaignForceAllocation]
  ) extends OathEvent
  final case class CampaignRaided(
      playerId: PlayerId, decision: DecisionId,
      losingForcePolicyId: String,
      defenderLoss: CampaignRaidBoardLoss,
      takenRelics: Vector[RelicId],
      takenBanners: Vector[CampaignBanner],
      discardedAdvisers: Vector[WorldCardId],
      adviserDiscardRegion: Region,
      boxedConspiracy: Option[VisionId],
      discardedRelics: Vector[RelicId],
      favorBurned: Int,
      bannerFavorReturned: Map[Suit, Int],
      darkestSecretBurned: Int
  ) extends OathEvent
  final case class CampaignRaidPawnRelocated(
      playerId: PlayerId, decision: DecisionId,
      defender: PlayerId, origin: SiteId, destination: SiteId
  ) extends OathEvent
  final case class BanditsRefilled(sites: Vector[(SiteId, Int)]) extends OathEvent
  final case class RestStarted(playerId: PlayerId) extends OathEvent
  final case class LeagueTreatyDecisionStarted(
      restActor: PlayerId,
      decision: DecisionId,
      powerId: PowerId,
      source: SiteDenizenTarget,
      decisionOwner: PlayerId,
      remaining: Vector[RestPowerInvocationRef],
      eligibleSources: Vector[SiteFavorSource],
      legalBanks: Vector[Suit]
  ) extends RestPowerDecisionStarted
  final case class LeagueTreatyResolved(
      restActor: PlayerId,
      decision: DecisionId,
      powerId: PowerId,
      source: SiteDenizenTarget,
      decisionOwner: PlayerId,
      allocations: Vector[FavorAllocation],
      destinationBank: Suit
  ) extends RestPowerDecisionCompleted
  final case class LeagueTreatyDeclined(
      restActor: PlayerId,
      decision: DecisionId,
      powerId: PowerId,
      source: SiteDenizenTarget,
      decisionOwner: PlayerId
  ) extends RestPowerDecisionCompleted
  final case class RestCompleted(
      playerId: PlayerId,
      returnedFavor: Map[Suit, Int],
      returnedSecrets: Int,
      refreshedSupply: Int,
      postRestActivePlayerId: PlayerId,
      completedRound: Int,
      usurperLimited: Boolean
  ) extends OathEvent
  final case class RoundEnded(completedRound: Int, nextRound: Option[Int])
      extends OathEvent
  final case class WarExhaustionResolved(
      winner: PlayerId,
      kind: VictoryKind,
      visionId: Option[VisionId],
      randomCandidates: Vector[PlayerId]
  ) extends OathEvent
  final case class OathkeeperChanged(holder: Option[PlayerId]) extends OathEvent
  final case class OathkeeperRecipientChoiceStarted(
      actor: PlayerId, decision: DecisionId, candidates: Vector[PlayerId])
      extends OathEvent
  final case class OathkeeperRecipientChosen(
      actor: PlayerId, decision: DecisionId, recipient: PlayerId)
      extends OathEvent
  final case class UsurperFlipped(playerId: PlayerId) extends OathEvent
  final case class UsurperVictory(playerId: PlayerId) extends OathEvent
  final case class VisionVictory(playerId: PlayerId, visionId: VisionId)
      extends OathEvent
}

sealed trait TradeResource extends Product with Serializable
object TradeResource {
  case object Favor extends TradeResource
  case object Secret extends TradeResource
}

sealed trait WakeResource extends Product with Serializable
object WakeResource {
  case object Favor extends WakeResource
  case object Secret extends WakeResource
}
