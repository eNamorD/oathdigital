package oathdigital.gameplay

import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.model.PlayerId
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.WarExhaustionChoices

/** What every `WarExhaustionRandomPort` promises: it picks one of the tied
  * candidates it is given. The real random port and the shared stubs run it,
  * so a stub cannot name a winner who was not tied. */
class WarExhaustionRandomPortSuite extends munit.FunSuite:
  private val ports = Vector(
    "random" -> WarExhaustionRandomPort.random,
    "first candidate" -> WarExhaustionChoices.firstCandidate,
    "last candidate" -> WarExhaustionChoices.lastCandidate)

  ports.foreach { (name, port) =>
    test(s"$name: picks one of the tied candidates"):
      val tied = Vector(p1, p2, p3)
      // Random, so ask many times: every answer must be a tied player.
      val picks = Vector.fill(50)(port.choose(tied))
      assertEquals(picks.filterNot(tied.contains), Vector.empty[PlayerId])

    test(s"$name: a single candidate is the pick"):
      assertEquals(port.choose(Vector(p2)), p2)
  }
