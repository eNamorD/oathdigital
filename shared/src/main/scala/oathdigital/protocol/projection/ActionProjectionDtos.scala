package oathdigital.protocol.projection

sealed trait BoardTargetRefProjection extends Product with Serializable {
  def stableKey: String = this match {
    case BoardTargetRefProjection.Player(id) => s"player:$id"
    case BoardTargetRefProjection.Site(id) => s"site:$id"
    case BoardTargetRefProjection.SiteCard(site, kind, id) =>
      s"site-card:$site:$kind:$id"
    case BoardTargetRefProjection.PlayerAdviser(player, card) =>
      s"player-adviser:$player:$card"
    case BoardTargetRefProjection.PlayerRelic(player, relic) =>
      s"player-relic:$player:$relic"
    case BoardTargetRefProjection.PlayerPawn(player) => s"player-pawn:$player"
    case BoardTargetRefProjection.PlayerBanner(player, banner) =>
      s"player-banner:$player:$banner"
  }
}
object BoardTargetRefProjection {
  final case class Player(playerId: String) extends BoardTargetRefProjection
  final case class Site(siteId: String) extends BoardTargetRefProjection
  final case class SiteCard(siteId: String, cardKind: String, cardId: String)
      extends BoardTargetRefProjection
  final case class PlayerAdviser(playerId: String, cardId: String)
      extends BoardTargetRefProjection
  final case class PlayerRelic(playerId: String, relicId: String)
      extends BoardTargetRefProjection
  final case class PlayerPawn(playerId: String) extends BoardTargetRefProjection
  final case class PlayerBanner(playerId: String, banner: String)
      extends BoardTargetRefProjection
}
final case class BoardTargetCandidateProjection(
    target: BoardTargetRefProjection,
    label: String,
    details: Vector[String] = Vector.empty
)
final case class BoardTargetFormationProjection(
    minimumForce: Int,
    maximumForce: Int,
    availableWarbands: Int,
    supplyCost: Int
) {
  require(minimumForce >= 0, "formation minimum must be non-negative")
  require(maximumForce >= minimumForce,
    "formation maximum must include minimum")
  require(maximumForce <= availableWarbands,
    "formation maximum cannot exceed available warbands")
  require(availableWarbands >= 0,
    "formation available warbands must be non-negative")
  require(supplyCost >= 0, "formation Supply cost must be non-negative")
}
final case class BoardTargetActionProjection(
    actionKind: String,
    prompt: String,
    minimum: Int,
    maximum: Int,
    autoActivate: Boolean,
    candidates: Vector[BoardTargetCandidateProjection],
    formation: Option[BoardTargetFormationProjection] = None,
    requiredTargets: Vector[BoardTargetRefProjection] = Vector.empty,
    decisionId: Option[String] = None,
    explicitConfirm: Boolean = false
) {
  require(minimum >= 0, "selection minimum must be non-negative")
  require(maximum >= minimum, "selection maximum must include minimum")
  require(maximum <= candidates.size,
    "selection maximum cannot exceed authorized candidates")
  require(requiredTargets.distinct.size == requiredTargets.size,
    "required selection targets must be distinct")
  require(requiredTargets.forall(required => candidates.exists(_.target == required)),
    "required selection targets must be authorized candidates")
  require(requiredTargets.size <= minimum,
    "required selection targets must fit within the minimum")
}
final case class CardResolutionProjection(
    kind: String,
    orientation: Option[String] = None,
    replacementRequired: Boolean = false,
    replacementTargets: Vector[CardDetailsProjection] = Vector.empty) {
  def replacement: Option[CardDetailsProjection] = replacementTargets.headOption
}
final case class PendingCardDecisionProjection(
    decisionId: String,
    kind: String,
    actorPlayerId: String,
    prompt: String,
    instructions: Vector[String],
    cards: Vector[CardDetailsProjection],
    keepMinimum: Int,
    keepMaximum: Int,
    orderingRequired: Boolean,
    resolutionsByCard: Map[String, Vector[CardResolutionProjection]]
)
final case class RecoverProjection(
    decisionId: String, dice: Vector[String], shields: Int,
    difficulty: Int, supplySpent: Int, supplyRemaining: Int,
    canAddDice: Boolean, canStop: Boolean)
final case class ForgeAssignmentTargetProjection(
    siteId: String, denizenId: String, label: String)
final case class ForgeProjection(
    decisionId: String, actorPlayerId: String, favor: Int, secrets: Int,
    targets: Vector[ForgeAssignmentTargetProjection])
final case class BannerProjection(key: String, face: String,
    holderPlayerId: Option[String], resources: Int) {
  def banner: String = key
}
final case class ChallengeProjection(decisionId: String, actorPlayerId: String,
    banner: String, priorHolderPlayerId: Option[String], priorResources: Int,
    legalSecretSiteIds: Vector[String],
    minimumPlacement: Int, maximumPlacement: Int)
final case class CampaignProjection(
    decisionId: String, targetSiteIds: Vector[String], force: Int,
    plansFinished: Boolean, planChoices: Vector[CampaignPlanChoiceProjection],
    selectedPlans: Vector[CampaignPlanChoiceProjection],
    attackDice: Vector[String], attack: Int, skullLosses: Int,
    maxSacrifice: Int, sacrificed: Option[Int], defenseDice: Vector[String],
    defense: Option[Int], victorious: Option[Boolean], maxPlacement: Int,
    placementTargets: Vector[CampaignPlacementTargetProjection],
    defenderKind: String = "bandits", defenderPlayerId: Option[String] = None,
    defenderForce: Int = 0, defenseDiceCount: Int = 0,
    planSide: String = "attacker", decisionOwnerPlayerId: Option[String] = None,
    kind: String = "conquest", raidTargets: Vector[String] = Vector.empty)
final case class CampaignPlacementTargetProjection(siteId: String, label: String)
final case class CampaignRaidRelocationProjection(
    decisionId: String, actorPlayerId: String, defenderPlayerId: String,
    originSiteId: String, legalSiteIds: Vector[String])
final case class CampaignPlanChoiceProjection(
    kind: String, sourceKey: Option[String], playerId: Option[String],
    siteId: Option[String], cardId: Option[String], label: String,
    handlerId: Option[String], favorCost: Int, secretCost: Int,
    mechanicalResult: String)
final case class OathkeeperProjection(
    goal: String, holderPlayerId: Option[String], side: String,
    usurperLimited: Boolean, winnerPlayerId: Option[String],
    winnerVictoryKind: Option[String] = None)
final case class OathkeeperRecipientProjection(
    decisionId: String, actorPlayerId: String,
    candidatePlayerIds: Vector[String])
final case class PlayerBoardProjection(
    playerId: String,
    warbands: Int,
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    committedSecrets: Int,
    totalSecrets: Int,
    supply: Int,
    pawnSiteId: Option[String],
    advisers: Vector[CardDetailsProjection],
    relics: Vector[CardDetailsProjection],
    revealedVision: Option[CardDetailsProjection],
    banners: Vector[BannerProjection] = Vector.empty
)
final case class MinorAdviserProjection(card: CardDetailsProjection,
    placements: Vector[CardResolutionProjection])
final case class MinorActionsProjection(advisers: Vector[MinorAdviserProjection],
    canPeekSiteRelics: Boolean, facedownRelics: Vector[CardDetailsProjection],
    siteId: Option[String], maxBoardToSite: Int, maxSiteToBoard: Int)
final case class NegotiationTransferProjection(authorPlayerId: String,
    recipientPlayerId: String, favor: Int, relicCount: Int,
    relics: Vector[CardDetailsProjection])
final case class NegotiationDisclosureProjection(authorPlayerId: String,
    recipientPlayerId: String, kind: String,
    card: Option[CardDetailsProjection])
final case class NegotiationProjection(decisionId: String, actorPlayerId: String,
    siteId: String, participantPlayerIds: Vector[String],
    acceptedPlayerIds: Vector[String], transfers: Vector[NegotiationTransferProjection],
    disclosures: Vector[NegotiationDisclosureProjection],
    editableFavor: Int, editableRelics: Vector[CardDetailsProjection],
    editableAdvisers: Vector[CardDetailsProjection],
    editableSiteRelics: Vector[CardDetailsProjection])
final case class RestFavorSourceProjection(kind: String, siteId: String,
    sourceId: String, label: String, availableFavor: Int)
final case class RestPowerProjection(decisionId: String,
    restActorPlayerId: String, decisionOwnerPlayerId: String, powerId: String,
    sources: Vector[RestFavorSourceProjection], legalBanks: Vector[String])
