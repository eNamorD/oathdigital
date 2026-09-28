package oathdigital.gameplay

import oathdigital.model.OathEvent.IgnoredRulesRecorded
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.phases.rest.WarExhaustionRandomPort
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.model._
import oathdigital.testkit.Table

/** Walker Begin Rest and Finish Rest, driven through `startWalker`. */
class RestWalkerSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)

  /** p1's Act. Broken Peaks and Buried Giant, where p2 and p3 stand, each
    * hold two secrets, so their Wakes have wealth to take and wait; p1's
    * Ancient City holds none. */
  private val act: ReadyGame = Table.start
    .siteTokens(Table.homeOf(Table.p2), secrets = 2)
    .siteTokens(Table.homeOf(Table.p3), secrets = 2)
    .ready
  private val actor = act.game.current.turn.activePlayer
  private def inRest(ready: ReadyGame) = ready.updateCurrent(_.copy(turn = ready.game.current.turn.copy(
      phase = Phase.Rest)))
  private def rest(state: OathState, player: PlayerId, using: OathRules = rules) =
    using.startWalker(state, PhaseTransitionRef.BeginRest, player)
  private def ready(state: OathState) = state.asInstanceOf[Ready].value

  test("Begin Rest with no usable REST power finishes Rest in the same command"):
    val rested = rest(Ready(act), actor).toOption.get
    assertEquals(rested.events.collect { case WalkerCompleted(p) => p },
      Vector(PhaseTransitionRef.BeginRest, PhaseTransitionRef.FinishRest))
    val next = ready(rested.state).game.current.turn
    assertEquals(next.phase, Phase.Wake)
    assertNotEquals(next.activePlayer, actor)
    assertEquals(rested.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(act)))((state, event) => state.flatMap(rules.evolve(_, event))),
      Right(rested.state))

  test("Finish Rest belongs to the active player in the Rest phase"):
    val other = act.game.current.players.map(_.player).find(_ != actor).get
    assertEquals(rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.FinishRest, other).left.toOption,
      Some(OathViolation.WrongPlayer(actor, other)))
    assertEquals(rules.startWalker(Ready(act), PhaseTransitionRef.FinishRest,
      actor).left.toOption, Some(OathViolation.WrongPhase(Phase.Rest, Phase.Act)))
    assertEquals(rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.BeginRest, actor).left.toOption,
      Some(OathViolation.WrongPhase(Phase.Act, Phase.Rest)))
    val finished = rules.startWalker(Ready(inRest(act)),
      PhaseTransitionRef.FinishRest, actor).toOption.get
    assertEquals(ready(finished.state).game.current.turn.phase, Phase.Wake)

  test("the last player's Rest ends the round and wakes the first player"):
    val order =
      val participants = act.game.current.players.map(_.player)
      val start = participants.indexOf(act.setup.firstPlayer)
      participants.drop(start) ++ participants.take(start)
    val last = act.updateCurrent(_.copy(
      turn = TurnState(order.last, Phase.Act, Set.empty)))
    val rested = rest(Ready(last), order.last).toOption.get
    assert(rested.events.exists(_.isInstanceOf[OathEvent.RoundEnded]))
    val after = ready(rested.state).game.current
    assertEquals(after.tracks.round, act.game.current.tracks.round + 1)
    assertEquals(after.turn.activePlayer, order.head)
    // The first player's Wake has nothing to decide on this board, so it
    // ends in the same command.
    assertEquals(after.turn.phase, Phase.Act)
    assert(rested.events.contains(
      oathdigital.gameplay.walker.WalkerCompleted(PhaseTransitionRef.EndWake)))

  test("the last player of round eight finishes the game by War Exhaustion"):
    val order =
      val participants = act.game.current.players.map(_.player)
      val start = participants.indexOf(act.setup.firstPlayer)
      participants.drop(start) ++ participants.take(start)
    val eighth = act.updateCurrent(_.copy(
      tracks = act.game.current.tracks.copy(round = 8),
      turn = TurnState(order.last, Phase.Act, Set.empty)))
    val deterministic = new OathRules(catalog, warExhaustionRandomPort =
      new WarExhaustionRandomPort {
        def choose(candidates: Vector[PlayerId]) = candidates.last
      })
    val finished = rest(Ready(eighth), order.last, deterministic).toOption.get
    assert(finished.events.exists(_.isInstanceOf[OathEvent.WarExhaustionResolved]))
    assertEquals(ready(finished.state).game.current.result.map(_.winner),
      Some(order.last))

  test("walker Begin Rest records the Rest fallback diagnostics first"):
    val definition = catalog.denizens.find(_.handlers.contains(
      "denizen.naysayers")).get
    val advised = act.updateCurrent(_.copy(
      players = act.game.current.players.map(p => if p.player != actor then p
        else p.copy(advisers = Vector(DenizenState(DenizenId(definition.id.value),
          Orientation.FaceUp, Tokens.empty))))))
    val rested = rest(Ready(advised), actor).toOption.get
    assertEquals(rested.events.head.asInstanceOf[IgnoredRulesRecorded]
      .diagnostics.head.handlerId, "denizen.naysayers")
