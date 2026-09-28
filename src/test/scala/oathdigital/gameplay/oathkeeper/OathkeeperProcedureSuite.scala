package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay._
import oathdigital.model.OathEvent.BanditsRefilled
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, ParkedDecisionAssertions,
  WalkerCompleted, WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}

class OathkeeperProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val walkerParked = new ParkedDecisionAssertions(catalog)

  /** p1 travels to Broken Peaks: the cheapest walker action whose
    * completion runs the action boundary and moves no forces.
    */
  private def travel(ready: ReadyGame) =
    rules.startWalker(Ready(ready), ActionRef.Travel, p1, Vector.empty,
      Vector(DecisionOptionRef.Site(Table.homeOf(p2))))

  private def replays(start: ReadyGame, events: Vector[OathEvent],
      expected: OathState): Unit =
    assertEquals(events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(start)))((state, event) => state.flatMap(rules.evolve(_, event))),
      Right(expected))

  /** p2 holds the Oathkeeper title while p1 and p3 each rule one site, a
    * tie p2 must break off turn. It is p1's Act. */
  private def tied: Table = Table.start
    .oathkeeper(Some(p2))
    .warbandsAt("Dunes", p1, 1).warbandsAt("Fair Isle", p3, 1)

  private def tie: (ReadyGame, PlayerId, PlayerId, Vector[PlayerId]) =
    (tied.ready, p1, p2, Vector(p1, p3))

  test("a single new leader takes the title inside the action's own command"):
    val leader = p3
    val ready = Table.start.warbandsAt("Dunes", p3, 1).ready
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

  test("a tie parks for the holder, who may be off-turn, and only the holder " +
      "may answer with a tied leader"):
    val (ready, active, holder, leaders) = tie
    val parked = travel(ready).toOption.get
    walkerParked.assertParked(parked.state, TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.recipientDecisionId, holder)
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
    walkerParked.assertResumed(chosen.state, Phase.Act, active)
    val Ready(after) = chosen.state: @unchecked
    assertEquals(after.game.current.title,
      OathkeeperState(Some(leaders(1)), TitleSide.Oathkeeper))
    replays(ready, parked.events ++ chosen.events, chosen.state)

  test("completing the Oathkeeper procedure runs no action boundary"):
    val (ready, _, holder, leaders) = tie
    val parked = travel(ready).toOption.get
    // The Travel's own boundary refilled every empty site before the park.
    // Emptying Desolate Shore again makes a boundary observable: if one ran
    // on the resolving command, it would refill bandits there.
    val Ready(waiting) = parked.state: @unchecked
    val shore = oathdigital.testkit.CatalogNames.site("Desolate Shore")
    val emptied = waiting.updateCurrent(current => current.copy(map =
      current.map.copy(sites = current.map.sites.updated(shore,
        current.map.sites(shore).copy(forces = SiteForces.Empty)))))
    assert(StateBasedEvaluation.banditRefill(catalog, Ready(emptied))
      .toOption.flatten.nonEmpty, "precondition: a boundary would refill here")
    val chosen = rules.resolveWalker(Ready(emptied), holder,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(leaders(0)))).toOption.get
    assertEquals(chosen.events.collect { case e: BanditsRefilled => e }, Vector.empty)

  test("a tie found after Take Wealth parks in Wake and returns the player to Wake"):
    val (active, holder, leaders) = (p1, p2, Vector(p1, p3))
    // p1 wakes at Ancient City, which holds a favor and a secret to take.
    val ready = tied.turn(p1, Phase.Wake)
      .siteTokens(Table.homeOf(p1), favor = 1, secrets = 1).ready
    val parked = TakeWealthFixture.take(rules, ready).toOption.get
    walkerParked.assertParked(parked.state, TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.recipientDecisionId, holder)
    val chosen = rules.resolveWalker(parked.state, holder,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(leaders(0)))).toOption.get
    walkerParked.assertResumed(chosen.state, Phase.Wake, active)

  test("the engine refuses to start a triggered procedure over a pending one"):
    val (ready, _, _, _) = tie
    val parked = travel(ready).toOption.get
    val result = rules.startTriggered(
      OathTransition(parked.state, Vector.empty),
      TriggeredProcedureRef.Oathkeeper)
    assert(result.left.toOption.exists(_.isInstanceOf[OathViolation.InvalidEventOrder]),
      s"expected a typed rejection, got $result")

  test("the procedure rejects a start selection and a state with nothing to change"):
    assertEquals(OathkeeperProcedure.build(catalog, Table.start.ready, p1,
      Vector.empty),
      Left(OathViolation.InvalidEventOrder("no Oathkeeper change to perform")))
    assert(OathkeeperProcedure.build(catalog,
      Table.start.warbandsAt("Dunes", p3, 1).ready, p1,
      Vector(DecisionOptionRef.Site(Table.homeOf(p1)))).isLeft)
