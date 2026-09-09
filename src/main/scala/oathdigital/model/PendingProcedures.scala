package oathdigital.model

sealed trait PendingProcedure extends Product with Serializable {
  def decision: DecisionId
}

sealed trait ConspiracyTarget extends Product with Serializable {
  def owner: PlayerId
  def stableKey: String
}

sealed trait ConspiracyTargetRef extends Product with Serializable {
  def owner: PlayerId
  def stableKey: String
}
object ConspiracyTargetRef {
  final case class RelicSlot(owner: PlayerId, slot: Int)
      extends ConspiracyTargetRef {
    require(slot >= 0, "Conspiracy relic slot must be non-negative")
    def stableKey = s"player:${owner.value}:relic-slot:$slot"
  }
  final case class Banner(owner: PlayerId, banner: oathdigital.model.Banner)
      extends ConspiracyTargetRef {
    def stableKey = s"player:${owner.value}:banner:${banner.key}"
  }
}
object ConspiracyTarget {
  final case class Relic(owner: PlayerId, relic: RelicId) extends ConspiracyTarget {
    def stableKey = s"player:${owner.value}:relic:${relic.value}"
  }
  final case class Banner(owner: PlayerId, banner: oathdigital.model.Banner)
      extends ConspiracyTarget {
    def stableKey = s"player:${owner.value}:banner:${banner.key}"
  }
}

/** Stable, container-qualified target for a denizen printed at a site. */
final case class SiteDenizenTarget(siteId: SiteId, denizenId: DenizenId) {
  def stableKey: String = s"site:${siteId.value}:denizen:${denizenId.value}"
}

/** A card at a site that can hold favor during a Rest power decision. */
sealed trait SiteFavorSource extends Product with Serializable {
  def siteId: SiteId
  def stableKey: String
}
object SiteFavorSource {
  final case class Denizen(siteId: SiteId, id: DenizenId)
      extends SiteFavorSource {
    def stableKey: String = s"site:${siteId.value}:denizen:${id.value}"
  }
  final case class Edifice(siteId: SiteId, id: EdificeId)
      extends SiteFavorSource {
    def stableKey: String = s"site:${siteId.value}:edifice:${id.value}"
  }
  final case class Relic(siteId: SiteId, slot: Int)
      extends SiteFavorSource {
    require(slot >= 0, "site relic slot must be non-negative")
    def stableKey: String = s"site:${siteId.value}:relic-slot:$slot"
  }
}

final case class FavorAllocation(source: SiteFavorSource, amount: Int) {
  require(amount > 0, "favor allocation must be positive")
}

sealed trait RestPowerSourceRef extends Product with Serializable {
  def stableKey: String
}
object RestPowerSourceRef {
  final case class Site(id: SiteId) extends RestPowerSourceRef {
    def stableKey = s"site:${id.value}"
  }
  final case class SiteCard(siteId: SiteId, id: CardId)
      extends RestPowerSourceRef {
    def stableKey = s"site-card:${siteId.value}:${id.kind}:${id.value}"
  }
  final case class Adviser(playerId: PlayerId, id: CardId)
      extends RestPowerSourceRef {
    def stableKey = s"adviser:${playerId.value}:${id.kind}:${id.value}"
  }
  final case class Relic(playerId: PlayerId, id: RelicId)
      extends RestPowerSourceRef {
    def stableKey = s"relic:${playerId.value}:${id.value}"
  }
  final case class SiteRelic(siteId: SiteId, id: RelicId)
      extends RestPowerSourceRef {
    def stableKey = s"site-relic:${siteId.value}:${id.value}"
  }
  final case class Edifice(siteId: SiteId, id: EdificeId)
      extends RestPowerSourceRef {
    def stableKey = s"edifice:${siteId.value}:${id.value}"
  }
  final case class Banner(id: oathdigital.model.Banner)
      extends RestPowerSourceRef {
    def stableKey = s"banner:${id.key}"
  }
  final case class Foundation(number: FoundationNumber)
      extends RestPowerSourceRef {
    def stableKey = s"foundation:${number.value}"
  }
  final case class Legacy(lineageId: LineageId, id: LegacyId)
      extends RestPowerSourceRef {
    def stableKey = s"legacy:${lineageId.value}:${id.value}"
  }
  final case class GameRule(id: String) extends RestPowerSourceRef {
    def stableKey = s"game:$id"
  }
}

/** Stable typed source identity and dynamic owner for one selected Rest hook. */
final case class RestPowerInvocationRef(
    powerId: PowerId,
    source: RestPowerSourceRef,
    decisionOwner: PlayerId)

sealed trait RestPowerDecisionPayload extends Product with Serializable
object RestPowerDecisionPayload {
  final case class LeagueTreaty(
      eligibleSources: Vector[SiteFavorSource],
      legalBanks: Vector[Suit]
  ) extends RestPowerDecisionPayload {
    require(eligibleSources.nonEmpty &&
      eligibleSources.map(_.stableKey).distinct.size == eligibleSources.size,
      "League Treaty favor sources must be non-empty and distinct")
    require(legalBanks.nonEmpty && legalBanks.distinct.size == legalBanks.size,
      "League Treaty favor banks must be non-empty and distinct")
  }
}

sealed trait ForgeResource extends Product with Serializable { def key: String }
object ForgeResource {
  case object Favor extends ForgeResource { val key = "favor" }
  case object Secret extends ForgeResource { val key = "secret" }
}
final case class ForgeResourceAssignment(
    target: SiteDenizenTarget, resource: ForgeResource)

final case class CampaignForceAllocation(site: SiteId, count: Int) {
  require(count >= 0, "Campaign allocation must be non-negative")
}

sealed trait CampaignKind extends Product with Serializable {
  def key: String
}
object CampaignKind {
  case object Conquest extends CampaignKind { val key = "conquest" }
  case object Raid extends CampaignKind { val key = "raid" }
}

sealed trait CampaignBanner extends Product with Serializable {
  def key: String
  private[model] def order: Int
}
object CampaignBanner {
  case object PeoplesFavor extends CampaignBanner {
    val key = "peoples-favor"
    private[model] val order = 0
  }
  case object DarkestSecret extends CampaignBanner {
    val key = "darkest-secret"
    private[model] val order = 1
  }
}

sealed trait CampaignRaidTarget extends Product with Serializable {
  def playerId: PlayerId
  def stableKey: String
  private[model] def canonicalOrder: (Int, String)
}
object CampaignRaidTarget {
  final case class Pawn(playerId: PlayerId) extends CampaignRaidTarget {
    def stableKey: String = s"pawn:${playerId.value}"
    private[model] def canonicalOrder = 0 -> playerId.value
  }
  final case class Relic(playerId: PlayerId, relicId: RelicId)
      extends CampaignRaidTarget {
    def stableKey: String = s"relic:${playerId.value}:${relicId.value}"
    private[model] def canonicalOrder = 1 -> s"${playerId.value}:${relicId.value}"
  }
  final case class Banner(playerId: PlayerId, banner: CampaignBanner)
      extends CampaignRaidTarget {
    def stableKey: String = s"banner:${playerId.value}:${banner.key}"
    private[model] def canonicalOrder =
      (2 + banner.order) -> playerId.value
  }

  def canonical(targets: Iterable[CampaignRaidTarget]): Vector[CampaignRaidTarget] =
    targets.toVector.sortBy(_.canonicalOrder)

  def isCanonical(targets: Vector[CampaignRaidTarget]): Boolean =
    targets.nonEmpty && targets.head.isInstanceOf[Pawn] &&
      targets.map(_.playerId).distinct.size == 1 &&
      targets.map(_.stableKey).distinct.size == targets.size &&
      canonical(targets) == targets
}

sealed trait CampaignLosingForceEffect extends Product with Serializable {
  def site: SiteId
}
sealed trait CampaignDefender extends Product with Serializable
object CampaignDefender {
  case object Bandits extends CampaignDefender
  final case class Player(playerId: PlayerId) extends CampaignDefender
}
object CampaignLosingForceEffect {
  final case class Remove(site: SiteId, force: ForceKind, count: Int)
      extends CampaignLosingForceEffect {
    require(count > 0, "removed Campaign force must be positive")
  }
  final case class Preserve(site: SiteId, force: ForceKind, count: Int)
      extends CampaignLosingForceEffect {
    require(count > 0, "preserved Campaign force must be positive")
  }
  final case class Relocate(site: SiteId, destination: SiteId,
      force: ForceKind, count: Int) extends CampaignLosingForceEffect {
    require(site != destination, "Campaign relocation needs a different site")
    require(count > 0, "relocated Campaign force must be positive")
  }
  final case class Replace(site: SiteId, force: ForceKind, count: Int,
      replacementForce: Option[ForceKind], replacementCount: Int)
      extends CampaignLosingForceEffect {
    require(count > 0, "replaced Campaign force must be positive")
    require(replacementCount >= 0, "replacement force must be non-negative")
    require(replacementForce.nonEmpty == (replacementCount > 0),
      "replacement force and count must agree")
  }
  final case class ReturnToBoard(site: SiteId, player: PlayerId,
      force: ForceKind, count: Int)
      extends CampaignLosingForceEffect {
    require(count > 0, "returned Campaign force must be positive")
  }
  final case class KillCommitted(site: SiteId, player: PlayerId,
      force: ForceKind, count: Int) extends CampaignLosingForceEffect {
    require(count > 0, "killed committed Campaign force must be positive")
  }
  final case class RelocateCommitted(site: SiteId, player: PlayerId,
      force: ForceKind, count: Int) extends CampaignLosingForceEffect {
    require(count > 0, "relocated committed Campaign force must be positive")
  }
  final case class PreserveCommitted(site: SiteId, player: PlayerId,
      force: ForceKind, count: Int) extends CampaignLosingForceEffect {
    require(count > 0, "preserved committed Campaign force must be positive")
  }
}
final case class CampaignRaidBoardLoss(playerId: PlayerId, killed: Int,
    returned: Int) {
  require(killed >= 0 && returned >= 0, "Raid board losses must be non-negative")
}
object PendingProcedure {
  sealed trait CampaignPlanSide extends Product with Serializable
  object CampaignPlanSide {
    case object Attacker extends CampaignPlanSide
    case object Defender extends CampaignPlanSide
  }

  sealed trait CampaignPlanCost extends Product with Serializable
  object CampaignPlanCost {
    final case class Favor(count: Int) extends CampaignPlanCost {
      require(count > 0, "Campaign favor cost must be positive")
    }
    final case class Secret(count: Int) extends CampaignPlanCost {
      require(count > 0, "Campaign secret cost must be positive")
    }
  }

  sealed trait CampaignPlanEffect extends Product with Serializable
  object CampaignPlanEffect {
    final case class AddAttackDice(count: Int) extends CampaignPlanEffect {
      require(count > 0, "added Campaign attack dice must be positive")
    }
    final case class AddDefenseDice(count: Int) extends CampaignPlanEffect {
      require(count > 0, "added Campaign defense dice must be positive")
    }
    case object IgnoreAttackSkulls extends CampaignPlanEffect
    case object RevealSource extends CampaignPlanEffect
    /** Typed extension points: their payload remains owned by a registered
      * handler rather than interpreted as a general card scripting language.
      */
    final case class TransformAttackResult(handlerId: String)
        extends CampaignPlanEffect
    final case class ReplaceLosingForcePolicy(policyId: String)
        extends CampaignPlanEffect
    final case class Suspend(decisionKind: String) extends CampaignPlanEffect
  }

  sealed trait CampaignPlanSource extends Product with Serializable {
    def stableKey: String
  }
  object CampaignPlanSource {
    final case class Adviser(playerId: PlayerId, id: DenizenId)
        extends CampaignPlanSource {
      def stableKey: String = s"adviser:${playerId.value}:denizen:${id.value}"
    }
    final case class SiteCard(siteId: SiteId, id: DenizenId)
        extends CampaignPlanSource {
      def stableKey: String = s"site-card:${siteId.value}:denizen:${id.value}"
    }
    final case class Relic(playerId: PlayerId, id: RelicId)
        extends CampaignPlanSource {
      def stableKey: String = s"relic:${playerId.value}:${id.value}"
    }
    final case class Title(playerId: PlayerId) extends CampaignPlanSource {
      def stableKey: String = s"title:${playerId.value}"
    }
  }

  final case class CampaignPlanResolution(
      source: CampaignPlanSource,
      handlerId: String,
      side: CampaignPlanSide,
      costs: Vector[CampaignPlanCost],
      effects: Vector[CampaignPlanEffect]
  )

  final case class Search(
      decision: DecisionId,
      actor: PlayerId,
      source: SearchSource = SearchSource.WorldDeck,
      origin: Region = Region.Cradle,
      supplySpent: Int = 0
  ) extends PendingProcedure

  final case class Campaign(
      decision: DecisionId,
      actor: PlayerId,
      targetSites: Vector[SiteId],
      defender: CampaignDefender,
      force: Int,
      plans: Vector[CampaignPlanResolution],
      attackerPlansFinished: Boolean,
      defenderPlansFinished: Boolean,
      attackDice: Vector[AttackDieFace],
      attack: Int,
      skullLosses: Int,
      sacrificed: Option[Int],
      defenseDice: Vector[DefenseDieFace],
      defense: Option[Int],
      victorious: Option[Boolean],
      kind: CampaignKind = CampaignKind.Conquest,
      raidTargets: Vector[CampaignRaidTarget] = Vector.empty
  ) extends PendingProcedure {
    require(kind match {
      case CampaignKind.Conquest => targetSites.nonEmpty && raidTargets.isEmpty
      case CampaignKind.Raid => targetSites.isEmpty &&
        CampaignRaidTarget.isCanonical(raidTargets)
    }, "Campaign targets must match their kind and canonical order")
  }

  final case class CampaignRaidRelocation(
      decision: DecisionId,
      actor: PlayerId,
      defender: PlayerId,
      origin: SiteId,
      legalSites: Vector[SiteId]
  ) extends PendingProcedure {
    require(legalSites.nonEmpty && !legalSites.contains(origin) &&
      legalSites.distinct.size == legalSites.size,
      "Raid relocation sites must be distinct and exclude the origin")
  }

  final case class RestPowerDecision(
      decision: DecisionId,
      restActor: PlayerId,
      current: RestPowerInvocationRef,
      remaining: Vector[RestPowerInvocationRef],
      payload: RestPowerDecisionPayload
  ) extends PendingProcedure

  /** Internal replay marker between deterministically ordered Rest hooks. */
  final case class RestPowerContinuation(
      decision: DecisionId,
      restActor: PlayerId,
      remaining: Vector[RestPowerInvocationRef]
  ) extends PendingProcedure {
    require(remaining.nonEmpty, "Rest power continuation must have remaining hooks")
  }

  final case class Forge(
      decision: DecisionId,
      actor: PlayerId,
      site: SiteId,
      eligibleTargets: Vector[SiteDenizenTarget],
      cost: Tokens,
      supplySpent: Int
  ) extends PendingProcedure {
    require(eligibleTargets.size == 3 && eligibleTargets.distinct.size == 3,
      "Forge requires exactly three distinct denizen targets")
  }

  /** Owner-scoped, replay-stable continuation of the printed banner procedure. */
  final case class Challenge(
      decision: DecisionId,
      actor: PlayerId,
      banner: Banner,
      priorHolder: Option[PlayerId],
      priorResources: Int,
      remainingRibbonResources: Int,
      favorReturned: Vector[Suit] = Vector.empty,
      secretsPlaced: Vector[SiteId] = Vector.empty
  ) extends PendingProcedure

  final case class OathkeeperRecipient(
      decision: DecisionId,
      actor: PlayerId,
      candidates: Vector[PlayerId]
  ) extends PendingProcedure

  final case class Conspiracy(
      decision: DecisionId,
      actor: PlayerId,
      source: VisionId,
      target: Option[ConspiracyTarget],
      favorReturnOrder: Vector[Suit] = Vector.empty,
      awaitingTarget: Boolean = false
  ) extends PendingProcedure

  final case class Negotiation(
      decision: DecisionId,
      actor: PlayerId,
      site: SiteId,
      participants: Vector[PlayerId],
      terms: Map[PlayerId, NegotiationTerms],
      accepted: Set[PlayerId]
  ) extends PendingProcedure {
    require(participants.size >= 2 && participants.head == actor &&
      participants.distinct.size == participants.size,
      "Negotiation participants must be distinct and actor-first")
    require(terms.keySet == participants.toSet,
      "Negotiation terms must exist for every participant")
    require(accepted.subsetOf(participants.toSet),
      "Negotiation acceptances must belong to participants")
  }

}

final case class NegotiationTransfer(
    recipient: PlayerId,
    favor: Int,
    relics: Vector[RelicId]
) {
  require(favor >= 0, "Negotiation favor must be non-negative")
  require(relics.distinct.size == relics.size,
    "Negotiation relic transfers must be distinct")
}

sealed trait NegotiationDisclosureRef extends Product with Serializable
object NegotiationDisclosureRef {
  final case class Adviser(owner: PlayerId, card: WorldCardId)
      extends NegotiationDisclosureRef
  final case class HeldRelic(owner: PlayerId, relic: RelicId)
      extends NegotiationDisclosureRef
  final case class SiteRelic(site: SiteId, relic: RelicId)
      extends NegotiationDisclosureRef
}
final case class NegotiationDisclosure(
    recipient: PlayerId,
    information: NegotiationDisclosureRef
)
final case class NegotiationTerms(
    transfers: Vector[NegotiationTransfer] = Vector.empty,
    disclosures: Vector[NegotiationDisclosure] = Vector.empty
) {
  require(transfers.map(_.recipient).distinct.size == transfers.size,
    "Negotiation transfers must have one row per recipient")
  require(disclosures.distinct.size == disclosures.size,
    "Negotiation disclosures must be distinct")
}
