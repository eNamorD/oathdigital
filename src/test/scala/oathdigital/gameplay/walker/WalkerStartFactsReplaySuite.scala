package oathdigital.gameplay.walker

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

/** Start facts are restored by replay, checked against the recorded start on
  * every later park, and cleared when the walk completes (travel destination
  * design, ruling 6). */
class WalkerStartFactsReplaySuite extends munit.FunSuite:
  private val source = DecisionOptionRef.Site(CatalogNames.site("Buried Giant"))
  private val elsewhere = DecisionOptionRef.Site(CatalogNames.site("Dunes"))

  private def park(at: String, facts: Vector[DecisionOptionRef]): WalkerParked =
    WalkerParked(ActionRef.Travel, Vector(at), Vector.empty, Vector.empty,
      facts)

  private def facts(state: OathState): Vector[DecisionOptionRef] = state match
    case OathState.Ready(ready) => ready.game.current.walkerStartFacts
    case other => fail(s"not ready: $other")

  test("a park restores its start facts and a completion clears them"):
    val parked = ProcedureWalker.applyRecorded(
      OathState.Ready(Table.start.ready), park("0", Vector(source)))
    assertEquals(parked.map(facts), Right(Vector(source)))
    val completed = parked.flatMap(ProcedureWalker.applyRecorded(_,
      WalkerCompleted(ActionRef.Travel)))
    assertEquals(completed.map(facts), Right(Vector.empty))

  test("a later park naming other start facts is refused"):
    val refused = ProcedureWalker.applyRecorded(
      OathState.Ready(Table.start.ready), park("0", Vector(source)))
      .flatMap(ProcedureWalker.applyRecorded(_, park("1", Vector(elsewhere))))
    assertEquals(refused, Left(OathViolation.InvalidEventOrder(
      "durable walker park start facts do not match the recorded start")))
