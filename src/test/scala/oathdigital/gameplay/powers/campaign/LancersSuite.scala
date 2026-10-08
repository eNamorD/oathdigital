package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Lancers: a free attacker's plan that doubles the count of every face
  * rolled, before the skull cap, and is discarded after the Campaign. */
class LancersSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Lancers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val doubled = NoteText.Said("doubled",
    "The attack roll was doubled.", covers = false)
  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Lancers.id, Vector(Lancers.doubled, PlanDiscard.discarded),
      run.events)

  // The Campaign's one proof that the card rescores: three hollow swords
  // are worth one sword undoubled and three doubled. The score math, with the
  // rulings' example, is AttackScoreSuite's: its "Lancers doubles every
  // face" and "the rulings' example" tests survive.
  test("doubled hollow swords make whole swords in pairs, and Lancers is " +
      "discarded after the Campaign"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(losingDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Three hollow swords doubled are six, worth three swords.
    assertEquals(done.result.attackScore, 3)
    assertEquals(lines(done), Vector(doubled, discarded))
    assert(Look(done.state).inRegionalDiscard(card))
