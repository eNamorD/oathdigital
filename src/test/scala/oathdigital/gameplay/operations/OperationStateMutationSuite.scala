package oathdigital.gameplay.operations

import oathdigital.gameplay.{MaterialBankState, ReadyGame}
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class OperationStateMutationSuite extends munit.FunSuite {
  private val ready = ReadyGame(
    game.copy(current = game.current.copy(players = game.current.players.map {
      player => player.copy(board = player.board.copy(
        faceUpSecrets = 1, faceDownSecrets = 1))
    })),
    Map(playerId -> PlayerColor("red")),
    FirstGameSupportState(
      FirstGameFoundationProfile.FixedUnaltered, playerId),
    MaterialBankState(Map.empty, Map.empty)
  )

  test("secret planning resolves one aggregate solution independent of order") {
    val other = PlayerId("other")
    val flexible = Move(
      Piece.Secrets(1),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.PlayArea(other))
    )
    val faceUpOnly = Move(
      Piece.Secrets(1),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head))
    )

    assertEquals(
      OperationSecretPlanner.plan(
        ready, Vector(flexible, faceUpOnly)),
      Right(Vector(
        flexible -> OperationSecretPlanner.SecretSplit(0, 1),
        faceUpOnly -> OperationSecretPlanner.SecretSplit(1, 0)
      ))
    )
  }
}
