package oathdigital.application

final case class StoredEventStream(
    gameId: String,
    records: Vector[String]
):
  def nextSequence: Long = records.size.toLong

sealed trait ExpectedStream extends Product with Serializable
object ExpectedStream:
  case object MustNotExist extends ExpectedStream
  final case class AtNextSequence(value: Long) extends ExpectedStream

sealed trait RepositoryFailure extends Product with Serializable
object RepositoryFailure:
  final case class StorageFailure(message: String) extends RepositoryFailure
  final case class InvalidConfiguration(message: String)
      extends RepositoryFailure

sealed trait RepositoryAppendResult extends Product with Serializable
object RepositoryAppendResult:
  final case class Appended(firstSequence: Long, count: Int)
      extends RepositoryAppendResult
  case object StreamAlreadyExists extends RepositoryAppendResult
  case object StreamNotFound extends RepositoryAppendResult
  final case class SequenceConflict(expected: Long, actual: Long)
      extends RepositoryAppendResult

/**
 * Storage-neutral boundary for one authoritative stream per game.
 *
 * Implementations must append the complete `records` vector atomically and
 * compare `expected` in the same transaction. Records are durable wire values,
 * not commands or in-memory domain events.
 */
trait EventStreamRepository:
  def load(
      gameId: String
  ): Either[RepositoryFailure, Option[StoredEventStream]]

  def append(
      gameId: String,
      expected: ExpectedStream,
      records: Vector[String]
  ): Either[RepositoryFailure, RepositoryAppendResult]
