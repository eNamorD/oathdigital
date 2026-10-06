package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Scouts: a free attacker's plan that gains a Supply when it is applied. */
class ScoutsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Scouts")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  test("the attacker gains a Supply when it is chosen, and says so"):
    val b = withAdviser(board(warbands = 5, supply = 4), card,
      Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(GainSupply(b.actor, 1)))
    // The Campaign spent two of the four; Scouts gives one back.
    assertEquals(Look(picked.state).player(b.actor).board.supply.supply, 3)
    assertEquals(NoteText.said(Scouts.id, Scouts.noteKeys, picked.events),
      Vector(NoteText.Said("gained", "Gained 1 Supply.", covers = false)))

  test("a defender is not offered it"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(base, base.rival, card, Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))
