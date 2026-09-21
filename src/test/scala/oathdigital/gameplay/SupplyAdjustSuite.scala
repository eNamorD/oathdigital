package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class SupplyAdjustSuite extends munit.FunSuite {
  private val blueId = PlayerId("player-blue")
  private val blueLineage = LineageId("blue")

  private val bluePlayer = PlayerState(
    blueId, blueLineage, Some(sites(1)),
    PlayerBoardState(2, 1, 0, 2, SupplyTrack.full),
    Vector.empty, Vector(RelicState(RelicId("R3"), Orientation.FaceUp,
      Tokens.empty)), None)

  private val ready = {
    val current = game.current.copy(
      players = game.current.players :+ bluePlayer,
      banners = game.current.banners.copy(
        peoplesFavor = game.current.banners.peoplesFavor.copy(
          holder = Some(playerId))))
    val campaign = game.campaign.copy(lineages = game.campaign.lineages.updated(
      blueLineage,
      LineageState(blueLineage, Some(blueId), Role.Exile,
        Vector.empty, Vector.empty)))
    ReadyGames.of(game.copy(campaign = campaign, current = current))
  }

  private val executor = new OperationExecutor()

  private def supply(state: ReadyGame, player: PlayerId): Int =
    state.game.current.players.find(_.player == player).get.board.supply.supply

  private def withSupply(player: PlayerId, value: Int): ReadyGame = {
    val fixed = ready.game.current.players.map(existing =>
      if (existing.player != player) existing
      else existing.copy(board = existing.board.copy(
        supply = SupplyTrack(value))))
    ready.updateCurrent(_.copy(players = fixed))
  }

  test("an exact spend reduces supply") {
    val actor = ready.game.current.players.find(_.player == playerId).get
    val result = executor.execute(ready,
      SpendSupply(playerId, 2)).toOption.get
    assertEquals(supply(result, playerId), supply(ready, playerId) - 2)
    assert(actor.board.supply.supply > 2)
  }

  test("an unaffordable spend is rejected") {
    val source = withSupply(playerId, 1)
    val result = executor.execute(source, SpendSupply(playerId, 2))
    assert(result.left.toOption.get
      .isInstanceOf[OperationError.InsufficientSupply])
  }

  test("a positive adjustment caps at the track maximum") {
    val source = withSupply(playerId, 6)
    val result = executor.execute(source,
      GainSupply(playerId, 5)).toOption.get
    assertEquals(supply(result, playerId), SupplyTrack.Maximum)
  }

  test("staged adjustments apply in order") {
    val first = executor.execute(ready,
      SpendSupply(playerId, 2)).toOption.get
    val second = executor.execute(first,
      SpendSupply(playerId, 2)).toOption.get
    assertEquals(supply(second, playerId), supply(ready, playerId) - 4)
  }
}
