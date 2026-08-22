package oathdigital.setup

import oathdigital.catalog.{ExecutableCatalog, Suit => CatalogSuit}
import oathdigital.engine.EventEvolution
import oathdigital.model._
import oathdigital.setup.SetupCommand.PlacePawn

final case class PlayerColor(value: String) {
  require(value.trim.nonEmpty, "player color must not be blank")
}

final case class FirstGameParticipant(
    playerId: PlayerId,
    lineageId: LineageId,
    color: PlayerColor
)

final case class FirstGameSetupPlan(
    catalog: CatalogRef,
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId,
    orderedSites: Vector[SiteId],
    denizenOrder: Vector[DenizenId],
    worldDeckOrder: Vector[WorldCardId],
    relicOrder: Vector[RelicId],
    homelandEdifices: Vector[(SiteId, EdificeId)],
    oathkeeperGoal: OathkeeperGoal = OathkeeperGoal.Supremacy
)

sealed trait FirstGameFoundationProfile extends Product with Serializable
object FirstGameFoundationProfile {
  case object FixedUnaltered extends FirstGameFoundationProfile
}

final case class FirstGameSupportState(
    foundationProfile: FirstGameFoundationProfile,
    favorBanks: Map[Suit, Int],
    firstPlayer: PlayerId,
    relicKnowledge: Map[PlayerId, Map[SiteId, Vector[RelicId]]] = Map.empty,
    adviserKnowledge: Map[PlayerId, Vector[WorldCardId]] = Map.empty,
    heldRelicKnowledge: Map[PlayerId, Vector[RelicId]] = Map.empty
)

final case class ReadyGame(
    game: OathGame,
    playerColors: Map[PlayerId, PlayerColor],
    support: FirstGameSupportState
)

sealed trait OathState extends Product with Serializable
object OathState {
  case object NoGame extends OathState

  final case class InProgress(
      plan: FirstGameSetupPlan,
      placements: Vector[PawnPlacement],
      adviserChoices: Vector[(PlayerId, DenizenId)]
  ) extends OathState

  final case class Ready(value: ReadyGame) extends OathState
}

sealed trait FirstGameSetupCommand extends Product with Serializable
object FirstGameSetupCommand {
  final case class Begin(plan: FirstGameSetupPlan)
      extends FirstGameSetupCommand
  final case class ChooseAdviser(playerId: PlayerId, adviserId: DenizenId)
      extends FirstGameSetupCommand
}

sealed trait OathEvent extends Product with Serializable
object OathEvent {
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
      placement: SearchPlacement
  ) extends OathEvent
  final case class RecoverRolled(
      playerId: PlayerId, decision: DecisionId, siteId: SiteId,
      supplySpent: Int, dice: Vector[DefenseDieFace]
  ) extends OathEvent
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
  final case class RestCompleted(
      playerId: PlayerId,
      returnedFavor: Map[Suit, Int],
      returnedSecrets: Int,
      refreshedSupply: Int,
      nextPlayerId: PlayerId,
      nextRound: Int,
      usurperLimited: Boolean
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

sealed trait OathContinue extends Product with Serializable
object OathContinue {
  final case class AwaitingPawn(playerId: PlayerId) extends OathContinue
  final case class AwaitingAdviser(playerId: PlayerId)
      extends OathContinue
  final case class ReadyForFirstTurn(playerId: PlayerId)
      extends OathContinue
  final case class AwaitingWakeAction(playerId: PlayerId)
      extends OathContinue
  final case class ActActionSelection(playerId: PlayerId)
      extends OathContinue
  final case class AwaitingRestAction(playerId: PlayerId)
      extends OathContinue
  final case class AwaitingSearchDecision(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingRecoverRoll(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingRecoverRelic(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingForgeAssignment(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingBannerDecision(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingCampaignSacrifice(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingCampaignPlan(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingCampaignPlacement(playerId: PlayerId, decision: DecisionId)
      extends OathContinue
  final case class AwaitingCampaignRaidRelocation(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
  final case class AwaitingOathkeeperRecipient(playerId: PlayerId,
      decision: DecisionId) extends OathContinue
  final case class GameFinished(winner: PlayerId) extends OathContinue
}

final case class OathTransition(
    state: OathState,
    events: Vector[OathEvent],
    continue: OathContinue
)

sealed trait OathViolation extends Product with Serializable
object OathViolation {
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
  final case class TravelConsentUnsupported(passSiteId: SiteId, ruler: PlayerId)
      extends OathViolation
  final case class InsufficientSupply(required: Int, available: Int)
      extends OathViolation
  final case class TravelSourceMismatch(expected: SiteId, actual: SiteId)
      extends OathViolation
  final case class TravelCostMismatch(expected: Int, actual: Int)
      extends OathViolation
  final case class UnsupportedTravelState(reason: String)
      extends OathViolation
  final case class UnsupportedEconomyState(reason: String)
      extends OathViolation
  final case class EconomyCardUnavailable(siteId: SiteId, cardId: CardId)
      extends OathViolation
  final case class EconomyCardNotEmpty(cardId: CardId) extends OathViolation
  final case class EconomySourceMismatch(detail: String) extends OathViolation
  final case class EconomyOutcomeMismatch(detail: String) extends OathViolation
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
  final case class UnsupportedMinorActionRule(source: CardId, handlers: Vector[String])
      extends OathViolation
  final case class UnsupportedMinorActionCatalogInventory(expected: String, actual: String)
      extends OathViolation
  final case class NegotiationUnavailable(detail: String) extends OathViolation
  final case class NegotiationDecisionMismatch(expected: DecisionId, actual: DecisionId)
      extends OathViolation
  final case class NegotiationOutcomeMismatch(detail: String) extends OathViolation
  final case class UnsupportedNegotiationRule(source: String, handler: String)
      extends OathViolation
  final case class UnsupportedNegotiationCatalogInventory(expected: String, actual: String)
      extends OathViolation
  final case class LockedAdviserCannotBeDiscarded(id: CardId)
      extends OathViolation
  final case class SearchCostMismatch(expected: Int, actual: Int)
      extends OathViolation
  final case class UnsupportedRecoverState(reason: String) extends OathViolation
  final case class RecoverDecisionMismatch(expected: DecisionId, actual: DecisionId)
      extends OathViolation
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
  final case class ForgeDecisionMismatch(expected: DecisionId, actual: DecisionId)
      extends OathViolation
  final case class ForgeOutcomeMismatch(detail: String) extends OathViolation
  final case class UnsupportedBannerState(reason: String) extends OathViolation
  final case class ChallengeUnavailable(detail: String) extends OathViolation
  final case class ChallengeDecisionMismatch(expected: DecisionId, actual: DecisionId)
      extends OathViolation
  final case class ChallengeOutcomeMismatch(detail: String) extends OathViolation
  final case class UnsupportedRestState(reason: String)
      extends OathViolation
  final case class RestOutcomeMismatch(detail: String)
      extends OathViolation
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
  final case class WrongDenizenSuitCount(suit: String, actual: Int)
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

object FirstGameRulesData {
  val visions: Vector[VisionId] = Vector(
    VisionId("vision:vision-of-sanctuary"),
    VisionId("vision:vision-of-rebellion"),
    VisionId("vision:vision-of-faith"),
    VisionId("vision:conspiracy"),
    VisionId("vision:vision-of-conquest")
  )
}

/**
 * CR pp. 6-7 first-game setup with NF p. 8's all-Exile boundary.
 *
 * Every shuffled order is supplied in `FirstGameSetupPlan`; neither command
 * handling nor replay has an RNG.
 */
final class FirstGameSetupRules(catalog: ExecutableCatalog)
    extends EventEvolution[
      OathState,
      OathEvent,
      OathViolation
    ] {
  import OathContinue._
  import FirstGameSetupCommand._
  import OathEvent._
  import OathState._
  import OathViolation._

  override val initialState: OathState = NoGame

  private val sitesById = catalog.sites.map(site => site.id -> site).toMap
  private val denizensById =
    catalog.denizens.map(d => DenizenId(d.id.value) -> d).toMap
  private val relicIds =
    catalog.relics.filter(_.role == oathdigital.catalog.RelicRole.Ordinary)
      .map(r => RelicId(r.id.value))
  private val edificesById =
    catalog.edifices.map(e => EdificeId(e.id.value) -> e).toMap

  def handle(
      state: OathState,
      command: FirstGameSetupCommand
  ): Either[OathViolation, OathTransition] =
    command match {
      case Begin(plan) =>
        state match {
          case NoGame =>
            validatePlan(plan).flatMap { _ =>
              transition(
                state,
                Vector(FirstGameStarted(plan)),
                AwaitingPawn(turnOrder(plan).head.playerId)
              )
            }
          case _ => Left(GameAlreadyExists)
        }
      case ChooseAdviser(playerId, adviserId) =>
        state match {
          case NoGame => Left(GameNotStarted)
          case _: Ready => Left(GameAlreadyReady)
          case progress: InProgress =>
            expectedAdviserPlayer(progress) match {
              case None =>
                Left(InvalidEventOrder("a pawn must be placed first"))
              case Some(expected) if expected != playerId =>
                Left(WrongPlayer(expected, playerId))
              case Some(_) if !handFor(progress.plan, playerId)
                    .contains(adviserId) =>
                Left(AdviserNotInHand(playerId, adviserId))
              case Some(_) =>
                val chosen = StartingAdviserChosen(playerId, adviserId)
                val isFinal =
                  progress.adviserChoices.size + 1 ==
                    progress.plan.participants.size
                val events =
                  if (isFinal) Vector(chosen, FirstGameCompleted)
                  else Vector(chosen)
                val next =
                  if (isFinal)
                    ReadyForFirstTurn(progress.plan.firstPlayer)
                  else {
                    val order = turnOrder(progress.plan)
                    AwaitingPawn(order(progress.adviserChoices.size + 1).playerId)
                  }
                transition(state, events, next)
            }
        }
    }

  /** Reuses the v1 bounded pawn-placement command type unchanged. */
  def handle(
      state: OathState,
      command: PlacePawn
  ): Either[OathViolation, OathTransition] =
    state match {
      case NoGame => Left(GameNotStarted)
      case _: Ready => Left(GameAlreadyReady)
      case progress: InProgress =>
        if (expectedAdviserPlayer(progress).nonEmpty)
          Left(InvalidEventOrder("the current player must choose an adviser"))
        else {
          val order = turnOrder(progress.plan)
          val expected = order(progress.placements.size).playerId
          if (command.playerId != expected)
            Left(WrongPlayer(expected, command.playerId))
          else if (!progress.plan.orderedSites.contains(command.siteId))
            Left(SiteNotInPlay(command.siteId))
          else
            transition(
              state,
              Vector(GamePawnPlaced(command.playerId, command.siteId)),
              AwaitingAdviser(command.playerId)
            )
        }
    }

  override def evolve(
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] =
    event match {
      case FirstGameStarted(plan) =>
        state match {
          case NoGame =>
            validatePlan(plan).map(_ => InProgress(plan, Vector.empty, Vector.empty))
          case _ => Left(GameAlreadyExists)
        }
      case GamePawnPlaced(playerId, siteId) =>
        state match {
          case progress: InProgress
              if expectedAdviserPlayer(progress).isEmpty &&
                progress.placements.size < progress.plan.participants.size =>
            val expected =
              turnOrder(progress.plan)(progress.placements.size).playerId
            if (playerId != expected) Left(WrongPlayer(expected, playerId))
            else if (!progress.plan.orderedSites.contains(siteId))
              Left(SiteNotInPlay(siteId))
            else
              Right(
                progress.copy(
                  placements =
                    progress.placements :+ PawnPlacement(playerId, siteId)
                )
              )
          case _: Ready => Left(GameAlreadyReady)
          case NoGame => Left(GameNotStarted)
          case _ => Left(InvalidEventOrder("unexpected pawn placement"))
        }
      case StartingAdviserChosen(playerId, adviserId) =>
        state match {
          case progress: InProgress =>
            expectedAdviserPlayer(progress) match {
              case Some(expected) if expected == playerId &&
                    handFor(progress.plan, playerId).contains(adviserId) =>
                Right(
                  progress.copy(
                    adviserChoices =
                      progress.adviserChoices :+ (playerId -> adviserId)
                  )
                )
              case Some(expected) if expected != playerId =>
                Left(WrongPlayer(expected, playerId))
              case Some(_) => Left(AdviserNotInHand(playerId, adviserId))
              case None => Left(InvalidEventOrder("unexpected adviser choice"))
            }
          case _: Ready => Left(GameAlreadyReady)
          case NoGame => Left(GameNotStarted)
        }
      case FirstGameCompleted =>
        state match {
          case progress: InProgress
              if progress.placements.size == progress.plan.participants.size &&
                progress.adviserChoices.size ==
                  progress.plan.participants.size =>
            buildReady(progress).map(Ready)
          case _: Ready => Left(GameAlreadyReady)
          case NoGame => Left(GameNotStarted)
          case _ => Left(InvalidEventOrder("setup is incomplete"))
        }
      case _: Traveled =>
        Left(InvalidEventOrder("Travel requires the gameplay evolution"))
      case _: Mustered | _: Traded =>
        Left(InvalidEventOrder("Economy requires the gameplay evolution"))
      case _: WealthTaken | _: WakeEnded =>
        Left(InvalidEventOrder("gameplay event cannot be applied by setup rules"))
      case _: SearchStarted | _: SearchCompleted =>
        Left(InvalidEventOrder("Search requires the gameplay evolution"))
      case _: RestStarted | _: RestCompleted =>
        Left(InvalidEventOrder("Rest requires the gameplay evolution"))
      case _: RecoverRolled | _: RecoverStopped | _: RelicRecovered |
          _: ForgeStarted | _: ForgeCompleted |
          _: BannerChallengeStarted | _: BannerRibbonChoiceMade |
          _: BannerChallengeCompleted | _: BannerResourcePlaced |
          _: FacedownAdviserDiscarded | _: FacedownAdviserPlayed |
          _: SiteRelicsPeeked | _: OwnedRelicRevealed | _: WarbandsMoved |
          _: NegotiationStarted | _: NegotiationTermsReplaced |
          _: NegotiationAccepted | _: NegotiationDeclined | _: NegotiationCompleted |
          _: CampaignStarted | _: CampaignPlanChosen | _: CampaignPlansFinished | _: CampaignSacrificed | _: CampaignConquered |
          _: CampaignRaided | _: CampaignRaidPawnRelocated |
          _: BanditsRefilled =>
        Left(InvalidEventOrder("Recover requires the gameplay evolution"))
      case _: OathkeeperChanged | _: OathkeeperRecipientChoiceStarted |
          _: OathkeeperRecipientChosen | _: UsurperFlipped |
          _: UsurperVictory =>
        Left(InvalidEventOrder("state-based checks require gameplay evolution"))
    }

  private def validatePlan(
      plan: FirstGameSetupPlan
  ): Either[OathViolation, Unit] = {
    def duplicate[A](values: Vector[A]): Option[A] = {
      val seen = scala.collection.mutable.HashSet.empty[A]
      values.find(value => !seen.add(value))
    }
    val playerIds = plan.participants.map(_.playerId)
    val lineageIds = plan.participants.map(_.lineageId)
    val colors = plan.participants.map(_.color)
    val siteDuplicate = duplicate(plan.orderedSites)
    val denizenDuplicate = duplicate(plan.denizenOrder)
    val relicDuplicate = duplicate(plan.relicOrder)

    if (plan.catalog != catalog.ref)
      Left(CatalogMismatch(catalog.ref, plan.catalog))
    else if (plan.participants.isEmpty) Left(ParticipantsEmpty)
    else if (duplicate(playerIds).nonEmpty)
      Left(DuplicatePlayer(duplicate(playerIds).get))
    else if (duplicate(lineageIds).nonEmpty)
      Left(DuplicateLineage(duplicate(lineageIds).get))
    else if (duplicate(colors).nonEmpty)
      Left(DuplicateColor(duplicate(colors).get))
    else if (!playerIds.contains(plan.firstPlayer))
      Left(UnknownFirstPlayer(plan.firstPlayer))
    else if (plan.orderedSites.size != 8)
      Left(WrongCount("orderedSites", 8, plan.orderedSites.size))
    else if (siteDuplicate.nonEmpty)
      Left(DuplicateComponent("orderedSites", siteDuplicate.get.value))
    else if (plan.orderedSites.exists(id => !sitesById.contains(id)))
      Left(UnknownComponent(
        "orderedSites",
        plan.orderedSites.find(id => !sitesById.contains(id)).get.value
      ))
    else if (plan.denizenOrder.size != 60)
      Left(WrongCount("denizenOrder", 60, plan.denizenOrder.size))
    else if (denizenDuplicate.nonEmpty)
      Left(DuplicateComponent("denizenOrder", denizenDuplicate.get.value))
    else if (plan.denizenOrder.exists(id => !denizensById.contains(id)))
      Left(UnknownComponent(
        "denizenOrder",
        plan.denizenOrder.find(id => !denizensById.contains(id)).get.value
      ))
    else
      validateSuitCounts(plan)
        .flatMap(_ => validateWorldDeck(plan))
        .flatMap(_ => validateRelics(plan, relicDuplicate))
        .flatMap(_ => validateHomelands(plan))
  }

  private def validateSuitCounts(
      plan: FirstGameSetupPlan
  ): Either[OathViolation, Unit] =
    CatalogSuit.values.toVector.sorted
      .collectFirst {
        case suit
            if plan.denizenOrder.count(
              id => denizensById(id).suit.value == suit
            ) != 10 =>
          val actual = plan.denizenOrder.count(
            id => denizensById(id).suit.value == suit
          )
          WrongDenizenSuitCount(suit, actual)
      }
      .toLeft(())

  private def validateWorldDeck(
      plan: FirstGameSetupPlan
  ): Either[OathViolation, Unit] = {
    val dealt = 6 + plan.participants.size * 3
    val remaining = plan.denizenOrder.drop(dealt).toSet
    val deckDenizens = plan.worldDeckOrder.collect {
      case id: DenizenId => id
    }
    val deckVisions = plan.worldDeckOrder.collect {
      case id: VisionId => id
    }
    val first = plan.worldDeckOrder.take(12)
    val second = plan.worldDeckOrder.slice(12, 30)
    def counts(cards: Vector[WorldCardId]) =
      cards.count(_.isInstanceOf[DenizenId]) ->
        cards.count(_.isInstanceOf[VisionId])

    if (dealt > plan.denizenOrder.size)
      Left(InvalidWorldDeck("too many player hands for the 60-card pool"))
    else if (deckDenizens.size != deckDenizens.distinct.size)
      Left(InvalidWorldDeck("denizens must be unique"))
    else if (deckDenizens.toSet != remaining)
      Left(InvalidWorldDeck("denizens must exactly equal the undealt pool"))
    else if (
      deckVisions.size != FirstGameRulesData.visions.size ||
      deckVisions.distinct.size != deckVisions.size ||
      deckVisions.toSet != FirstGameRulesData.visions.toSet
    )
      Left(InvalidWorldDeck("the five fixed Vision identities are required"))
    else if (counts(first) != (10 -> 2))
      Left(InvalidWorldDeck("top packet must contain 10 denizens and 2 Visions"))
    else if (counts(second) != (15 -> 3))
      Left(InvalidWorldDeck("second packet must contain 15 denizens and 3 Visions"))
    else Right(())
  }

  private def validateRelics(
      plan: FirstGameSetupPlan,
      duplicate: Option[RelicId]
  ): Either[OathViolation, Unit] =
    if (duplicate.nonEmpty)
      Left(DuplicateComponent("relicOrder", duplicate.get.value))
    else if (plan.relicOrder.toSet != relicIds.toSet)
      Left(InvalidRelicOrder(
        "order must contain every ordinary relic exactly once and no Grand Scepter"
      ))
    else Right(())

  private def validateHomelands(
      plan: FirstGameSetupPlan
  ): Either[OathViolation, Unit] = {
    val entries = plan.homelandEdifices
    val duplicates = entries.groupBy(_._1).collectFirst {
      case (site, values) if values.size > 1 => site
    }
    val required = plan.orderedSites.filter(homelandSuit(_).nonEmpty)
    if (duplicates.nonEmpty)
      Left(InvalidHomelandEdifice(duplicates.get, "duplicate assignment"))
    else if (entries.map(_._1).toSet != required.toSet)
      Left(InvalidHomelandEdifice(
        required.find(id => !entries.map(_._1).contains(id))
          .orElse(entries.map(_._1).find(id => !required.contains(id)))
          .getOrElse(plan.orderedSites.head),
        "assign exactly one edifice to each selected Homeland"
      ))
    else
      entries.collectFirst {
        case (site, edificeId) if !edificesById.contains(edificeId) =>
          InvalidHomelandEdifice(site, s"unknown edifice ${edificeId.value}")
        case (site, edificeId)
            if edificesById(edificeId).suit.value != homelandSuit(site).get =>
          InvalidHomelandEdifice(site, "edifice suit does not match Homeland")
      }.toLeft(())
  }

  private def homelandSuit(siteId: SiteId): Option[String] =
    sitesById(siteId).handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }

  private def turnOrder(
      plan: FirstGameSetupPlan
  ): Vector[FirstGameParticipant] = {
    val start = plan.participants.indexWhere(_.playerId == plan.firstPlayer)
    plan.participants.drop(start) ++ plan.participants.take(start)
  }

  private def handFor(
      plan: FirstGameSetupPlan,
      playerId: PlayerId
  ): Vector[DenizenId] = {
    val index = plan.participants.indexWhere(_.playerId == playerId)
    plan.denizenOrder.slice(6 + index * 3, 9 + index * 3)
  }

  private def expectedAdviserPlayer(
      progress: InProgress
  ): Option[PlayerId] =
    if (progress.placements.size == progress.adviserChoices.size + 1)
      Some(turnOrder(progress.plan)(progress.adviserChoices.size).playerId)
    else None

  private def transition(
      state: OathState,
      events: Vector[OathEvent],
      continue: OathContinue
  ): Either[OathViolation, OathTransition] =
    events
      .foldLeft[Either[OathViolation, OathState]](
        Right(state)
      ) {
        case (Right(current), event) => evolve(current, event)
        case (failure @ Left(_), _) => failure
      }
      .map(OathTransition(_, events, continue))

  private def buildReady(
      progress: InProgress
  ): Either[OathViolation, ReadyGame] = {
    val plan = progress.plan
    val placementMap = progress.placements.map(p => p.playerId -> p.siteId).toMap
    val adviserMap = progress.adviserChoices.toMap
    val relicsBySite = assignRelics(plan)
    val edifices = plan.homelandEdifices.toMap
    val map = MapState(
      plan.orderedSites.take(2),
      plan.orderedSites.slice(2, 5),
      plan.orderedSites.slice(5, 8),
      plan.orderedSites.map { siteId =>
        val definition = sitesById(siteId)
        val forces =
          if (definition.capacity == 0) SiteForces.Empty
          else SiteForces.Occupied(ForceKind.Bandit, definition.capacity)
        val denizens = edifices.get(siteId).toVector.map { id =>
          EdificeState(id, EdificeSide.Ruined, Tokens.empty)
        }
        val relics = relicsBySite.getOrElse(siteId, Vector.empty).map { id =>
          RelicState(id, Orientation.FaceDown, Tokens.empty)
        }
        siteId -> SiteState(
          forces,
          denizens,
          relics,
          definition.startingResources
        )
      }.toMap
    )
    val regionalDiscards = initialDiscards(plan, placementMap, adviserMap)
    val usedRelics = relicsBySite.valuesIterator.flatten.toSet
    val players = plan.participants.map { participant =>
      PlayerState(
        participant.playerId,
        participant.lineageId,
        Some(placementMap(participant.playerId)),
        PlayerBoardState(1, 1, 0, 3, SupplyTrack.full),
        Vector(
          DenizenState(
            adviserMap(participant.playerId),
            Orientation.FaceDown,
            Tokens.empty
          )
        ),
        Vector.empty,
        None
      )
    }
    val lineages = plan.participants.map { participant =>
      participant.lineageId -> LineageState(
        participant.lineageId,
        None,
        Role.Exile,
        Vector.empty,
        Vector.empty
      )
    }.toMap
    val foundations = FoundationNumber.all.map { number =>
      number -> FoundationState(FoundationFace.Normal, Set.empty)
    }.toMap
    val game = OathGame(
      plan.catalog,
      CampaignState(
        AtlasState(Vector.empty),
        foundations,
        lineages,
        Vector.empty,
        Vector.empty,
        Map.empty,
        plan.oathkeeperGoal,
        EraState(20, lineages.keys.map(_ -> 0).toMap)
      ),
      CurrentGameState(
        players,
        map,
        CardZones(
          plan.worldDeckOrder,
          plan.relicOrder.filterNot(usedRelics),
          catalog.edifices.map(e => EdificeId(e.id.value))
            .filterNot(edifices.values.toSet),
          Vector.empty,
          regionalDiscards
        ),
        BannersState(
          PeoplesFavorState(PeoplesFavorFace.Mob, None, 1),
          DarkestSecretState(DarkestSecretFace.WanderingFlame, None, 1)
        ),
        OathkeeperState(None, TitleSide.Oathkeeper),
        TurnState(plan.firstPlayer, Phase.Wake, Set.empty),
        GameTracks(1, 0, usurperLimited = true),
        None,
        None
      )
    )
    val problems = DomainValidation.validate(game)
    if (problems.nonEmpty) Left(InvalidAggregate(problems))
    else
      Right(
        ReadyGame(
          game,
          plan.participants.map(p => p.playerId -> p.color).toMap,
          FirstGameSupportState(
            FirstGameFoundationProfile.FixedUnaltered,
            favorBanks(plan),
            plan.firstPlayer
          )
        )
      )
  }

  private def assignRelics(
      plan: FirstGameSetupPlan
  ): Map[SiteId, Vector[RelicId]] = {
    var offset = 0
    plan.orderedSites.map { site =>
      val count = sitesById(site).relicSlots
      val assigned = plan.relicOrder.slice(offset, offset + count)
      offset += count
      site -> assigned
    }.toMap
  }

  private def initialDiscards(
      plan: FirstGameSetupPlan,
      placements: Map[PlayerId, SiteId],
      choices: Map[PlayerId, DenizenId]
  ): Map[Region, Vector[WorldCardId]] = {
    val seeded = Map[Region, Vector[WorldCardId]](
      Region.Cradle -> plan.denizenOrder.slice(0, 2),
      Region.Provinces -> plan.denizenOrder.slice(2, 4),
      Region.Hinterland -> plan.denizenOrder.slice(4, 6)
    )
    plan.participants.foldLeft(seeded) { (discards, participant) =>
      val rejected =
        handFor(plan, participant.playerId).filterNot(
          _ == choices(participant.playerId)
        )
      val destination =
        regionOf(plan, placements(participant.playerId)) match {
          case Region.Cradle => Region.Provinces
          case Region.Provinces => Region.Hinterland
          case Region.Hinterland => Region.Cradle
        }
      discards.updated(destination, discards(destination) ++ rejected)
    }
  }

  private def regionOf(plan: FirstGameSetupPlan, site: SiteId): Region =
    if (plan.orderedSites.take(2).contains(site)) Region.Cradle
    else if (plan.orderedSites.slice(2, 5).contains(site)) Region.Provinces
    else Region.Hinterland

  private def favorBanks(plan: FirstGameSetupPlan): Map[Suit, Int] = {
    val bonus = if (plan.participants.size >= 5) 1 else 0
    val edificeSuits = plan.homelandEdifices.map { case (_, id) =>
      modelSuit(edificesById(id).suit.value)
    }
    Suit.all.map { suit =>
      suit -> (3 + bonus + edificeSuits.count(_ == suit))
    }.toMap
  }

  private def modelSuit(value: String): Suit =
    Suit.all.find(_.key == value).get
}
