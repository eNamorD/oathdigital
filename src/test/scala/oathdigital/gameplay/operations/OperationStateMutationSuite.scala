package oathdigital.gameplay.operations

import oathdigital.gameplay.setup.{FirstGameSetupFixture, FirstGameSetupRules}
import oathdigital.model.OathState._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class OperationStateMutationSuite extends munit.FunSuite {
  private val ready = ReadyGames.of(game.copy(current = game.current.copy(players = game.current.players.map {
      player => player.copy(board = player.board.copy(
        faceUpSecrets = 1, faceDownSecrets = 1))
    }))).copy(banks = MaterialBankState(Map.empty, Map.empty))

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

  private def titled(holder: Option[PlayerId], side: TitleSide): ReadyGame = {
    val Ready(base) = FirstGameSetupFixture.execute(
      new FirstGameSetupRules(FirstGameSetupFixture.catalog))._1: @unchecked
    base.copy(game = base.game.copy(current = base.game.current.copy(
      title = OathkeeperState(holder, side))))
  }
  private def set(ready: ReadyGame, holder: Option[PlayerId]) =
    new OperationExecutor().executeAll(ready, Vector(SetOathkeeper(holder)))
      .map(_.game.current.title)

  test("SetOathkeeper moves the title and always resets it to the Oathkeeper side") {
    val p1 = PlayerId("p1"); val p2 = PlayerId("p2")
    assertEquals(set(titled(None, TitleSide.Oathkeeper), Some(p2)),
      Right(OathkeeperState(Some(p2), TitleSide.Oathkeeper)))
    assertEquals(set(titled(Some(p1), TitleSide.Usurper), Some(p2)),
      Right(OathkeeperState(Some(p2), TitleSide.Oathkeeper)))
    assertEquals(set(titled(Some(p1), TitleSide.Usurper), None),
      Right(OathkeeperState(None, TitleSide.Oathkeeper)))
  }

  test("SetOathkeeper rejects leaving the holder unchanged, whatever the side") {
    val p1 = PlayerId("p1")
    Vector(TitleSide.Oathkeeper, TitleSide.Usurper).foreach { side =>
      assertEquals(set(titled(Some(p1), side), Some(p1)).left.map(_.code),
        Left("oathkeeper-unchanged"))
    }
    assertEquals(set(titled(None, TitleSide.Oathkeeper), None).left.map(_.code),
      Left("oathkeeper-unchanged"))
  }

  private def begin(ready: ReadyGame, operation: BeginTurn) =
    new OperationExecutor().executeAll(ready, Vector(operation))
      .map(_.game.current.turn)

  test("BeginTurn hands the turn to a seated player and clears used powers") {
    val used = PowerUseRef(PowerTiming.Rest,
      PowerSourceRef.Card(DenizenId("92")), PowerId("denizen.silver-tongue"))
    val resting = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(turn = TurnState(playerId, Phase.Rest,
        Set(used)))))
    assertEquals(begin(resting, BeginTurn(playerId, Phase.Wake)),
      Right(TurnState(playerId, Phase.Wake, Set.empty)))
    assertEquals(begin(resting, BeginTurn(playerId, Phase.RoundEnd)),
      Right(TurnState(playerId, Phase.RoundEnd, Set.empty)))
  }

  test("BeginTurn rejects an unseated player and a phase no turn begins in") {
    assertEquals(begin(ready, BeginTurn(PlayerId("nobody"), Phase.Wake))
      .left.map(_.code), Left("unknown-player"))
    Vector(Phase.Act, Phase.Rest, Phase.WarExhaustion).foreach { phase =>
      assertEquals(begin(ready, BeginTurn(playerId, phase)).left.map(_.code),
        Left("invalid-turn-phase"), phase.key)
    }
  }
}
