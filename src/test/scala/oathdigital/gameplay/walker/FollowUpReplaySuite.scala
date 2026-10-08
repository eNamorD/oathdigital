package oathdigital.gameplay.walker

import oathdigital.gameplay.walker.DeltaMeaning.OperationApplied
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** The queue's replay (follow-up actions design, "State, ops and events"):
  * queueing appends, a start takes the head and holds it until the run
  * completes, and a skip takes the head alone. */
class FollowUpReplaySuite extends munit.FunSuite:
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))
  private val campaign = FollowUp(p1, ActionRef.Campaign, knights)
  private val travel = FollowUp(p1, ActionRef.Travel, knights)

  private def current(state: OathState): CurrentGameState = state match
    case OathState.Ready(ready) => ready.game.current
    case other => fail(s"not ready: $other")

  private def queued(entries: FollowUp*): OathState =
    Table.start.update(_.updateCurrent(_.copy(
      followUps = entries.toVector))).state

  test("queueing appends to the back of the queue"):
    val step = WalkerStepRecorded("0", DeltaRecorded(OperationApplied("queue")),
      Vector(QueueFollowUp(travel)), Vector.empty)
    assertEquals(ProcedureWalker.applyRecorded(queued(campaign), step)
      .map(current(_).followUps), Right(Vector(campaign, travel)))

  test("a start takes the head and holds it until the run completes"):
    val started = ProcedureWalker.applyRecorded(queued(campaign, travel),
      FollowUpStarted(campaign))
    assertEquals(started.map(current(_).followUps), Right(Vector(travel)))
    assertEquals(started.map(current(_).walkerFollowUp), Right(Some(campaign)))
    val completed = started.flatMap(ProcedureWalker.applyRecorded(_,
      WalkerCompleted(ActionRef.Campaign)))
    assertEquals(completed.map(current(_).walkerFollowUp), Right(None))
    assertEquals(completed.map(current(_).followUps), Right(Vector(travel)))

  test("a skip takes the head and nothing else"):
    val skipped = ProcedureWalker.applyRecorded(queued(travel, campaign),
      FollowUpSkipped(travel))
    assertEquals(skipped.map(current(_).followUps), Right(Vector(campaign)))
    assertEquals(skipped.map(current(_).walkerFollowUp), Right(None))

  test("a start or a skip that names another entry than the head is refused"):
    assertEquals(ProcedureWalker.applyRecorded(queued(campaign, travel),
      FollowUpStarted(travel)), Left(OathViolation.InvalidEventOrder(
      "follow-up start does not match the queue head")))
    assertEquals(ProcedureWalker.applyRecorded(queued(campaign, travel),
      FollowUpSkipped(travel)), Left(OathViolation.InvalidEventOrder(
      "follow-up skip does not match the queue head")))

  test("a start while a run is open is refused"):
    val open = Table.start.parkedWalker(ActionRef.Muster)
      .update(_.updateCurrent(_.copy(followUps = Vector(campaign)))).state
    assertEquals(ProcedureWalker.applyRecorded(open, FollowUpStarted(campaign)),
      Left(OathViolation.InvalidEventOrder(
        "a follow-up started while another run is open")))
