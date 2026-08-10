package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.{EconomyCommand, SearchCommand, SearchRules, TravelCommand}
import oathdigital.gameplay.phases.{RestCommand, WakeCommand}
import oathdigital.model._
import oathdigital.serialization.{GameEventWire, WireError}
import oathdigital.setup.{
  OathContinue,
  FirstGameSetupCommand,
  OathEvent,
  FirstGameSetupPlan,
  FirstGameSetupRules,
  OathState,
  OathViolation,
  SetupCommand,
  TradeResource,
  WakeResource
}

sealed trait GameCommand extends Product with Serializable
object GameCommand {
  final case class Begin(plan: FirstGameSetupPlan) extends GameCommand
  final case class PlacePawn(playerId: PlayerId, siteId: SiteId)
      extends GameCommand
  final case class ChooseAdviser(playerId: PlayerId, adviserId: DenizenId)
      extends GameCommand
  final case class TakeWealth(playerId: PlayerId, resource: WakeResource)
      extends GameCommand
  final case class EndWake(playerId: PlayerId) extends GameCommand
  final case class Travel(playerId: PlayerId, destinationSiteId: SiteId)
      extends GameCommand
  final case class Muster(playerId: PlayerId, target: EconomyTargetRef)
      extends GameCommand
  final case class Trade(playerId: PlayerId, target: EconomyTargetRef,
      resource: TradeResource) extends GameCommand
  final case class BeginSearch(playerId: PlayerId, source: SearchSource)
      extends GameCommand
  final case class CompleteSearch(
      playerId: PlayerId,
      decision: DecisionId,
      kept: WorldCardId,
      discardedInOrder: Vector[WorldCardId],
      placement: SearchPlacement
  ) extends GameCommand
  final case class BeginRest(playerId: PlayerId) extends GameCommand
  final case class FinishRest(playerId: PlayerId) extends GameCommand
}

trait SearchDrawPort {
  def prepare(
      ready: oathdigital.setup.ReadyGame,
      source: SearchSource,
      origin: Region
  ): Either[OathViolation, Vector[WorldCardId]]
}
object SearchDrawPort {
  val authoritative: SearchDrawPort = new SearchDrawPort {
    def prepare(ready: oathdigital.setup.ReadyGame, source: SearchSource,
        origin: Region) = SearchRules.draw(ready, source, origin)
  }
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
  final case class CodecFailure(error: WireError)
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
 * Event-sourced application service for the current mixed v2-v5 game stream.
 *
 * V1 envelopes are rejected by `GameEventWire`; no implicit migration is
 * attempted. Strict contiguous mixed-version replay shares one reconstruction
 * path.
 */
final class GameApplicationService(
    catalog: ExecutableCatalog,
    repository: EventStreamRepository,
    searchDrawPort: SearchDrawPort = SearchDrawPort.authoritative
) {
  import GameApplicationError._
  import RepositoryAppendResult._

  private val setupRules = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)
  private val replay = new EventReplayEngine(rules)

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
      envelopes <- GameEventWire
        .decodeStream(stream.records.mkString("[", ",", "]"))
        .left
        .map(CodecFailure)
      _ <- envelopes.zipWithIndex.collectFirst {
        case (envelope, index) if envelope.sequence != index.toLong =>
          CodecFailure(WireError.InvalidSequence(
            s"$$[$index].sequence",
            index.toLong,
            envelope.sequence
          ))
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
        setupRules.handle(state, SetupCommand.PlacePawn(playerId, siteId))
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
      case GameCommand.CompleteSearch(playerId, decision, kept, discarded,
          placement) =>
        rules.handle(state, SearchCommand.Complete(
          playerId, decision, kept, discarded, placement))
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
        GameEventWire
          .encodeEvent(
            gameId,
            catalog.ref,
            firstSequence + offset.toLong,
            event
          )
          .left
          .map(CodecFailure)
          .map(value => accumulated :+ ujson.write(value))
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
