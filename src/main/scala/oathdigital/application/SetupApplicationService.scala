package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.serialization.{
  SetupEventEnvelope,
  SetupEventWire,
  WireError
}
import oathdigital.setup.{
  SetupCommand,
  SetupContinue,
  SetupEvent,
  SetupRules,
  SetupState,
  SetupViolation
}

final case class SetupCommandAccepted(
    state: SetupState,
    emittedEvents: Vector[SetupEvent],
    continue: SetupContinue,
    nextSequence: Long
)

sealed trait SetupApplicationError extends Product with Serializable
object SetupApplicationError {
  final case class StreamNotFound(gameId: String)
      extends SetupApplicationError
  final case class DuplicateGame(gameId: String)
      extends SetupApplicationError
  final case class RepositoryStreamIdentityMismatch(
      expected: String,
      actual: String
  ) extends SetupApplicationError
  final case class EventStreamIdentityMismatch(
      expected: String,
      actual: String
  ) extends SetupApplicationError
  final case class DecodeFailure(error: WireError)
      extends SetupApplicationError
  final case class EncodeFailure(error: WireError)
      extends SetupApplicationError
  final case class ReplayFailure(index: Long, violation: SetupViolation)
      extends SetupApplicationError
  final case class CommandRejected(violation: SetupViolation)
      extends SetupApplicationError
  final case class SequenceConflict(expected: Long, actual: Long)
      extends SetupApplicationError
  final case class AppendFirstSequenceMismatch(
      expected: Long,
      actual: Long
  ) extends SetupApplicationError
  final case class AppendCountMismatch(expected: Int, actual: Int)
      extends SetupApplicationError
  final case class StorageFailure(message: String)
      extends SetupApplicationError
}

/**
 * Application orchestration for the setup event-sourced aggregate.
 *
 * Commands are transient. Only encoded events cross the repository boundary.
 */
final class SetupApplicationService(
    catalog: ExecutableCatalog,
    repository: EventStreamRepository
) {
  import ExpectedStream._
  import RepositoryAppendResult._
  import SetupApplicationError._
  import oathdigital.setup.SetupCommand.BeginSetup

  private val rules = new SetupRules(catalog)
  private val replay = new EventReplayEngine(rules)

  def handle(
      gameId: String,
      command: SetupCommand
  ): Either[SetupApplicationError, SetupCommandAccepted] =
    repository.load(gameId).left.map(storageError).flatMap {
      case None =>
        command match {
          case _: BeginSetup =>
            handleAgainst(gameId, rules.initialState, command, MustNotExist, 0L)
          case _ => Left(SetupApplicationError.StreamNotFound(gameId))
        }
      case Some(_) if command.isInstanceOf[BeginSetup] =>
        Left(DuplicateGame(gameId))
      case Some(stream) =>
        for {
          _ <-
            if (stream.gameId == gameId) Right(())
            else
              Left(RepositoryStreamIdentityMismatch(gameId, stream.gameId))
          events <- decode(gameId, stream.records)
          state <- replay
            .replay(events)
            .left
            .map(failure => ReplayFailure(failure.index, failure.violation))
          accepted <- handleAgainst(
            gameId,
            state,
            command,
            AtNextSequence(stream.nextSequence),
            stream.nextSequence
          )
        } yield accepted
    }

  private def handleAgainst(
      gameId: String,
      state: SetupState,
      command: SetupCommand,
      expected: ExpectedStream,
      nextSequence: Long
  ): Either[SetupApplicationError, SetupCommandAccepted] =
    for {
      transition <- rules.handle(state, command).left.map(CommandRejected)
      encoded <- encode(gameId, nextSequence, transition.events)
      appendResult <- repository
        .append(gameId, expected, encoded)
        .left
        .map(storageError)
      accepted <- appendResult match {
        case Appended(firstSequence, count)
            if firstSequence == nextSequence &&
              count == transition.events.size =>
          Right(
            SetupCommandAccepted(
              transition.state,
              transition.events,
              transition.continue,
              nextSequence + count
            )
          )
        case StreamAlreadyExists => Left(DuplicateGame(gameId))
        case RepositoryAppendResult.StreamNotFound =>
          Left(SetupApplicationError.StreamNotFound(gameId))
        case RepositoryAppendResult.SequenceConflict(wanted, actual) =>
          Left(SetupApplicationError.SequenceConflict(wanted, actual))
        case Appended(firstSequence, _)
            if firstSequence != nextSequence =>
          Left(AppendFirstSequenceMismatch(nextSequence, firstSequence))
        case Appended(_, count) =>
          Left(AppendCountMismatch(transition.events.size, count))
      }
    } yield accepted

  private def decode(
      gameId: String,
      records: Vector[String]
  ): Either[SetupApplicationError, Vector[RecordedEvent[SetupEvent]]] = {
    val json = records.mkString("[", ",", "]")
    SetupEventWire
      .decodeStream(json)
      .left
      .map(DecodeFailure)
      .flatMap { envelopes =>
        envelopes.find(_.gameId != gameId) match {
          case Some(envelope) =>
            Left(EventStreamIdentityMismatch(gameId, envelope.gameId))
          case None =>
            Right(envelopes.map(envelope =>
              RecordedEvent(envelope.sequence, envelope.event)))
        }
      }
  }

  private def encode(
      gameId: String,
      firstSequence: Long,
      events: Vector[SetupEvent]
  ): Either[SetupApplicationError, Vector[String]] =
    traverse(events.zipWithIndex) { case (event, offset) =>
      val envelope = SetupEventEnvelope(
        SetupEventWire.FormatVersion,
        gameId,
        firstSequence + offset,
        catalog.ref,
        discriminator(event),
        event
      )
      SetupEventWire
        .encode(envelope)
        .left
        .map(EncodeFailure)
        .map(ujson.write(_))
    }

  private def discriminator(event: SetupEvent): String =
    event match {
      case _: SetupEvent.SetupStarted => SetupEventWire.SetupStartedType
      case _: SetupEvent.PawnPlaced => SetupEventWire.PawnPlacedType
      case SetupEvent.SetupCompleted => SetupEventWire.SetupCompletedType
    }

  private def storageError(
      failure: RepositoryFailure
  ): SetupApplicationError =
    failure match {
      case RepositoryFailure.StorageFailure(message) =>
        StorageFailure(message)
    }

  private def traverse[A, B](
      values: Vector[A]
  )(f: A => Either[SetupApplicationError, B])
      : Either[SetupApplicationError, Vector[B]] =
    values.foldLeft[Either[SetupApplicationError, Vector[B]]](
      Right(Vector.empty)
    ) {
      case (Right(accumulated), value) =>
        f(value).map(accumulated :+ _)
      case (failure @ Left(_), _) => failure
    }
}
