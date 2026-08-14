package oathdigital.gameplay

import oathdigital.gameplay.actions.TravelCommand
import oathdigital.gameplay.phases.{RestCommand, WakeCommand}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState.Ready
import oathdigital.setup.OathViolation._

class StateBasedEvaluationSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def prepared(
      owners: Vector[Option[PlayerId]],
      holder: Option[PlayerId] = None,
      side: TitleSide = TitleSide.Oathkeeper,
      round: Int = 1,
      limited: Boolean = true
  ): ReadyGame = {
    val Ready(base) = execute(setup)._1: @unchecked
    val byPlayer = base.game.current.players.map(p => p.player -> p.lineage).toMap
    val sites = base.game.current.map.inPlay.zipWithIndex.map { case (id, index) =>
      val force = owners.lift(index).flatten.fold[SiteForces](
        SiteForces.Occupied(ForceKind.Bandit, 1))(player =>
        SiteForces.Occupied(ForceKind.Exile(byPlayer(player)), 1))
      id -> base.game.current.map.sites(id).copy(forces = force)
    }.toMap
    base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites = sites),
      title = OathkeeperState(holder, side),
      tracks = base.game.current.tracks.copy(round = round,
        usurperLimited = limited))))
  }

  test("first-game Supremacy qualification transfers at a completed action boundary") {
    val base = prepared(Vector(Some(PlayerId("p2"))))
    val actor = base.game.current.turn.activePlayer
    val act = base.copy(game = base.game.copy(current = base.game.current.copy(
      turn = TurnState(actor, Phase.Act, Set.empty))))
    val destination = base.game.current.map.inPlay.find(
      _ != base.game.current.players.find(_.player == actor).get.pawnSite.get).get
    val accepted = rules.handle(Ready(act), TravelCommand.Travel(actor, destination))
      .toOption.get

    assert(accepted.events.head.isInstanceOf[Traveled])
    assertEquals(accepted.events.last, OathkeeperChanged(Some(PlayerId("p2"))))
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.title,
      OathkeeperState(Some(PlayerId("p2")), TitleSide.Oathkeeper))
    val replayed = accepted.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(act)))((state, event) => state.flatMap(rules.evolve(_, event)))
    assertEquals(replayed, Right(accepted.state))
  }

  test("F7 retains a highest tied holder but does not invent an initial tie winner") {
    val players = execute(setup)._1.asInstanceOf[Ready].value.game.current.players
      .map(_.player)
    val tied = Vector(Some(players(0)), Some(players(1)))
    assertEquals(StateBasedEvaluation.afterAction(Ready(prepared(tied))), Right(None))

    val retained = prepared(tied, holder = Some(players(0)))
    assertEquals(StateBasedEvaluation.afterAction(Ready(retained)), Right(None))
  }

  test("F7 leaves the displaced-holder choice explicit") {
    val players = execute(setup)._1.asInstanceOf[Ready].value.game.current.players
      .map(_.player)
    val state = prepared(Vector(Some(players(0)), Some(players(1))),
      holder = Some(players(2)))
    assert(StateBasedEvaluation.afterAction(Ready(state)).left.toOption.get
      .isInstanceOf[UnsupportedOathkeeperTie])
  }

  test("round four releases limiter and retained Usurper wins next Wake") {
    val base = execute(setup)._1.asInstanceOf[Ready].value
    val holder = base.support.firstPlayer
    val initial = Ready(prepared(Vector(Some(holder)), Some(holder),
      round = 3, limited = true))
    val order = initial.value.game.current.players.map(_.player)
    val start = order.indexOf(holder)
    val turnOrder = order.drop(start) ++ order.take(start)
    var state: OathState = initial
    var events = Vector.empty[OathEvent]

    def accept(transition: Either[OathViolation, OathTransition]): Unit = {
      val accepted = transition.toOption.get
      state = accepted.state
      events ++= accepted.events
    }
    def finishTurn(player: PlayerId): Unit = {
      accept(rules.handle(state, WakeCommand.EndWake(player)))
      accept(rules.handle(state, RestCommand.Begin(player)))
      accept(rules.handle(state, RestCommand.Finish(player)))
    }

    turnOrder.foreach(finishTurn)
    val roundFour = state.asInstanceOf[Ready].value
    assertEquals(roundFour.game.current.tracks.round, 4)
    assertEquals(roundFour.game.current.tracks.usurperLimited, false)
    assertEquals(roundFour.game.current.title,
      OathkeeperState(Some(holder), TitleSide.Usurper))
    assert(events.contains(UsurperFlipped(holder)))

    accept(rules.handle(state, WakeCommand.EndWake(holder)))
    val holderState = state.asInstanceOf[Ready].value
    val destination = holderState.game.current.map.inPlay.find(
      _ != holderState.game.current.players.find(_.player == holder)
        .flatMap(_.pawnSite).get).get
    accept(rules.handle(state, TravelCommand.Travel(holder, destination)))
    assertEquals(state.asInstanceOf[Ready].value.game.current.title,
      OathkeeperState(Some(holder), TitleSide.Usurper))
    accept(rules.handle(state, RestCommand.Begin(holder)))
    accept(rules.handle(state, RestCommand.Finish(holder)))
    turnOrder.tail.foreach(finishTurn)

    val won = state.asInstanceOf[Ready].value
    assertEquals(won.game.current.result, Some(GameResult(holder)))
    assertEquals(events.last, UsurperVictory(holder))
    val replayed = events.foldLeft[Either[OathViolation, OathState]](
      Right(initial))((next, event) => next.flatMap(rules.evolve(_, event)))
    assertEquals(replayed, Right(state))
  }
}
