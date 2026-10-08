package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Outcomes.{finished, parked}
import oathdigital.testkit.Table.p1

/** When a `Repeat` stops. Its guard reads only state and answers, so a pass that
  * records nothing and asks nothing cannot change what the guard reads, and the
  * loop ends instead of repeating for ever.
  */
class RepeatPassSuite extends munit.FunSuite:
  private val actor = p1
  /** p1's Supply down to 1, so a gain shows. */
  private val supplyOfOne: ReadyGame = Table.start.supply(actor, 1).ready

  private def supply(state: ReadyGame): Int = Look(state).supply(actor)

  private def run(tree: Operation) = ProcedureWalker.advance(supplyOfOne, tree,
    None, WalkerPowers.empty)

  test("a pass that records nothing ends the loop, and the walk goes on"):
    val WalkerOutcome.Finished(after, events) = finished(run(Sequence(
      Vector[Operation](Repeat((_, _) => true, Sequence(Vector.empty)),
        GainSupply(actor, 1)))))
    // 1 + 1 gained.
    assertEquals(supply(after), 2)
    assertEquals(events.size, 1)

  test("a pass that parks on a decision is not an empty pass"):
    val question = Decide("test.question", actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val outcome = run(Repeat((_, _) => true, question))
    assertEquals(parked(outcome).tree, PendingTree(Vector("0"), Vector.empty))
