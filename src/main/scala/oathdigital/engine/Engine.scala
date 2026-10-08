package oathdigital.engine

/** One durable domain event paired with its zero-based stream position. */
final case class RecordedEvent[E](index: Long, event: E)

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
