package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.Look
import oathdigital.testkit.CatalogNames

/** Horse Archers: a free plan for three attack dice, added or removed, and the
  * card is discarded after the Campaign whoever won. */
class HorseArchersSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Horse Archers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val line = NoteText.Said("discarded", "Discarded after the Campaign.",
    covers = false)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(HorseArchers.id, Vector(PlanDiscard.discarded), run.events)

  test("an attacker adds three attack dice, for nothing"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is discarded after a Campaign its user won, and says so"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(winning), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(line))

  test("it is discarded after a Campaign its user lost"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(losing), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(line))

  test("a defender removes three attack dice, and it is discarded"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(base, base.rival, card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    assert(Look(run.finishPlanWindows.state).inRegionalDiscard(card))

  test("a bandit defender applies it at its site, and it is discarded"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(line))

  test("a Campaign that does not choose it leaves it in play and says nothing"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(winning), b, 2).finishPlanWindows
    assert(!Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector.empty)
