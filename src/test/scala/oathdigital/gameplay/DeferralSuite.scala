package oathdigital.gameplay

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.powerresolver.Deferred
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{Outcomes, RecordedOps}
import oathdigital.testkit.Outcomes.{finished, parked, recorded}
import oathdigital.testkit.ProcedureWalkerFixture._
import oathdigital.testkit.Unwrap.accepted
import oathdigital.testkit.WalkerStubs.continueOption

/** Deferral (operation rewrite design): `Defer` and `TakeDeferred` through
  * the pipeline, the walker dropping what is held when the action ends, and
  * a resolve that parks on its question. */
class DeferralSuite extends munit.FunSuite:
  private val key = DeferralKey("test.held")

  /** `state` after running `ops` through the pipeline as one batch. */
  private def afterBatch(state: ReadyGame, ops: CoreOperation*): ReadyGame =
    accepted(OperationPipeline.run(state, ops.toVector,
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state

  test("Defer holds ops under its key without running them, and " +
      "TakeDeferred drops them"):
    val held = afterBatch(ready, Defer(key, Vector(spendOneSupply)),
      Defer(key, Vector(movePawnToS3)))
    assertEquals(Deferred.held(held, key),
      Vector[CoreOperation](spendOneSupply, movePawnToS3))
    assertEquals(supplyOf(held), supplyOf(ready))
    assertEquals(pawnSiteOf(held), pawnSiteOf(ready))
    val taken = afterBatch(held, TakeDeferred(key))
    assertEquals(taken.game.current.walkerDeferred, Map.empty)

  test("a held op is kept as the journal records it"):
    val held = afterBatch(ready,
      Defer(key, Vector(SpendSupply(actor, 1, required = false))))
    assertEquals(Deferred.held(held, key),
      Vector[CoreOperation](SpendSupply(actor, 1)))

  test("the walker drops what is held when the action ends, live and on " +
      "replay"):
    val done = finished(ProcedureWalker.advance(ready,
      Defer(key, Vector(spendOneSupply)), None, noPowers))
    assertEquals(RecordedOps.of(recorded(done.events.head)),
      Vector[CoreOperation](Defer(key, Vector(spendOneSupply))))
    assertEquals(done.treeless.game.current.walkerDeferred, Map.empty)
    val held = afterBatch(ready, Defer(key, Vector(spendOneSupply)))
    val completed = accepted(ProcedureWalker.applyRecorded(
      OathState.Ready(held), WalkerCompleted(ActionRef.Recover)))
    assertEquals(Outcomes.ready(completed).game.current.walkerDeferred,
      Map.empty)

  test("a resolve that parks on its question still finds the ops held on " +
      "resume, and takes them last"):
    val tree: Operation = Sequence(Defer(key, Vector(spendOneSupply)),
      Deferred.resolve(key)((_, _, held) =>
        Vector(decide, BuildOps((_, _) => Right(held)))))
    val park = parked(ProcedureWalker.advance(ready, tree, None, noPowers))
    assertEquals(park.tree.at, Vector("1", "0"))
    val atPark = applyEvents(ready, park.events)
    assertEquals(Deferred.held(atPark, key),
      Vector[CoreOperation](spendOneSupply))
    val done = finished(ProcedureWalker.resolve(atPark, tree, park.tree,
      Answered(decide.decisionId, ChooseOneAnswer(continueOption), actor),
      noPowers))
    assertEquals(done.events.map(e => RecordedOps.of(recorded(e))), Vector(
      Vector.empty[CoreOperation], Vector[CoreOperation](spendOneSupply),
      Vector[CoreOperation](TakeDeferred(key))))
    assertEquals(supplyOf(done.treeless), supplyOf(ready) - 1)

  test("a resolve selects nothing while nothing is held"):
    val tree: Operation = Sequence(
      Deferred.resolve(key)((_, _, _) => Vector(decide)), spendOneSupply)
    val done = finished(ProcedureWalker.advance(ready, tree, None, noPowers))
    assertEquals(done.events.map(e => RecordedOps.of(recorded(e))),
      Vector(Vector[CoreOperation](spendOneSupply)))
