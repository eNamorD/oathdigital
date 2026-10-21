package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}

/** Hearts and Minds: three favor placed; the defender wins without a roll,
  * and the card is discarded after the Campaign unless its user holds the
  * People's Favor. */
class HeartsAndMindsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Hearts and Minds")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val power = HeartsAndMinds.forCatalog(catalog)

  /** p2 rules the origin with 2 warbands and the Oathkeeper title, Hearts
    * and Minds stands there, and p2 has `favor` favor; `favored` says
    * whether p2 holds the People's Favor. p1 has 5 warbands. */
  private def defender(favor: Int, favored: Boolean): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withSiteCard(base, base.origin, card))(_.favor(base.rival, favor)
      .peoplesFavor(Option.when(favored)(base.rival), favor = 1))

  /** `run` with its parked state changed by Table steps, as if something
    * earlier in the Campaign had changed the board. */
  private def runWithTableChanged(run: Run)(steps: Table => Table)(
      using munit.Location): Run =
    run.copy(transition = run.transition.copy(state =
      OathState.Ready(steps(Table.from(ready(run.state))).ready)))

  test("the defender wins without a roll, the attacker loses half the " +
      "force, and the People's Favor keeps the card"):
    val b = defender(favor = 3, favored = true)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(done.result.attackFaces, Vector.empty)
    assertEquals(done.result.sacrificed, 0)
    // Half of the four committed warbands die.
    assertEquals(Look(done.state).boardWarbands(b.actor), 3)
    assert(Look(done.state).cardsAt(b.origin).contains(card))
    assertEquals(said(power, done.events), Vector.empty)

  test("without the People's Favor it is discarded after the Campaign"):
    val b = defender(favor = 3, favored = false)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(said(power, done.events), Vector(NoteText.Said("discarded",
      "Discarded after the Campaign.", covers = false)))

  test("a defender without three favor sees the shortfall"):
    val b = defender(favor = 2, favored = true)
    val run = commit(rules(winningDice), b, 4)
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 3 favor; you have 2")))

  // No rule yet gives a defender the People's Favor while it wins with
  // Hearts and Minds, so these tests move the banner in the parked state,
  // after the pick and before the end of the Campaign.
  test("a People's Favor won during the Campaign keeps the card"):
    val b = defender(favor = 3, favored = false)
    val picked = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(awaits(picked, b.rival, CampaignIds.defenderPlan))
    val done = runWithTableChanged(picked)(
      _.peoplesFavor(Some(b.rival), favor = 1)).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(Look(done.state).cardsAt(b.origin).contains(card))
    assertEquals(said(power, done.events), Vector.empty)

  test("a People's Favor lost during the Campaign discards the card"):
    val b = defender(favor = 3, favored = true)
    val picked = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(awaits(picked, b.rival, CampaignIds.defenderPlan))
    val done = runWithTableChanged(picked)(
      _.peoplesFavor(None, favor = 1)).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(said(power, done.events), Vector(NoteText.Said("discarded",
      "Discarded after the Campaign.", covers = false)))
