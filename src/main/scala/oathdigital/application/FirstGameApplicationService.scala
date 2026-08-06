package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.{FirstGameRules, TravelCommand, WakeCommand}
import oathdigital.model.{DenizenId, PlayerId, SiteId}
import oathdigital.serialization.{FirstGameEventWire, WireError}
import oathdigital.setup.{
  FirstGameContinue,
  FirstGameSetupCommand,
  FirstGameSetupEvent,
  FirstGameSetupPlan,
  FirstGameSetupRules,
  FirstGameSetupState,
  FirstGameSetupViolation,
  SetupCommand,
  WakeResource
}

sealed trait FirstGameCommand extends Product with Serializable
object FirstGameCommand {
  final case class Begin(plan: FirstGameSetupPlan) extends FirstGameCommand
  final case class PlacePawn(playerId: PlayerId, siteId: SiteId)
      extends FirstGameCommand
  final case class ChooseAdviser(playerId: PlayerId, adviserId: DenizenId)
      extends FirstGameCommand
  final case class TakeWealth(playerId: PlayerId, resource: WakeResource)
      extends FirstGameCommand
  final case class EndWake(playerId: PlayerId) extends FirstGameCommand
  final case class Travel(playerId: PlayerId, destinationSiteId: SiteId)
      extends FirstGameCommand
}

final case class FirstGameAccepted(
    state: FirstGameSetupState,
    events: Vector[FirstGameSetupEvent],
    continue: FirstGameContinue,
    nextSequence: Long
)

final case class LoadedFirstGame(
    state: FirstGameSetupState,
    nextSequence: Long
)

sealed trait FirstGameApplicationError extends Product with Serializable
object FirstGameApplicationError {
  final case class StreamNotFound(gameId: String)
      extends FirstGameApplicationError
  final case class DuplicateGame(gameId: String)
      extends FirstGameApplicationError
  final case class StaleClientPosition(expected: Long, actual: Long)
      extends FirstGameApplicationError
  final case class StreamIdentityMismatch(expected: String, actual: String)
      extends FirstGameApplicationError
  final case class CodecFailure(error: WireError)
      extends FirstGameApplicationError
  final case class ReplayFailure(
      index: Long,
      violation: FirstGameSetupViolation
  ) extends FirstGameApplicationError
  final case class CommandRejected(violation: FirstGameSetupViolation)
      extends FirstGameApplicationError
  final case class BootstrapFailure(message: String)
      extends FirstGameApplicationError
  final case class SequenceConflict(expected: Long, actual: Long)
      extends FirstGameApplicationError
  final case class AppendAcknowledgementMismatch(message: String)
      extends FirstGameApplicationError
  final case class StorageFailure(message: String)
      extends FirstGameApplicationError
}

/**
 * Event-sourced application service for v2 setup plus v3 gameplay streams.
 *
 * V1 envelopes are rejected by `FirstGameEventWire`; no implicit migration is
 * attempted. Strict contiguous v2/v3 replay shares one reconstruction path.
 */
final class FirstGameApplicationService(
    catalog: ExecutableCatalog,
    repository: EventStreamRepository
) {
  import FirstGameApplicationError._
  import RepositoryAppendResult._

  private val setupRules = new FirstGameSetupRules(catalog)
  private val rules = new FirstGameRules(catalog)
  private val replay = new EventReplayEngine(rules)

  def load(
      gameId: String
  ): Either[FirstGameApplicationError, Option[LoadedFirstGame]] =
    repository.load(gameId).left.map(storageError).flatMap {
      case None => Right(None)
      case Some(stream) =>
        reconstruct(gameId, stream).map(state =>
          Some(LoadedFirstGame(state, stream.nextSequence)))
    }

  def handle(
      gameId: String,
      expectedNextSequence: Long,
      command: FirstGameCommand
  ): Either[FirstGameApplicationError, FirstGameAccepted] =
    repository.load(gameId).left.map(storageError).flatMap {
      case None =>
        if (expectedNextSequence != 0L)
          Left(StaleClientPosition(expectedNextSequence, 0L))
        else
          command match {
            case FirstGameCommand.Begin(_) =>
              handleAgainst(
                gameId,
                rules.initialState,
                command,
                ExpectedStream.MustNotExist,
                0L
              )
            case _ =>
              Left(FirstGameApplicationError.StreamNotFound(gameId))
          }
      case Some(stream) =>
        if (expectedNextSequence != stream.nextSequence)
          Left(StaleClientPosition(
            expectedNextSequence,
            stream.nextSequence
          ))
        else
          command match {
            case FirstGameCommand.Begin(_) => Left(DuplicateGame(gameId))
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
  ): Either[FirstGameApplicationError, FirstGameSetupState] =
    for {
      _ <-
        if (stream.gameId == gameId) Right(())
        else Left(StreamIdentityMismatch(gameId, stream.gameId))
      envelopes <- FirstGameEventWire
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
      state: FirstGameSetupState,
      command: FirstGameCommand,
      expected: ExpectedStream,
      nextSequence: Long
  ): Either[FirstGameApplicationError, FirstGameAccepted] =
    for {
      transition <- applyCommand(state, command).left.map(CommandRejected)
      records <- encode(gameId, nextSequence, transition.events)
      result <- repository.append(gameId, expected, records)
        .left.map(storageError)
      accepted <- result match {
        case Appended(first, count)
            if first == nextSequence && count == records.size =>
          Right(FirstGameAccepted(
            transition.state,
            transition.events,
            transition.continue,
            nextSequence + count
          ))
        case StreamAlreadyExists => Left(DuplicateGame(gameId))
        case RepositoryAppendResult.StreamNotFound =>
          Left(FirstGameApplicationError.StreamNotFound(gameId))
        case RepositoryAppendResult.SequenceConflict(wanted, actual) =>
          Left(FirstGameApplicationError.SequenceConflict(wanted, actual))
        case Appended(first, count) =>
          Left(AppendAcknowledgementMismatch(
            s"expected first=$nextSequence count=${records.size}; " +
              s"repository returned first=$first count=$count"
          ))
      }
    } yield accepted

  private def applyCommand(
      state: FirstGameSetupState,
      command: FirstGameCommand
  ) =
    command match {
      case FirstGameCommand.Begin(plan) =>
        setupRules.handle(state, FirstGameSetupCommand.Begin(plan))
      case FirstGameCommand.PlacePawn(playerId, siteId) =>
        setupRules.handle(state, SetupCommand.PlacePawn(playerId, siteId))
      case FirstGameCommand.ChooseAdviser(playerId, adviserId) =>
        setupRules.handle(
          state,
          FirstGameSetupCommand.ChooseAdviser(playerId, adviserId)
        )
      case FirstGameCommand.TakeWealth(playerId, resource) =>
        rules.handle(state, WakeCommand.TakeWealth(playerId, resource))
      case FirstGameCommand.EndWake(playerId) =>
        rules.handle(state, WakeCommand.EndWake(playerId))
      case FirstGameCommand.Travel(playerId, destination) =>
        rules.handle(state, TravelCommand.Travel(playerId, destination))
    }

  private def encode(
      gameId: String,
      firstSequence: Long,
      events: Vector[FirstGameSetupEvent]
  ): Either[FirstGameApplicationError, Vector[String]] =
    events.zipWithIndex.foldLeft[
      Either[FirstGameApplicationError, Vector[String]]
    ](Right(Vector.empty)) {
      case (Right(accumulated), (event, offset)) =>
        FirstGameEventWire
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
  ): FirstGameApplicationError =
    failure match {
      case RepositoryFailure.StorageFailure(message) =>
        StorageFailure(message)
      case RepositoryFailure.InvalidConfiguration(message) =>
        StorageFailure(message)
    }
}
