package oathdigital.testkit

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.OathRules
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** A game's journal replayed: from no game, the way the server replays it,
  * or from a ready game, the way the power harnesses check a walk's events
  * rebuild its end. */
object Journal:
  /** The state `events`, numbered from 0, replay to; fails naming the first
    * event that does not replay, and why. */
  def replayed(rules: OathRules, events: Vector[OathEvent])(
      using munit.Location): OathState =
    val records = events.zipWithIndex.map((event, index) =>
      RecordedEvent(index.toLong, event))
    new EventReplayEngine(rules).replay(records).fold(
      failure => munit.Assertions.fail(
        s"event ${failure.index} does not replay: ${failure.violation}"),
      identity)

  /** The state `events` evolve `from` to, one `rules.evolve` per event, or
    * the violation that stopped the first event that does not evolve. */
  def evolved(rules: OathRules, from: ReadyGame, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](Right(Ready(from)))(
      (state, event) => state.flatMap(rules.evolve(_, event)))
