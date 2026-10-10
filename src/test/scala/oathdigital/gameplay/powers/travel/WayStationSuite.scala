package oathdigital.gameplay.powers.travel

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.powerresolver.{FoldPhase, Transform}
import oathdigital.gameplay.powers.{NoteText, PayOrAsk, PowerFixture,
  RulerToll}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Refusals, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class WayStationSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val wayStation = CatalogNames.denizen("Way Station")
  private val rival = Table.p2

  /** Way Station stands at the coast, which `rival` rules. The actor, at the
    * Cradle's plains, holds `favor` favor and `supply` Supply; a Travel to
    * the coast costs 1. */
  private def rivalRules(favor: Int = 1, supply: Int = 7): ReadyGame =
    on(board(supply = supply))(_.denizen(wayStation, at = coast)
      .warbandsAt(coast, rival, 1).favor(actor, favor))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(WayStation.id, RulerToll.keys, events)

  private val parked = new ParkedDecisionAssertions(catalog, powers(Vector.empty))

  private def assertAsked(state: OathState)(using munit.Location): Unit =
    parked.assertParked(state, ActionRef.Travel, WayStation.decisionId, actor)

  private def answer(state: OathState, choice: DecisionOptionRef.Button)
      : OathTransition = accepted(rules.resolveWalker(state, actor,
    WayStation.decisionId, DecisionAnswer.ChooseOneAnswer(choice)))

  /** The coast also carries Toll Roads' toll of 1 favor on top of the Travel. */
  private def tolled(favor: Int): ReadyGame =
    on(rivalRules(favor = favor))(_
      .denizen(CatalogNames.denizen("Toll Roads"), at = provincePlains)
      .warbandsAt(provincePlains, rival, 1))

  test("its ruler travels there free, unasked"):
    val ready = on(board(supply = 7))(_.denizen(wayStation, at = coast)
      .warbandsAt(coast, actor, 1))
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(MenuPlay.procedureBeyondMenu(result), None)

  test("a non-ruler with favor is asked, and the favor goes to the ruler"):
    val ready = rivalRules()
    val asked = accepted(travel(ready, coast))
    assertAsked(asked.state)
    val done = answer(asked.state, WayStation.favorOption)
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(Look(result).favor(rival), Look(ready).favor(rival) + 1)
    assertEquals(said(done.events), Vector(NoteText.Said("gave",
      s"Gave 1 favor to ${rival.value}.", covers = false)))
    val all = asked.events ++ done.events
    assertEquals(ActPowerHarness.replayed(rules, ready, all), result)
    assertWireRoundTrips(all)

  test("spending the Supply instead posts no Way Station line"):
    val asked = accepted(travel(rivalRules(), coast))
    val done = answer(asked.state, WayStation.supplyOption)
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).supply(actor), 6)
    assertEquals(Look(result).favor(actor), 1)
    assertEquals(said(done.events), Vector.empty)

  test("a non-ruler with no favor is not asked"):
    val done = accepted(travel(rivalRules(favor = 0), coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 6)

  test("a Travel a selected modifier already made free is not asked"):
    val ready = on(rivalRules(favor = 2))(_
      .adviser(actor, CatalogNames.denizen("Tents")))
    val done = accepted(travel(ready, coast, Vector(Tents.id)))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 7)

  test("under bandit rule the favor is burnt"):
    val ready = on(board())(_.denizen(wayStation, at = coast)
      .bandits(coast, 1).favor(actor, 1))
    val done = answer(accepted(travel(ready, coast)).state,
      WayStation.favorOption)
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).favor(actor), 0)
    assertEquals(said(done.events), Vector(NoteText.Said("burned",
      "Burned 1 favor.", covers = false)))

  test("a player short of Supply can still go there by paying the favor"):
    val asked = accepted(travel(rivalRules(supply = 0), coast))
    assertAsked(asked.state)
    assertEquals(rules.resolveWalker(asked.state, actor, WayStation.decisionId,
      DecisionAnswer.ChooseOneAnswer(WayStation.supplyOption)).left.toOption,
      Some(Refusals.notOffered(WayStation.decisionId)))
    val result = Outcomes.ready(answer(asked.state,
      WayStation.favorOption).state)
    assertEquals(Look(result).pawn(actor), coast)

  test("a favor the toll takes first is not offered twice"):
    val stepped = accepted(travel(tolled(favor = 1), coast))
    // Not asked: the toll's step owes the only favor.
    parked.assertParked(stepped.state, ActionRef.Travel, TollRoads.decisionId,
      actor)
    val done = accepted(answeringStep(stepped, TollRoads.decisionId,
      PayOrAsk.pay))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(Look(result).supply(actor), 6)
    assertEquals(Look(result).favor(actor), 0)

  test("with favor for both, both questions are asked before either is " +
      "paid, and the answers pay the toll and the Way Station"):
    val asked = accepted(travel(tolled(favor = 2), coast))
    assertAsked(asked.state)
    val stepped = answer(asked.state, WayStation.favorOption)
    // Still 2 favor: the Way Station pays last.
    assertEquals(Look(Outcomes.ready(stepped.state)).favor(actor), 2)
    val done = accepted(answeringStep(stepped, TollRoads.decisionId,
      PayOrAsk.pay))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).supply(actor), 7)
    assertEquals(Look(result).favor(actor), 0)

  test("it folds Late, after every other Travel power, at no priority"):
    val power = WayStation.forCatalog(catalog)
    assertEquals(power.contributions(PowerWindow.TravelCost).collect {
      case transform: Transform => transform.phase }, Vector(FoldPhase.Late))
    assertEquals(power.priority, 0)
