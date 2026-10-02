package oathdigital.testkit

import oathdigital.gameplay.walker.WalkerOutcome
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.serialization.GameEventWire

/** Reads of rules results that [[Unwrap]] cannot name generically. Each
  * fails the test with the value it got. */
object Outcomes:
  /** The game in `state`; fails naming the state when it is not Ready. */
  def ready(state: OathState)(using munit.Location): ReadyGame = state match
    case Ready(game) => game
    case other => munit.Assertions.fail(s"expected a ready game, got $other")

  /** The walk parked at a decision; fails with the violation or the
    * finished walk. */
  def parked(outcome: Either[OathViolation, WalkerOutcome])(
      using munit.Location): WalkerOutcome.Parked =
    Unwrap.as[WalkerOutcome.Parked](Unwrap.accepted(outcome))

  /** The walk that consumed its whole tree; fails with the violation or the
    * park. */
  def finished(outcome: Either[OathViolation, WalkerOutcome])(
      using munit.Location): WalkerOutcome.Finished =
    Unwrap.as[WalkerOutcome.Finished](Unwrap.accepted(outcome))

  /** Every event encodes and decodes back equal; fails naming the first
    * that does not, and why. */
  def assertWireRoundTrips(events: Vector[OathEvent])(
      using munit.Location): Unit =
    events.zipWithIndex.foreach { case (event, index) =>
      val back = GameEventWire.encodeEvent("g", index.toLong, event)
        .left.map(failure => s"encode failed: $failure")
        .flatMap(json => GameEventWire.decode(json)
          .left.map(failure => s"decode failed: $failure").map(_.event))
      munit.Assertions.assertEquals(back, Right(event),
        s"event $index does not round trip")
    }
