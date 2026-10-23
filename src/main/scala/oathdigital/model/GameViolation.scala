package oathdigital.model

import oathdigital.model._

sealed trait OathViolation extends Product with Serializable
object OathViolation:
  final case class CoreOperationRejected(code: String, detail: String)
      extends OathViolation
  /** A required operation a waivable restriction refused (consent design,
    * slice 3). `waiver` names who may lift it and the key a grant is
    * remembered under, so the walker's search can ask about the option
    * whose path it met (catalog batch 8, slice 10). */
  final case class WaivableOperationRejected(code: String, detail: String,
      waiver: OperationWaiver) extends OathViolation
  final case class UnsupportedRuleCatalog(expected: String, actual: String)
      extends OathViolation
  case object GameAlreadyExists extends OathViolation
  case object GameNotStarted extends OathViolation
  case object GameEnded extends OathViolation
  /** No live command batch is left since the latest barrier. */
  case object NothingToUndo extends OathViolation
  /** `player` asked to take back a batch that `issuer` issued. */
  final case class UndoNotIssuer(player: PlayerId, issuer: PlayerId)
      extends OathViolation
  /** Nothing was taken back since the last command batch. */
  case object NothingToRedo extends OathViolation
  /** `player` asked to restore a batch that `issuer` issued. */
  final case class RedoNotIssuer(player: PlayerId, issuer: PlayerId)
      extends OathViolation
  final case class WrongPhase(expected: Phase, actual: Phase)
      extends OathViolation
  final case class UnsupportedWakeVictoryState(reason: String)
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
  /** No way through an action's modifier decision starts it. `reason` is
    * the one its "Start" option shows before any pick (walker-driven game
    * loop design, slice 4, plan-time ruling 1). */
  final case class StartUnavailable(reason: String) extends OathViolation
  final case class InsufficientFavor(required: Int, available: Int)
      extends OathViolation
  final case class InsufficientSecrets(required: Int, available: Int)
      extends OathViolation
  final case class SearchSourceUnavailable(source: SearchSource)
      extends OathViolation
  final case class SearchDrawMismatch(detail: String)
      extends OathViolation
  final case class SearchChoiceMismatch(detail: String)
      extends OathViolation
  final case class UnknownWorldCard(id: WorldCardId)
      extends OathViolation
  final case class UnknownEdifice(id: EdificeId)
      extends OathViolation
  final case class InvalidSearchPlacement(detail: String)
      extends OathViolation
  final case class MinorActionUnavailable(detail: String)
      extends OathViolation
  final case class ConspiracyUnavailable(detail: String) extends OathViolation
  final case class NegotiationUnavailable(detail: String) extends OathViolation
  final case class CampaignUnavailable(reason: String) extends OathViolation
  final case class RecoverUnavailable(detail: String) extends OathViolation
  final case class ForgeUnavailable(detail: String) extends OathViolation
  final case class UnsupportedBannerState(reason: String) extends OathViolation
  final case class UnsupportedRestState(reason: String)
      extends OathViolation
  /** A Chronicle shape Setup does not support yet: a non-empty `world`
    * (needs the Empire), stored denizens or relics on an atlas site (needs
    * placement rules not specified yet), an unknown or duplicate id, or not
    * enough cards to deal (2026-09-21 Chronicle design, slice 2, "Setup
    * from a Chronicle").
    */
  final case class UnsupportedChronicle(reason: String) extends OathViolation
  case object ParticipantsEmpty extends OathViolation
  final case class DuplicatePlayer(id: PlayerId)
      extends OathViolation
  final case class DuplicateLineage(id: LineageId)
      extends OathViolation
  final case class DuplicateColor(color: PlayerColor)
      extends OathViolation
  final case class UnknownFirstPlayer(id: PlayerId)
      extends OathViolation
  final case class WrongPlayer(expected: PlayerId, actual: PlayerId)
      extends OathViolation
  /** A consent request waits on its granters: only their answers, the
    * requester's withdrawal and preference changes run (consent design,
    * "Commands"). */
  case object ConsentPending extends OathViolation
  /** A consent command that cannot apply now, and why. */
  final case class ConsentUnavailable(reason: String) extends OathViolation
  final case class SiteNotInPlay(siteId: SiteId)
      extends OathViolation
  final case class InvalidEventOrder(detail: String)
      extends OathViolation
  final case class InvalidAggregate(problems: Vector[DomainProblem])
      extends OathViolation
