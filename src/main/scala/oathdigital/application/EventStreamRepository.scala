package oathdigital.application

final case class StoredEventStream(
    gameId: String,
    records: Vector[String]
) {
  def nextSequence: Long = records.size.toLong
}

sealed trait ExpectedStream extends Product with Serializable
object ExpectedStream {
  case object MustNotExist extends ExpectedStream
  final case class AtNextSequence(value: Long) extends ExpectedStream
}

sealed trait RepositoryFailure extends Product with Serializable
object RepositoryFailure {
  final case class StorageFailure(message: String) extends RepositoryFailure
  final case class InvalidConfiguration(message: String)
      extends RepositoryFailure
}

sealed trait RepositoryAppendResult extends Product with Serializable
object RepositoryAppendResult {
  final case class Appended(firstSequence: Long, count: Int)
      extends RepositoryAppendResult
  case object StreamAlreadyExists extends RepositoryAppendResult
  case object StreamNotFound extends RepositoryAppendResult
  final case class SequenceConflict(expected: Long, actual: Long)
      extends RepositoryAppendResult
}

/**
 * Storage-neutral boundary for one authoritative stream per game.
 *
 * Implementations must append the complete `records` vector atomically and
 * compare `expected` in the same transaction. Records are durable wire values,
 * not commands or in-memory domain events.
 */
trait EventStreamRepository {
  def load(
      gameId: String
  ): Either[RepositoryFailure, Option[StoredEventStream]]

  def append(
      gameId: String,
      expected: ExpectedStream,
      records: Vector[String]
  ): Either[RepositoryFailure, RepositoryAppendResult]
}

/** Deterministic, synchronized repository for application tests and adapters. */
final class InMemoryEventStreamRepository extends EventStreamRepository {
  private var streams = Map.empty[String, Vector[String]]

  override def load(
      gameId: String
  ): Either[RepositoryFailure, Option[StoredEventStream]] = synchronized {
    Right(streams.get(gameId).map(StoredEventStream(gameId, _)))
  }

  override def append(
      gameId: String,
      expected: ExpectedStream,
      records: Vector[String]
  ): Either[RepositoryFailure, RepositoryAppendResult] = synchronized {
    import ExpectedStream._
    import RepositoryAppendResult._

    streams.get(gameId) match {
      case Some(_) if expected == MustNotExist =>
        Right(StreamAlreadyExists)
      case None if expected.isInstanceOf[AtNextSequence] =>
        Right(StreamNotFound)
      case None =>
        streams += gameId -> records
        Right(Appended(0L, records.size))
      case Some(current) =>
        val expectedSequence =
          expected.asInstanceOf[AtNextSequence].value
        val actual = current.size.toLong
        if (expectedSequence != actual)
          Right(SequenceConflict(expectedSequence, actual))
        else {
          streams += gameId -> (current ++ records)
          Right(Appended(actual, records.size))
        }
    }
  }

  /** Test support for representing records already written by external storage. */
  def seed(gameId: String, records: Vector[String]): Unit = synchronized {
    streams += gameId -> records
  }
}
