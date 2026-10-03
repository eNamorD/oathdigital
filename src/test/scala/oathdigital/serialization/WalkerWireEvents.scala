package oathdigital.serialization

import oathdigital.engine.RecordedEvent
import oathdigital.gameplay.walker.{DeltaMeaning, WalkerStepPayload,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.Unwrap.accepted

/** The recorded walker events and event streams the wire suites write and
  * read back. */
object WalkerWireEvents:
  /** The walker step at path "0" that records `operation` as applied, under
    * the delta `label`. */
  def recordedOperation(label: String, operation: CoreOperation): OathEvent =
    WalkerStepRecorded("0", WalkerStepPayload.DeltaRecorded(
      DeltaMeaning.OperationApplied(label)), Vector(operation), Vector.empty)

  /** `events` written as one stream of game `label`, numbered from 0. Fails
    * with the WireError if the codec refuses to write it. */
  def encodedStream(label: String, events: Vector[OathEvent])(
      using munit.Location): String =
    accepted(GameEventWire.encodeStream(label,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event) }))

  /** [[encodedStream]] of `events`, which also asserts that decoding the
    * stream gives `events` back. Returns the written JSON for further
    * checks. */
  def assertStreamRoundTrips(label: String, events: Vector[OathEvent])(
      using munit.Location): String =
    val encoded = encodedStream(label, events)
    munit.Assertions.assertEquals(
      accepted(GameEventWire.decodeStream(encoded)).map(_.event), events)
    encoded
