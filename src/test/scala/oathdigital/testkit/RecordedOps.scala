package oathdigital.testkit

import oathdigital.gameplay.walker.{ChoicePayload, DeltaPayload, RollPayload,
  WalkerStepRecorded}
import oathdigital.model.{CoreOperation, OathEvent}

/** The operations a recorded step applied, for the suites that assert what a
  * walk recorded.
  *
  * A choice or a roll records a state write and applies none, so it reads as
  * empty here. That makes "this step applied nothing" a fact about this
  * function rather than about the walker: a test that means it must assert
  * the step's payload instead.
  */
object RecordedOps:
  def of(step: WalkerStepRecorded): Vector[CoreOperation] =
    step.payload match
      case DeltaPayload(ops) => ops
      case _: ChoicePayload | _: RollPayload => Vector.empty

  /** Every operation the recorded steps among `events` applied, in order. */
  def all(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => of(step) }.flatten
