package oathdigital.engine

/** A command whose result must be durable and replayable. */
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

/** An action paired with its zero-based position in the authoritative stream. */
final case class RecordedAction(index: Long, action: Action)

sealed trait AppendResult
object AppendResult {
  final case class Appended(record: RecordedAction) extends AppendResult
  final case class Conflict(expectedIndex: Long, actualIndex: Long) extends AppendResult
}

/**
 * Append-only persistence boundary.
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

/** Deterministically reconstructs game state from the authoritative action stream. */
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
