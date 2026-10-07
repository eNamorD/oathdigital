package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathEvent.CommandAccepted
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}

/** Command batches and the live stack, as the service folds its journal
  * (undo design, "Replay: one forward fold"). */
class JournalFoldSuite extends munit.FunSuite:
  private val table = Table.start.banditsRefilled
  private def travel(actor: PlayerId, to: PlayerId) = GameCommand.StartWalker(
    ActionRef.Travel, StartPayload(actor, Vector.empty,
      Vector(DecisionOptionRef.Site(Table.quietPawnSite(to)))))
  private def accept(result: Either[GameApplicationError, GameAccepted])
      : GameAccepted = result.fold(error => fail(s"rejected: $error"), identity)

  test("each command's batch opens with its issuer, outside the events it reports"):
    val (service, _) = table.service()
    val accepted = accept(service.handle("g", 0L, travel(p1, p3)))
    assertEquals(accepted.nextSequence, accepted.events.size.toLong + 1L)
    val history = service.history("g").toOption.flatten
      .getOrElse(fail("no history"))
    assertEquals(history.steps.head.event.event, CommandAccepted(p1))
    assertEquals(history.steps.tail.map(_.event.event), accepted.events)

  test("an ordinary batch is offered back to its issuer, on accept and on load"):
    val (service, repository) = table.service()
    val accepted = accept(service.handle("g", 0L, travel(p1, p3)))
    val step = accepted.undo.getOrElse(fail("no undo offered"))
    assertEquals((step.issuer, step.from, step.until),
      (p1, 0L, accepted.nextSequence))
    assert(step.note.nonEmpty, "a Travel posts a line its issuer sees")
    val reloaded = new GameApplicationService(catalog, repository,
      genesis = table.state)
    assertEquals(reloaded.load("g").map(_.flatMap(_.undo)), Right(Some(step)))
    assertEquals(accepted.logRevision, None)

  test("the latest batch is the one offered"):
    val (service, _) = table.service()
    val first = accept(service.handle("g", 0L, travel(p1, p3)))
    val second = accept(service.handle("g", first.nextSequence, travel(p1, p2)))
    assertEquals(second.undo.map(step => (step.from, step.until)),
      Some((first.nextSequence, second.nextSequence)))

  test("a journal written without batch openings loads and offers nothing"):
    val (service, repository) = table.service()
    val accepted = accept(service.handle("g", 0L, travel(p1, p3)))
    val stripped = UndoJournals.withoutBatchOpenings(repository, "g")
    val reloaded = new GameApplicationService(catalog, stripped,
      genesis = table.state)
    assertEquals(reloaded.load("g").map(_.map(loaded =>
      (loaded.state, loaded.undo))), Right(Some((accepted.state, None))))
