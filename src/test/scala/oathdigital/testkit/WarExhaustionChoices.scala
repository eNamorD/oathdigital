package oathdigital.testkit

import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.model.PlayerId

/** Stubs of WarExhaustionRandomPort (canned answers, no record of calls) that
  * fix which tied candidate a War Exhaustion picks. `WarExhaustionRandomPortSuite`
  * holds them to the port's promise. */
object WarExhaustionChoices:
  /** Stub of WarExhaustionRandomPort: always picks the first candidate. */
  val firstCandidate: WarExhaustionRandomPort = new WarExhaustionRandomPort:
    def choose(candidates: Vector[PlayerId]): PlayerId = candidates.head

  /** Stub of WarExhaustionRandomPort: always picks the last candidate. */
  val lastCandidate: WarExhaustionRandomPort = new WarExhaustionRandomPort:
    def choose(candidates: Vector[PlayerId]): PlayerId = candidates.last
