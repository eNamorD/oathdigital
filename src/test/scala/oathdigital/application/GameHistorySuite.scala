package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, chronicle, orders}
import oathdigital.model.OathState
import oathdigital.testkit.Unwrap.accepted
import ParkedServiceFixture.loaded

class GameHistorySuite extends munit.FunSuite:
  test("history is the scanned journal: one step per event, ending on the loaded state"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val begun = accepted(service.handle("history", 0L,
      GameCommand.Begin(chronicle, orders)))
    val history = loaded(service.history("history"))
    val stored = loaded(service.load("history"))
    assertEquals(history.nextSequence, begun.nextSequence)
    assertEquals(history.steps.size.toLong, begun.nextSequence)
    assertEquals(history.steps.map(_.event.index),
      (0L until begun.nextSequence).toVector)
    assertEquals(history.steps.head.before, OathState.NoGame)
    assertEquals(history.steps.last.after, stored.state)
    history.steps.sliding(2).foreach { pair =>
      assertEquals(pair(1).before, pair(0).after) }

  test("history of a missing game is None"):
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository)
    assertEquals(service.history("missing"), Right(None))
