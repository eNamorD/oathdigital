package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay._
import oathdigital.model.OathEvent.BanditsRefilled
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.oathkeeper.OathkeeperFixture._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

class OathkeeperProcedureSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog)

  /** A Travel by the active player: the cheapest walker action whose
    * completion runs the action boundary and moves no forces.
    */
  private def travel(ready: ReadyGame) = {
    val active = ready.game.current.turn.activePlayer
    val pawn = ready.game.current.players.find(_.player == active).get.pawnSite.get
    val destination = ready.game.current.map.inPlay.find(_ != pawn).get
    rules.startWalker(Ready(ready), ActionRef.Travel, active, Vector.empty,
      Vector(DecisionOptionRef.Site(destination)))
  }

  private def replays(start: ReadyGame, events: Vector[OathEvent],
      expected: OathState): Unit =
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(start)))((state, event) => state.flatMap(rules.evolve(_, event))),
      Right(expected))

  /** Holder is not the active player; the two leaders are everyone else. */
  private def tie: (ReadyGame, PlayerId, PlayerId, Vector[PlayerId]) = {
    val active = base.game.current.turn.activePlayer
    val holder = players.find(_ != active).get
    val leaders = players.filterNot(_ == holder).take(2)
    (inPhase(ruled(base, leaders.map(Some(_)), holder = Some(holder)),
      Phase.Act), active, holder, leaders)
  }

  test("a single new leader takes the title inside the action's own command") {
    val leader = players.last
    val ready = inPhase(ruled(base, Vector(Some(leader))), Phase.Act)
    val accepted = travel(ready).toOption.get
    assert(accepted.events.exists {
      case step: WalkerStepRecorded => step.ops == Vector(SetOathkeeper(Some(leader)))
      case _ => false
    })
    assertEquals(accepted.events.last,
      WalkerCompleted(TriggeredProcedureRef.Oathkeeper): OathEvent)
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.title,
      OathkeeperState(Some(leader), TitleSide.Oathkeeper))
    assertEquals(after.game.current.walkerPending, None)
    replays(ready, accepted.events, accepted.state)
  }

  test("a tie parks for the holder, who may be off-turn, and only the holder " +
      "may answer with a tied leader") {
    val (ready, active, holder, leaders) = tie
    val parked = travel(ready).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingOathkeeperRecipient(
      holder, DecisionId(OathkeeperProcedure.recipientDecisionId)))
    assert(parked.events.last.isInstanceOf[WalkerParked])
    val Ready(waiting) = parked.state: @unchecked
    assertEquals(waiting.game.current.walkerProcedure,
      Some(TriggeredProcedureRef.Oathkeeper))

    def answer(by: PlayerId, chosen: PlayerId) = rules.resolveWalker(parked.state,
      by, OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(chosen)))

    assertEquals(answer(active, leaders(0)),
      Left(OathViolation.WrongPlayer(holder, active)))
    assertEquals(answer(holder, holder),
      Left(OathViolation.InvalidEventOrder(
        "decision oathkeeper.recipient does not offer the selected option")),
      "a player who is not a tied leader is not a legal recipient")

    val chosen = answer(holder, leaders(1)).toOption.get
    assert(chosen.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == holder
      case _ => false
    })
    assertEquals(chosen.continue, OathContinue.ActActionSelection(active))
    val Ready(after) = chosen.state: @unchecked
    assertEquals(after.game.current.title,
      OathkeeperState(Some(leaders(1)), TitleSide.Oathkeeper))
    replays(ready, parked.events ++ chosen.events, chosen.state)
  }

  test("completing the Oathkeeper procedure runs no action boundary") {
    val (ready, _, holder, leaders) = tie
    val parked = travel(ready).toOption.get
    // An empty site with capacity makes a boundary observable: if one ran on
    // the resolving command, it would refill bandits here.
    val Ready(waiting) = parked.state: @unchecked
    val empty = waiting.game.current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.capacity > 0) &&
        waiting.game.current.map.sites(id).forces ==
          SiteForces.Occupied(ForceKind.Bandit, 1)).get
    val emptied = waiting.updateCurrent(_.copy(map = waiting.game.current.map.copy(sites =
        waiting.game.current.map.sites.updated(empty,
          waiting.game.current.map.sites(empty).copy(forces = SiteForces.Empty)))))
    assert(StateBasedEvaluation.banditRefill(catalog, Ready(emptied))
      .toOption.flatten.nonEmpty, "precondition: a boundary would refill here")
    val chosen = rules.resolveWalker(Ready(emptied), holder,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(leaders(0)))).toOption.get
    assertEquals(chosen.events.collect { case e: BanditsRefilled => e }, Vector.empty)
  }

  test("a tie found after Take Wealth parks in Wake and returns the player to Wake") {
    val active = base.game.current.turn.activePlayer
    val holder = players.find(_ != active).get
    val leaders = players.filterNot(_ == holder).take(2)
    val ready = TakeWealthFixture.wakeReady(ruled(base, leaders.map(Some(_)),
      holder = Some(holder)))
    val parked = TakeWealthFixture.take(rules, ready).toOption.get
    assertEquals(parked.continue, OathContinue.AwaitingOathkeeperRecipient(
      holder, DecisionId(OathkeeperProcedure.recipientDecisionId)))
    val chosen = rules.resolveWalker(parked.state, holder,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(leaders(0)))).toOption.get
    assertEquals(chosen.continue, OathContinue.AwaitingWakeAction(active))
  }

  test("the engine refuses to start a triggered procedure over a pending one") {
    val (ready, _, _, _) = tie
    val parked = travel(ready).toOption.get
    val result = rules.startTriggered(
      OathTransition(parked.state, Vector.empty, parked.continue),
      TriggeredProcedureRef.Oathkeeper)
    assert(result.left.toOption.exists(_.isInstanceOf[OathViolation.InvalidEventOrder]),
      s"expected a typed rejection, got $result")

    // A legacy (non-walker) pending procedure is refused the same way.
    val actor = ready.game.current.players
      .find(_.player == ready.game.current.turn.activePlayer).get
    val legacy = ready.updateCurrent(_.copy(
      pending = Some(PendingProcedure.Challenge(DecisionId("legacy-pending"),
        actor.player, Banner.PeoplesFavor, None, 0, 1))))
    assertEquals(legacy.game.current.walkerPending, None)
    assertEquals(legacy.game.current.walkerProcedure, None)
    val refused = rules.startTriggered(OathTransition(Ready(legacy), Vector.empty,
      OathContinue.ActActionSelection(actor.player)), TriggeredProcedureRef.Oathkeeper)
    assert(refused.left.toOption.exists(_.isInstanceOf[OathViolation.InvalidEventOrder]),
      s"expected a typed rejection over a legacy pending procedure, got $refused")
  }

  test("the procedure rejects a start selection and a state with nothing to change") {
    val ready = inPhase(base, Phase.Act)
    assertEquals(OathkeeperProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty),
      Left(OathViolation.InvalidEventOrder("no Oathkeeper change to perform")))
    assert(OathkeeperProcedure.build(catalog, ruled(ready, Vector(Some(players.last))),
      ready.game.current.turn.activePlayer,
      Vector(DecisionOptionRef.Site(ready.game.current.map.inPlay.head))).isLeft)
  }
}
