package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.{OathContinue, OathEvent, OathRules, OathState,
  OathViolation}
import oathdigital.gameplay.actions.{Campaign, CampaignCommand, CampaignRules, ChallengeCommand,
  EconomyCommand, Forge, ForgeCommand, RecoverCommand, SearchCommand, TravelCommand}
import oathdigital.gameplay.actions.MinorActionCommand
import oathdigital.gameplay.actions.VisionCommand
import oathdigital.gameplay.actions.NegotiationCommand
import oathdigital.gameplay.phases.{RestCommand, WakeCommand}
import oathdigital.gameplay.phases.WarExhaustionRandomPort
import oathdigital.model._
import oathdigital.gameplay.setup.{
  FirstGameSetupCommand,
  FirstGameSetupRules
}


final case class GameAccepted(
    state: OathState,
    events: Vector[OathEvent],
    continue: OathContinue,
    nextSequence: Long
)

final case class LoadedGame(
    state: OathState,
    nextSequence: Long
)

sealed trait GameApplicationError extends Product with Serializable
object GameApplicationError {
  final case class StreamNotFound(gameId: String)
      extends GameApplicationError
  final case class DuplicateGame(gameId: String)
      extends GameApplicationError
  final case class StaleClientPosition(expected: Long, actual: Long)
      extends GameApplicationError
  final case class StreamIdentityMismatch(expected: String, actual: String)
      extends GameApplicationError
  final case class CodecFailure(error: EventCodecFailure)
      extends GameApplicationError
  final case class ReplayFailure(
      index: Long,
      violation: OathViolation
  ) extends GameApplicationError
  final case class CommandRejected(violation: OathViolation)
      extends GameApplicationError
  final case class BootstrapFailure(message: String)
      extends GameApplicationError
  final case class SequenceConflict(expected: Long, actual: Long)
      extends GameApplicationError
  final case class AppendAcknowledgementMismatch(message: String)
      extends GameApplicationError
  final case class StorageFailure(message: String)
      extends GameApplicationError
}

/**
 * Event-sourced application service for the single current v1 game stream.
 */
final class GameApplicationService(
    catalog: ExecutableCatalog,
    repository: EventStreamRepository,
    searchDrawPort: SearchDrawPort = SearchDrawPort.authoritative,
    relicDrawPort: RelicDrawPort = RelicDrawPort.authoritative,
    defenseDicePort: DefenseDicePort = DefenseDicePort.random,
    campaignDicePort: CampaignDicePort = CampaignDicePort.random,
    warExhaustionRandomPort: WarExhaustionRandomPort =
      WarExhaustionRandomPort.random,
    eventCodec: GameEventCodec = GameEventCodec.default
) {
  import GameApplicationError._
  import RepositoryAppendResult._

  private val setupRules = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog,
    warExhaustionRandomPort = warExhaustionRandomPort)
  private val replay = new EventReplayEngine(rules)

  /** Privileged development support. Never include this in a player projection. */
  def rawEventHistory(
      gameId: String,
      limit: Int
  ): Either[GameApplicationError, Vector[String]] =
    if (limit < 1 || limit > 100)
      Left(BootstrapFailure("event history limit must be between 1 and 100"))
    else repository.load(gameId).left.map(storageError).flatMap {
      case None => Left(GameApplicationError.StreamNotFound(gameId))
      case Some(stream) => Right(stream.records.takeRight(limit))
    }

  def load(
      gameId: String
  ): Either[GameApplicationError, Option[LoadedGame]] =
    repository.load(gameId).left.map(storageError).flatMap {
      case None => Right(None)
      case Some(stream) =>
        reconstruct(gameId, stream).map(state =>
          Some(LoadedGame(state, stream.nextSequence)))
    }

  def handle(
      gameId: String,
      expectedNextSequence: Long,
      command: GameCommand
  ): Either[GameApplicationError, GameAccepted] =
    repository.load(gameId).left.map(storageError).flatMap {
      case None =>
        if (expectedNextSequence != 0L)
          Left(StaleClientPosition(expectedNextSequence, 0L))
        else
          command match {
            case GameCommand.Begin(_) =>
              handleAgainst(
                gameId,
                rules.initialState,
                command,
                ExpectedStream.MustNotExist,
                0L
              )
            case _ =>
              Left(GameApplicationError.StreamNotFound(gameId))
          }
      case Some(stream) =>
        if (expectedNextSequence != stream.nextSequence)
          Left(StaleClientPosition(
            expectedNextSequence,
            stream.nextSequence
          ))
        else
          command match {
            case GameCommand.Begin(_) => Left(DuplicateGame(gameId))
            case _ =>
              reconstruct(gameId, stream).flatMap { state =>
                handleAgainst(
                  gameId,
                  state,
                  command,
                  ExpectedStream.AtNextSequence(stream.nextSequence),
                  stream.nextSequence
                )
              }
          }
    }

  private def reconstruct(
      gameId: String,
      stream: StoredEventStream
  ): Either[GameApplicationError, OathState] =
    for {
      _ <-
        if (stream.gameId == gameId) Right(())
        else Left(StreamIdentityMismatch(gameId, stream.gameId))
      envelopes <- eventCodec
        .decodeStream(stream.records.mkString("[", ",", "]"))
        .left
        .map(CodecFailure)
      _ <- envelopes.zipWithIndex.collectFirst {
        case (envelope, index) if envelope.sequence != index.toLong =>
          CodecFailure(EventCodecFailure("invalid-sequence",
            s"$$[$index].sequence",
            s"expected $index but found ${envelope.sequence}"))
        case (envelope, _) if envelope.gameId != gameId =>
          StreamIdentityMismatch(gameId, envelope.gameId)
      }.toLeft(())
      state <- replay
        .replay(envelopes.map(envelope =>
          RecordedEvent(envelope.sequence, envelope.event)))
        .left
        .map(failure => ReplayFailure(failure.index, failure.violation))
    } yield state

  private def handleAgainst(
      gameId: String,
      state: OathState,
      command: GameCommand,
      expected: ExpectedStream,
      nextSequence: Long
  ): Either[GameApplicationError, GameAccepted] =
    for {
      transition <- applyCommand(state, command, nextSequence).left.map(CommandRejected)
      records <- encode(gameId, nextSequence, transition.events)
      result <- repository.append(gameId, expected, records)
        .left.map(storageError)
      accepted <- result match {
        case Appended(first, count)
            if first == nextSequence && count == records.size =>
          Right(GameAccepted(
            transition.state,
            transition.events,
            transition.continue,
            nextSequence + count
          ))
        case StreamAlreadyExists => Left(DuplicateGame(gameId))
        case RepositoryAppendResult.StreamNotFound =>
          Left(GameApplicationError.StreamNotFound(gameId))
        case RepositoryAppendResult.SequenceConflict(wanted, actual) =>
          Left(GameApplicationError.SequenceConflict(wanted, actual))
        case Appended(first, count) =>
          Left(AppendAcknowledgementMismatch(
            s"expected first=$nextSequence count=${records.size}; " +
              s"repository returned first=$first count=$count"
          ))
      }
    } yield accepted

  private def applyCommand(
      state: OathState,
      command: GameCommand,
      nextSequence: Long
  ) =
    command match {
      case GameCommand.Begin(plan) =>
        setupRules.handle(state, FirstGameSetupCommand.Begin(plan))
      case GameCommand.PlacePawn(playerId, siteId) =>
        setupRules.handle(state, FirstGameSetupCommand.PlacePawn(playerId, siteId))
      case GameCommand.ChooseAdviser(playerId, adviserId) =>
        setupRules.handle(
          state,
          FirstGameSetupCommand.ChooseAdviser(playerId, adviserId)
        )
      case GameCommand.TakeWealth(playerId, resource) =>
        rules.handle(state, WakeCommand.TakeWealth(playerId, resource))
      case GameCommand.EndWake(playerId) =>
        rules.handle(state, WakeCommand.EndWake(playerId))
      case GameCommand.Travel(playerId, destination) =>
        rules.handle(state, TravelCommand.Travel(playerId, destination))
      case GameCommand.Muster(playerId, target) =>
        rules.handle(state, EconomyCommand.Muster(playerId, target))
      case GameCommand.Trade(playerId, target, resource) =>
        rules.handle(state, EconomyCommand.Trade(playerId, target, resource))
      case GameCommand.BeginSearch(playerId, source) => state match {
        case OathState.Ready(ready) =>
          for {
            region <- ready.game.current.players.find(_.player == playerId)
              .flatMap(_.pawnSite).flatMap(ready.game.current.map.regionOf)
              .toRight(OathViolation.PawnSiteMissing(playerId))
            drawn <- searchDrawPort.prepare(ready, source, region)
            result <- rules.handle(state, SearchCommand.Start(
              playerId, DecisionId(s"search-$nextSequence"), source, drawn))
          } yield result
        case _ => Left(OathViolation.GameNotStarted)
      }
      case GameCommand.BeginRecover(playerId) =>
        rules.handle(state, RecoverCommand.Roll(playerId,
          DecisionId(s"recover-$nextSequence"), defenseDicePort.rollTwo()))
      case GameCommand.BeginForge(playerId) =>
        rules.handle(state, ForgeCommand.Begin(playerId,
          DecisionId(s"forge-$nextSequence")))
      case GameCommand.CompleteForge(playerId, decision, assignments) => state match {
        case OathState.Ready(ready) =>
          Forge.prepareComplete(catalog, state, playerId, decision, assignments)
            .flatMap(_ => relicDrawPort.prepare(ready)).flatMap(relic =>
              rules.handle(state, ForgeCommand.Complete(playerId, decision,
                assignments, relic)))
        case _ => Left(OathViolation.GameNotStarted)
      }
      case GameCommand.BeginChallenge(playerId, banner) =>
        rules.handle(state, ChallengeCommand.Begin(playerId,
          DecisionId(s"challenge-$nextSequence"), banner))
      case GameCommand.ChooseChallengeSecretSite(playerId, decision, site) =>
        rules.handle(state, ChallengeCommand.ChooseSecretSite(playerId, decision, site))
      case GameCommand.CompleteChallenge(playerId, decision, amount) =>
        rules.handle(state, ChallengeCommand.Complete(playerId, decision, amount))
      case GameCommand.PlaceBannerResource(playerId, banner, amount) =>
        rules.handle(state, ChallengeCommand.PlaceResource(playerId, banner, amount))
      case GameCommand.DiscardFacedownAdviser(playerId, adviser) =>
        rules.handle(state, MinorActionCommand.DiscardFacedownAdviser(playerId, adviser))
      case GameCommand.PlayFacedownAdviser(playerId, adviser, placement) =>
        rules.handle(state, MinorActionCommand.PlayFacedownAdviser(
          playerId, adviser, placement))
      case GameCommand.PeekSiteRelics(playerId) =>
        rules.handle(state, MinorActionCommand.PeekSiteRelics(playerId))
      case GameCommand.RevealOwnedRelic(playerId, relic) =>
        rules.handle(state, MinorActionCommand.RevealOwnedRelic(playerId, relic))
      case GameCommand.MoveWarbands(playerId, toSite, amount) =>
        rules.handle(state, MinorActionCommand.MoveWarbands(playerId, toSite, amount))
      case GameCommand.RevealVision(playerId, visionId) =>
        rules.handle(state, VisionCommand.Reveal(playerId, visionId))
      case GameCommand.PlayConspiracy(playerId, target) =>
        rules.handle(state, VisionCommand.PlayConspiracy(playerId,
          state match {
            case OathState.Ready(ready) => ready.game.current.pending.collect {
              case p: PendingProcedure.Conspiracy if p.awaitingTarget => p.decision
            }.getOrElse(DecisionId(s"conspiracy-$nextSequence"))
            case _ => DecisionId(s"conspiracy-$nextSequence")
          }, target))
      case GameCommand.ChooseConspiracySecretSite(playerId, decision, siteId) =>
        rules.handle(state, VisionCommand.ChooseSecretSite(playerId, decision, siteId))
      case GameCommand.BeginNegotiation(playerId, participants) =>
        rules.handle(state, NegotiationCommand.Begin(playerId,
          DecisionId(s"negotiation-$nextSequence"), participants))
      case GameCommand.ReplaceNegotiationTerms(playerId, decision, terms) =>
        rules.handle(state, NegotiationCommand.ReplaceTerms(playerId, decision, terms))
      case GameCommand.AcceptNegotiation(playerId, decision) =>
        rules.handle(state, NegotiationCommand.Accept(playerId, decision))
      case GameCommand.DeclineNegotiation(playerId, decision) =>
        rules.handle(state, NegotiationCommand.Decline(playerId, decision))
      case GameCommand.AddRecoverDice(playerId, decision) =>
        rules.handle(state, RecoverCommand.Roll(playerId, decision,
          defenseDicePort.rollTwo()))
      case GameCommand.StopRecover(playerId, decision) =>
        rules.handle(state, RecoverCommand.Stop(playerId, decision))
      case GameCommand.BeginCampaignConquest(playerId, targets, count) =>
        rules.handle(state, CampaignCommand.Start(playerId,
          DecisionId(s"campaign-$nextSequence"), targets, count))
      case GameCommand.BeginCampaignRaid(playerId, targets, count) =>
        rules.handle(state, CampaignCommand.StartRaid(playerId,
          DecisionId(s"campaign-$nextSequence"), targets, count))
      case GameCommand.ChooseCampaignPlan(playerId, decision, source) =>
        rules.handle(state, CampaignCommand.ChoosePlan(playerId, decision, source))
      case GameCommand.FinishCampaignPlans(playerId, decision) =>
        Campaign.prepareFinishPlans(catalog, state, playerId, decision).flatMap { count =>
          rules.handle(state, CampaignCommand.FinishPlans(playerId, decision,
            count.fold(Vector.empty[AttackDieFace])(campaignDicePort.rollAttack)))
        }
      case GameCommand.ChooseCampaignSacrifice(playerId, decision, count) => state match {
        case OathState.Ready(ready) => ready.game.current.pending match {
          case Some(c: PendingProcedure.Campaign) =>
            val defenseCount = CampaignRules.defenseDiceCount(catalog, ready, c)
            rules.handle(state, CampaignCommand.Sacrifice(playerId, decision, count,
              campaignDicePort.rollDefense(defenseCount)))
          case _ => rules.handle(state, CampaignCommand.Sacrifice(playerId, decision,
            count, Vector.empty))
        }
        case _ => rules.handle(state, CampaignCommand.Sacrifice(playerId, decision,
          count, Vector.empty))
      }
      case GameCommand.PlaceCampaignForce(playerId, decision, allocations) =>
        rules.handle(state, CampaignCommand.Place(playerId, decision, allocations))
      case GameCommand.RelocateCampaignRaidPawn(playerId, decision, destination) =>
        rules.handle(state, CampaignCommand.RelocateRaidPawn(
          playerId, decision, destination))
      case GameCommand.ChooseOathkeeperRecipient(playerId, decision, recipient) =>
        rules.chooseOathkeeperRecipient(state, playerId, decision, recipient)
      case GameCommand.CompleteSearch(playerId, decision, kept, discarded,
          placement) =>
        rules.handle(state, SearchCommand.Complete(
          playerId, decision, kept, discarded, placement))
      case GameCommand.ResolveCardDecision(playerId, decision, resolution) =>
        resolution match {
          case CardDecisionResolution.StartingAdviser(adviserId) => state match {
            case progress: OathState.InProgress =>
              val expected = CardDecisionIds.startingAdviser(
                playerId, progress.adviserChoices.size)
              if (decision != expected)
                Left(OathViolation.InvalidEventOrder(
                  "stale or incorrect starting-adviser decision ID"))
              else setupRules.handle(state,
                FirstGameSetupCommand.ChooseAdviser(playerId, adviserId))
            case _ => Left(OathViolation.InvalidEventOrder(
              "starting-adviser resolution has the wrong decision kind"))
          }
          case CardDecisionResolution.Search(kept, discarded, placement) =>
            rules.handle(state, SearchCommand.Complete(
              playerId, decision, kept, discarded, placement))
          case CardDecisionResolution.TakeFacedownRelic(relicId) =>
            rules.handle(state, RecoverCommand.TakeRelic(
              playerId, decision, relicId))
        }
      case GameCommand.BeginRest(playerId) =>
        rules.handle(state, RestCommand.Begin(playerId))
      case GameCommand.FinishRest(playerId) =>
        rules.handle(state, RestCommand.Finish(playerId))
    }

  private def encode(
      gameId: String,
      firstSequence: Long,
      events: Vector[OathEvent]
  ): Either[GameApplicationError, Vector[String]] =
    events.zipWithIndex.foldLeft[
      Either[GameApplicationError, Vector[String]]
    ](Right(Vector.empty)) {
      case (Right(accumulated), (event, offset)) =>
        eventCodec
          .encodeEvent(
            gameId,
            catalog.ref,
            firstSequence + offset.toLong,
            event
          )
          .left
          .map(CodecFailure)
          .map(value => accumulated :+ value)
      case (failure @ Left(_), _) => failure
    }

  private def storageError(
      failure: RepositoryFailure
  ): GameApplicationError =
    failure match {
      case RepositoryFailure.StorageFailure(message) =>
        StorageFailure(message)
      case RepositoryFailure.InvalidConfiguration(message) =>
        StorageFailure(message)
    }
}
