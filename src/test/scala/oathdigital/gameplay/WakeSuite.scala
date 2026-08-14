package oathdigital.gameplay

import oathdigital.gameplay.phases.{Wake, WakeCommand}

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathContinue._
import oathdigital.setup.OathEvent._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathState.Ready
import oathdigital.setup.OathViolation._

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

  test("Take Wealth transfers favor and remains in Wake") {
    val state = ready()
    val active = activePlayer(state)
    val before = totals(state)
    val accepted = rules.handle(
      state,
      WakeCommand.TakeWealth(active, WakeResource.Favor)
    ).toOption.get
    val Ready(value) = accepted.state: @unchecked

    assertEquals(accepted.events.map(_.getClass.getSimpleName),
      Vector("WealthTaken"))
    assertEquals(accepted.continue, AwaitingWakeAction(active))
    assertEquals(value.game.current.turn.phase, Phase.Wake)
    assertEquals(totals(accepted.state), before)
    assert(value.game.current.turn.usedPowers.contains(
      Wake.takeWealthPower(activeSite(accepted.state))))
  }

  test("Take Wealth transfers a face-up secret") {
    val state = ready()
    val active = activePlayer(state)
    val Ready(before) = state: @unchecked
    val beforeSecrets = before.game.current.players.find(
      _.player == active).get.board.faceUpSecrets
    val accepted = rules.handle(
      state,
      WakeCommand.TakeWealth(active, WakeResource.Secret)
    ).toOption.get
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.players.find(_.player == active).get
      .board.faceUpSecrets, beforeSecrets + 1)
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

  test("Take Wealth rejects enemy pawn missing resource and wrong actor") {
    val state = ready(sharedEnemy = true)
    val active = activePlayer(state)
    assert(rules.handle(state,
      WakeCommand.TakeWealth(active, WakeResource.Favor)).left.toOption.get
      .isInstanceOf[EnemyPawnBlocksTakeWealth])

    val empty = ready(favor = 0)
    assertEquals(
      rules.handle(empty,
        WakeCommand.TakeWealth(activePlayer(empty), WakeResource.Favor))
        .left.toOption.get,
      ResourceUnavailable(activeSite(empty), WakeResource.Favor)
    )
    assert(rules.handle(state,
      WakeCommand.EndWake(PlayerId("p1"))).left.toOption.get
      .isInstanceOf[WrongPlayer])
  }

  test("a power instance cannot repeat at one site but differs by site") {
    val state = ready()
    val active = activePlayer(state)
    val once = rules.handle(state,
      WakeCommand.TakeWealth(active, WakeResource.Favor)).toOption.get.state
    assert(rules.handle(once,
      WakeCommand.TakeWealth(active, WakeResource.Secret)).left.toOption.get
      .isInstanceOf[PowerAlreadyUsed])
    assertNotEquals(
      Wake.takeWealthPower(activeSite(state)),
      Wake.takeWealthPower(sites.find(_ != activeSite(state)).get)
    )
  }

  test("Take Wealth command legality and private projection agree") {
    Vector(
      ready(),
      ready(favor = 0),
      ready(secrets = 0),
      ready(sharedEnemy = true)
    ).foreach { state =>
      val Ready(value) = state: @unchecked
      val active = value.game.current.turn.activePlayer
      val projection = new oathdigital.application.GameProjector(catalog)
        .project("take-wealth",
          oathdigital.application.LoadedGame(state, 9), active)
      val favorLegal = rules.handle(state,
        WakeCommand.TakeWealth(active, WakeResource.Favor)).isRight
      val secretLegal = rules.handle(state,
        WakeCommand.TakeWealth(active, WakeResource.Secret)).isRight
      assertEquals(projection.legalControls.contains("takeFavor"), favorLegal)
      assertEquals(projection.legalControls.contains("takeSecret"), secretLegal)
    }
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
    val wealth = rules.handle(initial,
      WakeCommand.TakeWealth(active, WakeResource.Favor)).toOption.get
    val ended = rules.handle(wealth.state,
      WakeCommand.EndWake(active)).toOption.get
    val events = wealth.events ++ ended.events
    val replay = events.zipWithIndex.foldLeft[
      Either[OathViolation, OathState]](Right(initial)) {
      case (next, (event, _)) => next.flatMap(rules.evolve(_, event))
    }
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

  private def activeSite(state: OathState): SiteId = {
    val Ready(value) = state: @unchecked
    value.game.current.players.find(
      _.player == value.game.current.turn.activePlayer).flatMap(_.pawnSite).get
  }

  private def totals(state: OathState): (Int, Int) = {
    val Ready(value) = state: @unchecked
    val playerTokens = value.game.current.players.foldLeft((0, 0)) {
      case ((favor, secrets), player) =>
        (favor + player.board.favor,
          secrets + player.board.faceUpSecrets + player.board.faceDownSecrets)
    }
    value.game.current.map.sites.values.foldLeft(playerTokens) {
      case ((favor, secrets), site) =>
        (favor + site.tokens.favor, secrets + site.tokens.secrets)
    }
  }
}
