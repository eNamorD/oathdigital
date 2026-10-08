package oathdigital.application

import oathdigital.model.OathEvent.CommandAccepted
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Command batches and the live stack, as the service folds its journal
  * (undo design, "Replay: one forward fold"). */
class JournalFoldSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled

  test("each command's batch opens with its issuer, outside the events it reports"):
    val (service, _) = table.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    assertEquals(started.nextSequence, started.events.size.toLong + 1L)
    val history = present(accepted(service.history("g")), "a stored game")
    assertEquals(history.steps.head.event.event, CommandAccepted(p1))
    assertEquals(history.steps.tail.map(_.event.event), started.events)

  test("an ordinary batch is offered back to its issuer, on accept and on load"):
    val (service, repository) = table.service()
    val travel = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val picked = travel.picked
    val step = present(picked.undo, "an undo offered")
    assertEquals((step.issuer, step.from, step.until),
      (p1, travel.started.nextSequence, picked.nextSequence))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load("g").map(_.flatMap(_.undo)), Right(Some(step)))
    assertEquals(picked.logRevision, None)

  test("the latest batch is the one offered"):
    val (service, _) = table.service()
    val first = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
    val second = ParkedServiceFixture.travelToPawnOf(service, "g",
      first.picked.nextSequence, p1, p2)
    assertEquals(second.picked.undo.map(step => (step.from, step.until)),
      Some((second.started.nextSequence, second.picked.nextSequence)))

  test("a journal written without batch openings loads and offers nothing"):
    val (service, repository) = table.service()
    val picked = ParkedServiceFixture.travelToPawnOf(service, "g", 0L, p1, p3)
      .picked
    val stripped = UndoJournals.withoutBatchOpenings(repository, "g")
    val reloaded = ParkedServiceFixture.replayingSecond(service, stripped)
    assertEquals(reloaded.load("g").map(_.map(loaded =>
      (loaded.state, loaded.undo))), Right(Some((picked.state, None))))
