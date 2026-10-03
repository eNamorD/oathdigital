package oathdigital.gameplay.oathkeeper

import oathdigital.gameplay.{OathRules, StateBasedEvaluation}
import oathdigital.model.OathEvent.BanditsRefilled
import oathdigital.model.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, ParkedDecisionAssertions,
  WalkerCompleted, WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Outcomes,
  TakeWealthFixture, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class OathkeeperProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val walkerParked = new ParkedDecisionAssertions(catalog)

  /** p1 travels to Broken Peaks: the cheapest walker action whose
    * completion runs the action boundary and moves no forces.
    */
  private def travel(ready: ReadyGame) =
    rules.startWalker(Ready(ready), ActionRef.Travel, p1, Vector.empty,
      Vector(DecisionOptionRef.Site(CatalogNames.site("Broken Peaks"))))

  private def replays(start: ReadyGame, events: Vector[OathEvent],
      expected: OathState): Unit =
    assertEquals(Ready(ActPowerHarness.replayed(rules, start, events)),
      expected)

  /** p2 holds the Oathkeeper title while p1 and p3 each rule one site, a
    * tie p2 must break off turn. It is p1's Act. */
  private def tied: Table = Table.start
    .oathkeeper(Some(p2))
    .warbandsAt("Dunes", p1, 1).warbandsAt("Fair Isle", p3, 1)
  private val active = p1
  private val holder = p2

  test("a single new leader takes the title inside the action's own command"):
    val leader = p3
    val ready = Table.start.warbandsAt("Dunes", p3, 1).ready
    val traveled = accepted(travel(ready))
    assert(traveled.events.exists {
      case step: WalkerStepRecorded =>
        step.ops == Vector(SetOathkeeper(Some(leader)))
      case _ => false
    })
    assertEquals(traveled.events.last,
      WalkerCompleted(TriggeredProcedureRef.Oathkeeper): OathEvent)
    val after = Outcomes.ready(traveled.state)
    assertEquals(after.game.current.title,
      OathkeeperState(Some(leader), TitleSide.Oathkeeper))
    assertEquals(after.game.current.walkerPending, None)
    replays(ready, traveled.events, traveled.state)

  test("a tie parks for the holder, who may be off-turn, and only the holder " +
      "may answer with a tied leader"):
    val ready = tied.ready
    val parked = accepted(travel(ready))
    walkerParked.assertParked(parked.state, TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.recipientDecisionId, holder)
    assert(parked.events.last.isInstanceOf[WalkerParked])
    assertEquals(Outcomes.ready(parked.state).game.current.walkerProcedure,
      Some(TriggeredProcedureRef.Oathkeeper))

    def answer(by: PlayerId, chosen: PlayerId) = rules.resolveWalker(parked.state,
      by, OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(chosen)))

    assertEquals(answer(active, p1),
      Left(OathViolation.WrongPlayer(holder, active)))
    assertEquals(answer(holder, holder),
      Left(OathViolation.InvalidEventOrder(
        "decision oathkeeper.recipient does not offer the selected option")),
      "a player who is not a tied leader is not a legal recipient")

    // p2 gives the title to p3, a tied leader.
    val chosen = accepted(answer(holder, p3))
    assert(chosen.events.exists {
      case WalkerStepRecorded(_, ChoicePayload(_, _, by), _, _) => by == holder
      case _ => false
    })
    walkerParked.assertResumed(chosen.state, Phase.Act, active)
    assertEquals(Outcomes.ready(chosen.state).game.current.title,
      OathkeeperState(Some(p3), TitleSide.Oathkeeper))
    replays(ready, parked.events ++ chosen.events, chosen.state)

  test("completing the Oathkeeper procedure runs no action boundary"):
    val parked = accepted(travel(tied.ready))
    // The Travel's own boundary refilled every empty site before the park.
    // Emptying Desolate Shore again makes a boundary observable: if one ran
    // on the resolving command, it would refill bandits there.
    val emptied = Table.from(Outcomes.ready(parked.state))
      .bandits("Desolate Shore", 0).unchecked
    assert(StateBasedEvaluation.banditRefill(catalog, Ready(emptied))
      .toOption.flatten.nonEmpty, "precondition: a boundary would refill here")
    val chosen = accepted(rules.resolveWalker(Ready(emptied), holder,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(p1))))
    assertEquals(chosen.events.collect { case e: BanditsRefilled => e },
      Vector.empty)

  test("a tie found after Take Wealth parks in Wake and returns the player to Wake"):
    // p1 wakes at Ancient City, which holds a favor and a secret to take.
    val ready = tied.turn(p1, Phase.Wake)
      .siteTokens("Ancient City", favor = 1, secrets = 1).ready
    val parked = accepted(TakeWealthFixture.take(rules, ready))
    walkerParked.assertParked(parked.state, TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.recipientDecisionId, holder)
    val chosen = accepted(rules.resolveWalker(parked.state, holder,
      OathkeeperProcedure.recipientDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Player(p1))))
    walkerParked.assertResumed(chosen.state, Phase.Wake, active)

  test("the engine refuses to start a triggered procedure over a pending one"):
    val parked = accepted(travel(tied.ready))
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
      Vector(DecisionOptionRef.Site(CatalogNames.site("Ancient City")))).isLeft)
