package oathdigital.gameplay

import oathdigital.gameplay.phases.WakeCommand

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.OathContinue._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation._

/** What the Wake phase still owns once Take Wealth moved onto the walker
  * (batch-1 Task 7): ending the phase. Taking wealth is proved in
  * `TakeWealthProcedureSuite` against the declared tree, and its limit in
  * `TakeWealthPowerSuite`.
  */
class WakeSuite extends munit.FunSuite {
  private val setupRules = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def ready(
      favor: Int = 1,
      secrets: Int = 1,
      sharedEnemy: Boolean = false
  ): OathState = {
    val Ready(value) = execute(setupRules)._1: @unchecked
    val active = value.game.current.turn.activePlayer
    val activeSite = value.game.current.players.find(_.player == active)
      .flatMap(_.pawnSite).get
    val players = value.game.current.players.map { player =>
      if (sharedEnemy && player.player != active)
        player.copy(pawnSite = Some(activeSite))
      else player
    }
    val site = value.game.current.map.sites(activeSite)
    Ready(value.copy(game = value.game.copy(current = value.game.current.copy(
      players = players,
      map = value.game.current.map.copy(sites =
        value.game.current.map.sites.updated(
          activeSite,
          site.copy(tokens = Tokens(favor, secrets))
        ))
    ))))
  }

  test("End Wake is independent and enters Act action selection") {
    val state = ready(favor = 0, secrets = 0, sharedEnemy = true)
    val active = activePlayer(state)
    val accepted = rules.handle(state, WakeCommand.EndWake(active)).toOption.get
    val Ready(value) = accepted.state: @unchecked
    assertEquals(accepted.events, Vector(WakeEnded(active)))
    assertEquals(accepted.continue, ActActionSelection(active))
    assertEquals(value.game.current.turn.phase, Phase.Act)
  }

  test("End Wake remains legal while another player has a revealed Vision") {
    val state = ready()
    val active = activePlayer(state)
    val Ready(value) = state: @unchecked
    val other = value.game.current.players.indexWhere(_.player != active)
    val players = value.game.current.players.updated(
      other,
      value.game.current.players(other).copy(
        revealedVision = Some(VisionState(
          VisionId("V1"),
          Orientation.FaceUp
        ))
      )
    )
    val visionRevealed = Ready(value.copy(game = value.game.copy(current =
      value.game.current.copy(players = players))))

    val accepted = rules.handle(
      visionRevealed,
      WakeCommand.EndWake(active)
    ).toOption.get
    val Ready(after) = accepted.state: @unchecked

    assertEquals(accepted.events, Vector(WakeEnded(active)))
    assertEquals(after.game.current.turn.phase, Phase.Act)
    assertEquals(
      after.game.current.players(other).revealedVision,
      Some(VisionState(VisionId("V1"), Orientation.FaceUp))
    )
  }

  test("End Wake rejects a player who is not the active one") {
    assert(rules.handle(ready(sharedEnemy = true),
      WakeCommand.EndWake(PlayerId("p1"))).left.toOption.get
      .isInstanceOf[WrongPlayer])
  }

  test("wrong phase and limited Oathkeeper Wake remains playable") {
    val state = ready()
    val active = activePlayer(state)
    val ended = rules.handle(state, WakeCommand.EndWake(active)).toOption.get.state
    assertEquals(
      rules.handle(ended, WakeCommand.EndWake(active)).left.toOption.get,
      WrongPhase(Phase.Wake, Phase.Act)
    )
    val Ready(value) = state: @unchecked
    val titled = Ready(value.copy(game = value.game.copy(current =
      value.game.current.copy(title =
        OathkeeperState(Some(active), TitleSide.Oathkeeper)))))
    assert(rules.handle(titled, WakeCommand.EndWake(active)).isRight)
  }

  test("command evolution and replay are equal and corrupt index is exact") {
    val initial = ready()
    val active = activePlayer(initial)
    val ended = rules.handle(initial, WakeCommand.EndWake(active)).toOption.get
    val replay = ended.events.foldLeft[Either[OathViolation, OathState]](
      Right(initial)) { case (next, event) => next.flatMap(rules.evolve(_, event)) }
    assertEquals(replay, Right(ended.state))

    val setupEvents = execute(setupRules)._2
    val corrupt = new EventReplayEngine(rules).replay(
      (setupEvents ++ Vector(WakeEnded(active), WakeEnded(active)))
        .zipWithIndex.map { case (event, index) =>
          RecordedEvent(index.toLong, event)
        }
    )
    assertEquals(corrupt.left.toOption.get.index, 9L)
  }

  private def activePlayer(state: OathState): PlayerId = {
    val Ready(value) = state: @unchecked
    value.game.current.turn.activePlayer
  }
}
