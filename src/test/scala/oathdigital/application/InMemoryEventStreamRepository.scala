package oathdigital.application

/** Fake of `EventStreamRepository` for tests: deterministic and synchronized.
  * `EventStreamRepositoryContract` holds it to the real repository's behavior
  * (see `InMemoryEventStreamRepositorySuite`). It lives in `src/test` because
  * no production code uses it. */
final class InMemoryEventStreamRepository extends EventStreamRepository:
  private var streams = Map.empty[String, Vector[String]]

  override def load(
      gameId: String
  ): Either[RepositoryFailure, Option[StoredEventStream]] = synchronized:
    Right(streams.get(gameId).map(StoredEventStream(gameId, _)))

  override def append(
      gameId: String,
      expected: ExpectedStream,
      records: Vector[String]
  ): Either[RepositoryFailure, RepositoryAppendResult] = synchronized:
    import ExpectedStream._
    import RepositoryAppendResult._

    streams.get(gameId) match
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
        if expectedSequence != actual then
          Right(SequenceConflict(expectedSequence, actual))
        else
          streams += gameId -> (current ++ records)
          Right(Appended(actual, records.size))

  /** Test support for representing records already written by external storage. */
  def seed(gameId: String, records: Vector[String]): Unit = synchronized:
    streams += gameId -> records
