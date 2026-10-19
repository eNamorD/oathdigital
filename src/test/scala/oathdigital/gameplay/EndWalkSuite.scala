package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.testkit.RecordedOps
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** `EndWalk` finishes the walk where it is reached: nothing after it runs,
  * in its own children or in any enclosing node. */
class EndWalkSuite extends munit.FunSuite:
  /** A leaf that records a step and names it. */
  private def recording(name: String): ModifyDicePool =
    ModifyDicePool(PoolKey(name), 1)

  test("a walk finishes at EndWalk inside a branch, and no later node runs, " +
      "not even a decision"):
    val later = Decide("test.later", ready.game.current.turn.activePlayer,
      DecisionQuery.ChooseOne(Vector(DecisionOption.Button(
        DecisionOptionRef.Button("ok"), "OK"))))
    val walk = Sequence(Vector[Operation](recording("first"),
      Branch((_, _) => Vector[Operation](recording("second"), EndWalk,
        recording("skipped"))),
      later, recording("last")))
    ProcedureWalker.advance(ready, walk, None, WalkerPowers.empty) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(RecordedOps.all(events),
          Vector[CoreOperation](recording("first"), recording("second")))
      case other => fail(s"expected the walk to finish at EndWalk, got $other")
