package oathdigital.model

import oathdigital.model.TestGameFixtures._

class ReadyGameSuite extends munit.FunSuite {
  private val second = player.copy(player = PlayerId("player-blue"),
    lineage = LineageId("blue"))
  private val twoSeats = game.copy(current = game.current.copy(
    players = Vector(player, second)))

  test("printed warband supply is 14 per seated Exile lineage plus 24 bandits") {
    assertEquals(
      MaterialBankState.printedWarbandSupply(Vector(lineageId, second.lineage)),
      Map[ForceKind, Int](
        ForceKind.Exile(lineageId) -> 14,
        ForceKind.Exile(second.lineage) -> 14,
        ForceKind.Bandit -> 24))
  }

  test("start stocks the seated lineages, the given banks and the first player") {
    val banks = Suit.all.map(_ -> 3).toMap
    val started = ReadyGame.start(twoSeats,
      Map(playerId -> PlayerColor.Red, second.player -> PlayerColor.Blue),
      firstPlayer = second.player, favorBanks = banks)

    assertEquals(started.banks.favor, banks)
    assertEquals(started.banks.warbandSupply.keySet, Set[ForceKind](
      ForceKind.Exile(lineageId), ForceKind.Exile(second.lineage),
      ForceKind.Bandit))
    assertEquals(started.setup,
      FirstGameSupportState(FirstGameFoundationProfile.FixedUnaltered,
        second.player))
    assertEquals(started.knowledge, CardKnowledge())
  }

  test("updateCurrent changes the current game state and nothing else") {
    val moved = ready.updateCurrent(_.copy(tracks =
      ready.game.current.tracks.copy(round = 7)))

    assertEquals(moved.game.current.tracks.round, 7)
    assertEquals(moved.copy(game = moved.game.copy(current = ready.game.current)),
      ready)
  }

  test("updateCampaign changes the campaign state and nothing else") {
    val goal = OathkeeperGoal.ThePeople
    val changed = ready.updateCampaign(_.copy(oathkeeperGoal = goal))

    assertEquals(changed.game.campaign.oathkeeperGoal, goal)
    assertEquals(
      changed.updateCampaign(_ => ready.game.campaign), ready)
  }

  test("the test builder seats players in order and starts the active player") {
    val table = ReadyGames.of(twoSeats, favorPerSuit = 2)

    assertEquals(table.playerColors, Map(
      playerId -> PlayerColor.Red, second.player -> PlayerColor.Blue))
    assertEquals(table.setup.firstPlayer, playerId)
    assertEquals(table.banks.favor, Suit.all.map(_ -> 2).toMap)
  }
}
