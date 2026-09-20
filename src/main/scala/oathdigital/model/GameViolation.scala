package oathdigital.model

import oathdigital.model._

sealed trait OathViolation extends Product with Serializable
object OathViolation {
  final case class CoreOperationRejected(code: String, detail: String)
      extends OathViolation
  final case class UnsupportedRuleCatalog(expected: String, actual: String)
      extends OathViolation
  final case class InvalidModifierInvocation(message: String) extends OathViolation
  case object GameAlreadyExists extends OathViolation
  case object GameNotStarted extends OathViolation
  case object GameAlreadyReady extends OathViolation
  final case class CatalogMismatch(expected: CatalogRef, actual: CatalogRef)
      extends OathViolation
  case object GameEnded extends OathViolation
  final case class WrongPhase(expected: Phase, actual: Phase)
      extends OathViolation
  final case class UnsupportedWakeVictoryState(reason: String)
      extends OathViolation
  final case class UnsupportedOathkeeperTie(reason: String)
      extends OathViolation
  final case class PawnSiteMissing(playerId: PlayerId)
      extends OathViolation
  final case class ResourceUnavailable(siteId: SiteId, resource: WakeResource)
      extends OathViolation
  final case class EnemyPawnBlocksTakeWealth(
      siteId: SiteId,
      enemies: Vector[PlayerId]
  ) extends OathViolation
  final case class PowerAlreadyUsed(power: PowerUseRef)
      extends OathViolation
  final case class PendingProcedureBlocksAction(decision: DecisionId)
      extends OathViolation
  final case class SameTravelSite(siteId: SiteId)
      extends OathViolation
  final case class TravelPassBlocked(passSiteId: SiteId, destination: SiteId)
      extends OathViolation
  final case class InsufficientSupply(required: Int, available: Int)
      extends OathViolation
  final case class UnsupportedEconomyState(reason: String)
      extends OathViolation
  final case class EconomyCardUnavailable(siteId: SiteId, cardId: CardId)
      extends OathViolation
  final case class EconomyCardNotEmpty(cardId: CardId) extends OathViolation
  /** A start whose first decision offers nothing the procedure would accept. */
  final case class NoPlayableOption(procedure: String) extends OathViolation
  final case class InsufficientFavor(required: Int, available: Int)
      extends OathViolation
  final case class InsufficientSecrets(required: Int, available: Int)
      extends OathViolation
  final case class UnsupportedSearchState(reason: String)
      extends OathViolation
  final case class SearchSourceUnavailable(source: SearchSource)
      extends OathViolation
  final case class SearchDrawMismatch(detail: String)
      extends OathViolation
  final case class SearchDecisionMismatch(expected: DecisionId, actual: DecisionId)
      extends OathViolation
  final case class SearchChoiceMismatch(detail: String)
      extends OathViolation
  final case class UnknownWorldCard(id: WorldCardId)
      extends OathViolation
  final case class InvalidSearchPlacement(detail: String)
      extends OathViolation
  final case class MinorActionUnavailable(detail: String)
      extends OathViolation
  final case class MinorActionOutcomeMismatch(detail: String)
      extends OathViolation
  final case class ConspiracyUnavailable(detail: String) extends OathViolation
  final case class NegotiationUnavailable(detail: String) extends OathViolation
  final case class LockedAdviserCannotBeDiscarded(id: CardId)
      extends OathViolation
  final case class SearchCostMismatch(expected: Int, actual: Int)
      extends OathViolation
  final case class UnsupportedRecoverState(reason: String) extends OathViolation
  final case class RecoverOutcomeMismatch(detail: String) extends OathViolation
  final case class UnsupportedCampaignState(reason: String) extends OathViolation
  final case class CampaignUnavailable(reason: String) extends OathViolation
  final case class CampaignDecisionMismatch(expected: DecisionId, actual: DecisionId)
      extends OathViolation
  final case class CampaignPlanUnavailable(detail: String) extends OathViolation
  final case class CampaignOutcomeMismatch(detail: String) extends OathViolation
  final case class RecoverUnavailable(detail: String) extends OathViolation
  final case class UnsupportedForgeState(reason: String) extends OathViolation
  final case class ForgeUnavailable(detail: String) extends OathViolation
  final case class UnsupportedBannerState(reason: String) extends OathViolation
  final case class UnsupportedRestState(reason: String)
      extends OathViolation
  final case class UnsupportedRoundEndRule(sourceKey: String, handlerId: String)
      extends OathViolation
  final case class UnsupportedRoundEndCatalogInventory(
      expected: String, actual: String) extends OathViolation
  case object ParticipantsEmpty extends OathViolation
  final case class DuplicatePlayer(id: PlayerId)
      extends OathViolation
  final case class DuplicateLineage(id: LineageId)
      extends OathViolation
  final case class DuplicateColor(color: PlayerColor)
      extends OathViolation
  final case class UnknownFirstPlayer(id: PlayerId)
      extends OathViolation
  final case class WrongCount(field: String, expected: Int, actual: Int)
      extends OathViolation
  final case class DuplicateComponent(field: String, id: String)
      extends OathViolation
  final case class UnknownComponent(field: String, id: String)
      extends OathViolation
  final case class WrongDenizenSuitCount(suit: Suit, actual: Int)
      extends OathViolation
  final case class InvalidWorldDeck(detail: String)
      extends OathViolation
  final case class InvalidRelicOrder(detail: String)
      extends OathViolation
  final case class InvalidHomelandEdifice(siteId: SiteId, detail: String)
      extends OathViolation
  final case class WrongPlayer(expected: PlayerId, actual: PlayerId)
      extends OathViolation
  final case class SiteNotInPlay(siteId: SiteId)
      extends OathViolation
  final case class AdviserNotInHand(
      playerId: PlayerId,
      adviserId: DenizenId
  ) extends OathViolation
  final case class InvalidEventOrder(detail: String)
      extends OathViolation
  final case class InvalidAggregate(problems: Vector[DomainProblem])
      extends OathViolation
}
