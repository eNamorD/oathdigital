package oathdigital.model

import oathdigital.model._

sealed trait OathEvent extends Product with Serializable

/** Walker (procedure-walker) event family root (Task 3).
  *
  * Open, not `sealed`: the concrete per-node cases live in
  * `gameplay/walker/WalkerEvents.scala` and later tasks extend per-node
  * payloads from other files, which a sealed root (same-file subclasses only)
  * would forbid — mirroring the `Operation` root decision in Task 1.
  */
trait WalkerEvent extends OathEvent
object OathEvent {
  final case class IgnoredRulesRecorded(
      playerId: PlayerId,
      action: ActionKind,
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
  final case class SiteRelicsPeeked(
      playerId: PlayerId, siteId: SiteId, relics: Vector[RelicId])
      extends OathEvent
  final case class OwnedRelicRevealed(playerId: PlayerId, relicId: RelicId)
      extends OathEvent
  final case class WarbandsMoved(
      playerId: PlayerId, siteId: SiteId, toSite: Boolean, amount: Int,
      priorBoardWarbands: Int, priorSiteWarbands: Int) extends OathEvent
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
      source: CampaignPlanSource,
      handlerId: String, side: CampaignPlanSide,
      costs: Vector[CampaignPlanCost],
      effects: Vector[CampaignPlanEffect]
  ) extends OathEvent {
    def revealed: Boolean = effects.contains(
      CampaignPlanEffect.RevealSource)
    def ignoreAttackSkulls: Boolean = effects.contains(
      CampaignPlanEffect.IgnoreAttackSkulls)
    def addedAttackDice: Int = effects.collect {
      case CampaignPlanEffect.AddAttackDice(n) => n
    }.sum
  }
  final case class CampaignPlansFinished(
      playerId: PlayerId, decision: DecisionId,
      side: CampaignPlanSide,
      orderedSources: Vector[CampaignPlanSource],
      orderedHandlerIds: Vector[String],
      effects: Vector[CampaignPlanEffect],
      attackDice: Vector[AttackDieFace], attack: Int, skullLosses: Int
  ) extends OathEvent {
    def ignoreAttackSkulls: Boolean = effects.contains(
      CampaignPlanEffect.IgnoreAttackSkulls)
    def addedAttackDice: Int = effects.collect {
      case CampaignPlanEffect.AddAttackDice(n) => n
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
      takenBanners: Vector[Banner],
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
  final case class RoundEnded(completedRound: Int, nextRound: Option[Int])
      extends OathEvent
  final case class WarExhaustionResolved(
      winner: PlayerId,
      kind: VictoryKind,
      visionId: Option[VisionId],
      randomCandidates: Vector[PlayerId]
  ) extends OathEvent
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

/** `key` is the resource's spelling in a Take Wealth start selection, which
  * is the only place the choice crosses a wire. It lives on the case so the
  * procedure that reads a selection and the one that builds one cannot spell
  * it differently -- see `TakeWealthProcedure.selection`/`resourceOf`.
  */
sealed trait WakeResource extends Product with Serializable { def key: String }
object WakeResource {
  case object Favor extends WakeResource { val key = "favor" }
  case object Secret extends WakeResource { val key = "secret" }

  val all: Vector[WakeResource] = Vector(Favor, Secret)

  def fromKey(key: String): Option[WakeResource] = all.find(_.key == key)
}
