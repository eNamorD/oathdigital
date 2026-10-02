package oathdigital.testkit

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.OathRules
import oathdigital.model._

/** A game's journal replayed from no game, the way the server replays it. */
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
