package oathdigital.engine

/**
 * Legacy HRF-inspired action API retained for compatibility and tests.
 *
 * Production rules persist resulting domain events, not actions.
 */
trait Action extends Product with Serializable

/** What the rules engine expects after applying an action. */
sealed trait Continue extends Product with Serializable
object Continue {
  final case class AwaitingPlayer(playerId: String, legalActions: Vector[Action]) extends Continue
  final case class Forced(action: Action) extends Continue
  final case class Milestone(name: String, next: Action) extends Continue
  final case class Finished(winners: Set[String]) extends Continue
}

/** Result of one deterministic rules transition. */
final case class Transition[S](state: S, continue: Continue)

/** Pure game rules. The same state and action must always produce the same result. */
trait Rules[S] {
  def initialState: S
  def transition(state: S, action: Action): Either[RuleViolation, Transition[S]]
}

final case class RuleViolation(message: String)

/** An action paired with its zero-based position in a legacy action stream. */
final case class RecordedAction(index: Long, action: Action)

sealed trait AppendResult
object AppendResult {
  final case class Appended(record: RecordedAction) extends AppendResult
  final case class Conflict(expectedIndex: Long, actualIndex: Long) extends AppendResult
}

/**
 * Legacy append-only action boundary.
 *
 * `expectedIndex` provides optimistic concurrency for asynchronous turns:
 * only a client that has consumed the complete stream can append to it.
 */
trait Journal {
  def size: Long
  def read(fromIndex: Long): Vector[RecordedAction]
  def append(expectedIndex: Long, action: Action): AppendResult
}

final class InMemoryJournal extends Journal {
  private var records = Vector.empty[RecordedAction]

  override def size: Long = synchronized(records.size.toLong)

  override def read(fromIndex: Long): Vector[RecordedAction] = synchronized {
    require(fromIndex >= 0, "fromIndex must be non-negative")
    records.drop(fromIndex.toInt)
  }

  override def append(expectedIndex: Long, action: Action): AppendResult = synchronized {
    val actualIndex = records.size.toLong
    if (expectedIndex != actualIndex)
      AppendResult.Conflict(expectedIndex, actualIndex)
    else {
      val record = RecordedAction(actualIndex, action)
      records :+= record
      AppendResult.Appended(record)
    }
  }
}

/** Deterministically replays a legacy action stream for compatibility. */
final class ReplayEngine[S](rules: Rules[S]) {
  def replay(actions: Iterable[RecordedAction]): Either[ReplayFailure, S] = {
    actions.foldLeft[Either[ReplayFailure, S]](Right(rules.initialState)) {
      case (Right(state), record) =>
        rules
          .transition(state, record.action)
          .map(_.state)
          .left
          .map(violation => ReplayFailure(record.index, violation))
      case (failure @ Left(_), _) => failure
    }
  }
}

final case class ReplayFailure(index: Long, violation: RuleViolation)

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
