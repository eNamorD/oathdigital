package oathdigital.model

import oathdigital.model.TestGameFixtures._

class ReadyGameSuite extends munit.FunSuite:
  private val second = player.copy(player = PlayerId("player-blue"),
    lineage = LineageId("blue"))
  private val twoSeats = game.copy(current = game.current.copy(
    players = Vector(player, second)))

  test("printed warband supply is 14 per seated Exile lineage plus 24 bandits"):
    assertEquals(
      MaterialBankState.printedWarbandSupply(Vector(lineageId, second.lineage)),
      Map[ForceKind, Int](
        ForceKind.Exile(lineageId) -> 14,
        ForceKind.Exile(second.lineage) -> 14,
        ForceKind.Bandit -> 24))

  test("start stocks the seated lineages, the given banks and the first " +
      "player, and starts with the fixed unaltered first-game profile and " +
      "no knowledge"):
    val banks = Suit.all.map(_ -> 3).toMap
    val started = ReadyGame.start(twoSeats,
      Map(playerId -> PlayerColor.Red, second.player -> PlayerColor.Blue),
      firstPlayer = second.player, favorBanks = banks)

    assertEquals(started.banks.favor, banks)
    assertEquals(started.banks.warbandSupply.keySet, Set[ForceKind](
      ForceKind.Exile(lineageId), ForceKind.Exile(second.lineage),
      ForceKind.Bandit))
    // start is given no profile, so it defaults to FixedUnaltered, and the
    // knowledge of a new ReadyGame defaults to empty.
    assertEquals(started.setup,
      FirstGameSupportState(FirstGameFoundationProfile.FixedUnaltered,
        second.player))
    assertEquals(started.knowledge, CardKnowledge())
