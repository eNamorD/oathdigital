package oathdigital.engine

/** One durable domain event paired with its zero-based stream position. */
final case class RecordedEvent[E](index: Long, event: E)

sealed trait EventAppendResult[+E]
object EventAppendResult:
  final case class Appended[E](records: Vector[RecordedEvent[E]])
      extends EventAppendResult[E]
  final case class Conflict(expectedIndex: Long, actualIndex: Long)
      extends EventAppendResult[Nothing]

/**
 * Generic append-only event persistence boundary.
 *
 * A command may append several events atomically. `expectedIndex` is the
 * position of the first proposed event and prevents stale writers from
 * interleaving event batches.
 */
trait EventJournal[E]:
  def size: Long
  def read(fromIndex: Long): Vector[RecordedEvent[E]]
  def append(
      expectedIndex: Long,
      events: Vector[E]
  ): EventAppendResult[E]

final class InMemoryEventJournal[E] extends EventJournal[E]:
  private var records = Vector.empty[RecordedEvent[E]]

  override def size: Long = synchronized(records.size.toLong)

  override def read(fromIndex: Long): Vector[RecordedEvent[E]] = synchronized:
    require(fromIndex >= 0, "fromIndex must be non-negative")
    records.drop(fromIndex.toInt)

  override def append(
      expectedIndex: Long,
      events: Vector[E]
  ): EventAppendResult[E] = synchronized:
    val actualIndex = records.size.toLong
    if expectedIndex != actualIndex then
      EventAppendResult.Conflict(expectedIndex, actualIndex)
    else
      val appended = events.zipWithIndex.map { case (event, offset) =>
        RecordedEvent(actualIndex + offset, event)
      }
      records ++= appended
      EventAppendResult.Appended(appended)

/** Pure event evolution used both after commands and during replay. */
trait EventEvolution[S, E, V]:
  def initialState: S
  def evolve(state: S, event: E): Either[V, S]

final case class EventReplayFailure[V](index: Long, violation: V)

/** One journal event with the state it was applied to and the state it made. */
final case class ReplayStep[S, E](event: RecordedEvent[E], before: S, after: S)

/** Deterministically reconstructs state and reports the corrupt event index. */
final class EventReplayEngine[S, E, V](
    evolution: EventEvolution[S, E, V]
):
  /** The whole fold, one step per event, so a reader that needs the state
    * around every event (the game log) and a reader that needs only the end
    * (`replay`) share one fold and cannot drift.
    */
  def scan(
      events: Iterable[RecordedEvent[E]]
  ): Either[EventReplayFailure[V], Vector[ReplayStep[S, E]]] =
    events.foldLeft[Either[EventReplayFailure[V], (S, Vector[ReplayStep[S, E]])]](
      Right(evolution.initialState -> Vector.empty)
    ):
      case (Right((state, steps)), record) =>
        evolution
          .evolve(state, record.event)
          .left
          .map(violation => EventReplayFailure(record.index, violation))
          .map(next => next -> (steps :+ ReplayStep(record, state, next)))
      case (Left(failure), _) => Left(failure)
    .map(_._2)

  def replay(
      events: Iterable[RecordedEvent[E]]
  ): Either[EventReplayFailure[V], S] =
    scan(events).map(_.lastOption.fold(evolution.initialState)(_.after))
