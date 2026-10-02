package oathdigital.testkit

import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.walker.WalkerOutcome
import oathdigital.model._
import oathdigital.model.OathState.Ready
import Outcomes.*

/** The rules-result reads return what a test expects and fail with what
  * they got. */
class OutcomesSuite extends munit.FunSuite:
  private def failure(body: => Any): String =
    intercept[munit.FailException](body).getMessage

  private val quiet = Table.start.ready
  private val walk = PendingTree(Vector("0"), Vector.empty)

  test("ready returns the game in a Ready state and fails naming another state"):
    assertEquals(ready(Ready(quiet)), quiet)
    assert(failure(ready(OathState.NoGame)).contains("NoGame"))

  test("parked and finished return their walk and fail naming the other"):
    val parkedWalk = WalkerOutcome.Parked(walk, Vector.empty)
    val finishedWalk = WalkerOutcome.Finished(quiet, Vector.empty)
    assertEquals(parked(Right(parkedWalk)), parkedWalk)
    assertEquals(finished(Right(finishedWalk)), finishedWalk)
    assert(failure(parked(Right(finishedWalk))).contains("Finished"))
    assert(failure(finished(Left(OathViolation.NoPlayableOption("Search"))))
      .contains("NoPlayableOption"))

  test("assertWireRoundTrips passes for the events of a real setup"):
    assertWireRoundTrips(FirstGameSetupFixture.execute()._2.take(3))
