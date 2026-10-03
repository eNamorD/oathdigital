package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Unwrap.{accepted, rejected}

class SupplyAdjustSuite extends munit.FunSuite:
  private val ready = ReadyGames.of(game)

  private val executor = new OperationExecutor()

  private def supply(state: ReadyGame, player: PlayerId): Int =
    Look(state).supply(player)

  private def withSupply(player: PlayerId, value: Int): ReadyGame =
    Table.from(ready).supply(player, value).unchecked

  test("an exact spend reduces supply"):
    val result = accepted(executor.execute(ready,
      SpendSupply(playerId, 2)))
    assertEquals(supply(result, playerId), supply(ready, playerId) - 2)
    assert(supply(ready, playerId) > 2)

  test("an unaffordable spend is rejected"):
    val source = withSupply(playerId, 1)
    val result = executor.execute(source, SpendSupply(playerId, 2))
    assert(rejected(result)
      .isInstanceOf[OperationError.InsufficientSupply])

  test("a positive adjustment caps at the track maximum"):
    val source = withSupply(playerId, 6)
    val result = accepted(executor.execute(source,
      GainSupply(playerId, 5)))
    assertEquals(supply(result, playerId), SupplyTrack.Maximum)
