package oathdigital.engine

/** One durable domain event paired with its zero-based stream position. */
final case class RecordedEvent[E](index: Long, event: E)

sealed trait EventAppendResult[+E]
object EventAppendResult {
  final case class Appended[E](records: Vector[RecordedEvent[E]])
      extends EventAppendResult[E]
  final case class Conflict(expectedIndex: Long, actualIndex: Long)
      extends EventAppendResult[Nothing]
}

/**
 * Generic append-only event persistence boundary.
 *
 * A command may append several events atomically. `expectedIndex` is the
 * position of the first proposed event and prevents stale writers from
 * interleaving event batches.
 */
trait EventJournal[E] {
  def size: Long
  def read(fromIndex: Long): Vector[RecordedEvent[E]]
  def append(
      expectedIndex: Long,
      events: Vector[E]
  ): EventAppendResult[E]
}

final class InMemoryEventJournal[E] extends EventJournal[E] {
  private var records = Vector.empty[RecordedEvent[E]]

  override def size: Long = synchronized(records.size.toLong)

  override def read(fromIndex: Long): Vector[RecordedEvent[E]] = synchronized {
    require(fromIndex >= 0, "fromIndex must be non-negative")
    records.drop(fromIndex.toInt)
  }

  override def append(
      expectedIndex: Long,
      events: Vector[E]
  ): EventAppendResult[E] = synchronized {
    val actualIndex = records.size.toLong
    if (expectedIndex != actualIndex)
      EventAppendResult.Conflict(expectedIndex, actualIndex)
    else {
      val appended = events.zipWithIndex.map { case (event, offset) =>
        RecordedEvent(actualIndex + offset, event)
      }
      records ++= appended
      EventAppendResult.Appended(appended)
    }
  }
}

/** Pure event evolution used both after commands and during replay. */
trait EventEvolution[S, E, V] {
  def initialState: S
  def evolve(state: S, event: E): Either[V, S]
}

final case class EventReplayFailure[V](index: Long, violation: V)

/** Deterministically reconstructs state and reports the corrupt event index. */
final class EventReplayEngine[S, E, V](
    evolution: EventEvolution[S, E, V]
) {
  def replay(
      events: Iterable[RecordedEvent[E]]
  ): Either[EventReplayFailure[V], S] =
    events.foldLeft[Either[EventReplayFailure[V], S]](
      Right(evolution.initialState)
    ) {
      case (Right(state), record) =>
        evolution
          .evolve(state, record.event)
          .left
          .map(violation => EventReplayFailure(record.index, violation))
      case (failure @ Left(_), _) => failure
    }
}
