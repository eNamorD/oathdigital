package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1

/** A service seeded with a start state: its streams begin there instead of
  * at `GameStarted`. Tests only; see the service and log start-states
  * design. */
class GenesisServiceSuite extends munit.FunSuite:
  test("a missing stream loads as the genesis at sequence 0"):
    val table = Table.start
    val (service, _) = table.service()
    assertEquals(service.load("g"), Right(Some(LoadedGame(table.state, 0L))))

  test("the first command runs against the genesis, and replay starts there"):
    val table = Table.start
    val (service, repository) = table.service()
    val accepted = service.handle("g", 0L, GameCommand.BeginRest(p1))
      .fold(error => fail(s"rejected: $error"), identity)
    assert(accepted.events.nonEmpty)
    assertEquals(accepted.nextSequence, accepted.events.size.toLong)
    // A second service over the same journal replays from the same genesis.
    val reloaded = new GameApplicationService(catalog, repository,
      genesis = table.state)
    assertEquals(reloaded.load("g").map(_.map(_.state)),
      Right(Some(accepted.state)))

  test("a started genesis refuses Begin"):
    val (service, _) = Table.start.service()
    assert(service.handle("g", 0L, GameCommand.Begin(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders)).isLeft)

  test("without a genesis, a missing stream still accepts only Begin"):
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository)
    assertEquals(service.load("g"), Right(None))
    assertEquals(service.handle("g", 0L, GameCommand.BeginRest(p1)),
      Left(GameApplicationError.StreamNotFound("g")))
