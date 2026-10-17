package oathdigital.gameplay

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.campaign.{PlanDriver, SecondWindCard}
import oathdigital.gameplay.powers.travel.SpecialEnvoy
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** A queued walk belongs to its entry's actor, on another player's turn
  * (catalog batch 8, slice 14, rulings 1, 2 and 5). */
class OffTurnWalkSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val buriedGiant = CatalogNames.site("Buried Giant")
  private val secondWind = PowerSourceRef.Card(SecondWindCard.id)

  /** An offer owed to p2 on p1's turn, sourced by Second Wind. */
  private def owedToP2(action: ActionRef): QueuedProcedure =
    QueuedProcedure(p2, action, Vector.empty, Some(secondWind),
      QueueKind.Offer)

  /** The quiet table, p1's Act, with p2 owed `action`. */
  private def p2OwedOnP1sTurn(action: ActionRef): Table =
    Table.start.offering(owedToP2(action))

  private def spent(events: Vector[OathEvent]): Vector[SpendSupply] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten
      .collect { case spend: SpendSupply => spend }

  /** Who the parked decision awaits, as the projection reads it. */
  private def awaitedBy(state: OathState): Option[PlayerId] =
    PlanDriver.parked.parkedDecision(state).map(_.awaiting)

  private def parkedDecisionId(state: OathState): Option[String] =
    PlanDriver.parked.parkedDecision(state).map(_.decision)

  test("an off-turn Travel is walked and answered by its actor, for no " +
      "Supply"):
    val taken = accepted(FollowUpPlay.taken(rules,
      p2OwedOnP1sTurn(ActionRef.Travel).state, p2))
    assertEquals(awaitedBy(taken.state), Some(p2))
    val refused = rules.resolveWalker(taken.state, p1,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(buriedGiant)))
    assertEquals(refused.left.toOption,
      Some(OathViolation.WrongPlayer(p2, p1)))
    val done = accepted(rules.resolveWalker(taken.state, p2,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(buriedGiant))))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.pawn(p2), buriedGiant)
    assertEquals(after.pawn(p1), ancientCity)
    assertEquals(after.supply(p2), 7)
    assertEquals(spent(done.events), Vector.empty)
    assertEquals(after.phase, Phase.Act)
    assertEquals(after.active, p1)

  test("an off-turn Campaign is walked by its actor as the attacker"):
    // Two bandits rule Broken Peaks, where p2 stands alone.
    val table = p2OwedOnP1sTurn(ActionRef.Campaign).bandits(brokenPeaks, 2)
    val taken = accepted(FollowUpPlay.taken(rules, table.state, p2))
    assertEquals(awaitedBy(taken.state), Some(p2))
    val parked = Outcomes.ready(taken.state).game.current
    assertEquals(parked.walkerEntry.map(_.actor), Some(p2))
    assertEquals(parked.walkActor, p2)
    // Conquest of the bandits is p2's only kind, so the force is asked.
    assertEquals(parkedDecisionId(taken.state), Some(CampaignIds.force))

  test("a menu start still requires the turn's player"):
    assertEquals(rules.startWalker(Table.start.state, ActionRef.Travel, p2)
      .left.toOption, Some(OathViolation.WrongPlayer(p1, p2)))

  test("a walk with no entry belongs to the turn's player"):
    assertEquals(Table.start.ready.game.current.walkActor, p1)

  test("an off-turn Travel with Special Envoy spends no Supply and leaves " +
      "the Act Phase running"):
    val board = p2OwedOnP1sTurn(ActionRef.Travel)
      .adviser(p2, "Special Envoy").state
    val taken = accepted(FollowUpPlay.taken(rules, board, p2))
    val started = accepted(ModifierPlay.picking(rules, taken, p2,
      PowerWindow.TravelModifierSelection, Vector(SpecialEnvoy.id)))
    val done = accepted(rules.resolveWalker(started.state, p2,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(buriedGiant))))
    val after = Look(Outcomes.ready(done.state))
    assertEquals(after.phase, Phase.Act)
    assertEquals(after.pawn(p2), buriedGiant)
    assertEquals(spent(done.events), Vector.empty)
    assert(!PhasePowerHarness.ops(done.events).contains(
      EnterPhase(Phase.Rest)), done.events)

  test("a replayed off-turn Travel reaches the same state"):
    val from = p2OwedOnP1sTurn(ActionRef.Travel).ready
    val taken = accepted(FollowUpPlay.taken(rules, OathState.Ready(from),
      p2))
    val done = accepted(rules.resolveWalker(taken.state, p2,
      TravelProcedure.destinationDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Site(buriedGiant))))
    assertEquals(ActPowerHarness.replayed(rules, from,
      taken.events ++ done.events), Outcomes.ready(done.state))
