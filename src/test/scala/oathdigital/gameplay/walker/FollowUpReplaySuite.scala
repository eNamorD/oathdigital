package oathdigital.gameplay.walker

import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** The queue's replay (walker-driven game loop design, "The queue"):
  * queueing appends or goes in front, a dequeue takes the head and holds it
  * until the walk completes, and a skip changes nothing. */
class FollowUpReplaySuite extends munit.FunSuite:
  private val knights =
    PowerSourceRef.Card(CatalogNames.denizen("Knights Errant"))
  private val campaign = QueuedProcedure(p1, ActionRef.Campaign, Vector.empty,
      Some(knights), QueueKind.Offer)
  private val travel = QueuedProcedure(p1, ActionRef.Travel, Vector.empty,
      Some(knights), QueueKind.Offer)

  private def current(state: OathState): CurrentGameState = state match
    case OathState.Ready(ready) => ready.game.current
    case other => fail(s"not ready: $other")

  private def queued(entries: QueuedProcedure*): OathState =
    Table.start.update(_.updateCurrent(_.copy(
      queue = entries.toVector))).state

  test("queueing appends to the back of the queue"):
    val step = WalkerStepRecorded("0", DeltaRecorded,
      Vector(QueueProcedure(travel)), Vector.empty)
    assertEquals(ProcedureWalker.applyRecorded(queued(campaign), step)
      .map(current(_).queue), Right(Vector(campaign, travel)))

  test("a start takes the head and holds it until the run completes"):
    val started = ProcedureWalker.applyRecorded(queued(campaign, travel),
      ProcedureDequeued(campaign))
    assertEquals(started.map(current(_).queue), Right(Vector(travel)))
    assertEquals(started.map(current(_).walkerEntry), Right(Some(campaign)))
    val completed = started.flatMap(ProcedureWalker.applyRecorded(_,
      WalkerCompleted(ActionRef.Campaign)))
    assertEquals(completed.map(current(_).walkerEntry), Right(None))
    assertEquals(completed.map(current(_).queue), Right(Vector(travel)))

  test("a skip names the offered entry and changes nothing"):
    val offering = Table.start.offering(travel).state
    assertEquals(ProcedureWalker.applyRecorded(offering,
      FollowUpSkipped(travel)), Right(offering))
    assertEquals(ProcedureWalker.applyRecorded(offering,
      FollowUpSkipped(campaign)), Left(OathViolation.InvalidEventOrder(
      "a skip does not name the offered entry")))

  test("a dequeue that names another entry than the head is refused"):
    assertEquals(ProcedureWalker.applyRecorded(queued(campaign, travel),
      ProcedureDequeued(travel)), Left(OathViolation.InvalidEventOrder(
      "dequeued entry does not match the queue head")))

  test("a start while a run is open is refused"):
    val open = Table.start.parkedWalker(ActionRef.Muster)
      .update(_.updateCurrent(_.copy(queue = Vector(campaign)))).state
    assertEquals(
      ProcedureWalker.applyRecorded(open, ProcedureDequeued(campaign)),
      Left(OathViolation.InvalidEventOrder(
        "an entry was dequeued while another walk is open")))

  test("an entry queued in front goes before the waiting ones"):
    val first = campaign.copy(procedure = ActionRef.Muster,
      kind = QueueKind.Run)
    val ready = Table.start.update(_.updateCurrent(_.copy(
      queue = Vector(campaign)))).ready
    assertEquals(WalkerReplay.applyOperation(ready,
      QueueProcedure(first, atFront = true)).map(_.game.current.queue),
      Right(Vector(first, campaign)))
