package oathdigital.gameplay

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerCompleted}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes, RecordedOps}
import oathdigital.testkit.Outcomes.{finished, parked, recorded}
import oathdigital.testkit.ProcedureWalkerFixture._
import oathdigital.testkit.Unwrap.accepted

/** `ActAsIf` (catalog batch 8, item 13c): it records the site the walk's
  * actor acts as if at, a parked walk keeps it, and the walk's end clears
  * it, live and on replay. */
class ActAsIfSuite extends munit.FunSuite:
  private val site = CatalogNames.site("Deep Woods")

  /** `state` after `ActAsIf(site)` ran through the pipeline. */
  private def afterActingAsIf(state: ReadyGame): ReadyGame =
    accepted(OperationPipeline.run(state, Vector(ActAsIf(site)),
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state

  test("it records the site and moves no pawn"):
    val acting = afterActingAsIf(ready)
    assertEquals(acting.game.current.walkerAsIfSite, Some(site))
    assertEquals(pawnSiteOf(acting), pawnSiteOf(ready))

  test("a walk parked after it holds the site, and its events rebuild it"):
    val tree: Operation = Sequence(ActAsIf(site), decide)
    val park = parked(ProcedureWalker.advance(ready, tree, None, noPowers))
    assertEquals(RecordedOps.of(recorded(park.events.head)),
      Vector[CoreOperation](ActAsIf(site)))
    assertEquals(applyEvents(ready, park.events).game.current.walkerAsIfSite,
      Some(site))

  test("the walk's end clears it, live and on replay"):
    val done = finished(ProcedureWalker.advance(ready, ActAsIf(site), None,
      noPowers))
    assertEquals(done.treeless.game.current.walkerAsIfSite, None)
    val completed = accepted(ProcedureWalker.applyRecorded(
      OathState.Ready(afterActingAsIf(ready)),
      WalkerCompleted(ActionRef.Recover)))
    assertEquals(Outcomes.ready(completed).game.current.walkerAsIfSite, None)
