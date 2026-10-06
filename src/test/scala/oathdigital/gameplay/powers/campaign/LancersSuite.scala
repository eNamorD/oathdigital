package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.AttackDieFace.{OneSword, TwoSwordsSkull}
import oathdigital.testkit.{CatalogNames, Look}

/** Lancers: a free attacker's plan that doubles the count of every face
  * rolled, before the skull cap, and is discarded after the Campaign. */
class LancersSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Lancers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val general = CatalogNames.denizen("Fearsome General")
  private val doubled = NoteText.Said("doubled",
    "The attack roll was doubled.", covers = false)
  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Lancers.id, Vector(Lancers.doubled, PlanDiscard.discarded),
      run.events)

  test("the rulings' example: with Fearsome General, a force of 3 rolling " +
      "two skulls and a sword attacks for 14 and loses 3"):
    val b = withAdviser(withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp), general, Orientation.FaceUp)
    val roll = dice(attack = Vector(TwoSwordsSkull, TwoSwordsSkull, OneSword),
      defense = Vector.fill(2)(DefenseDieFace.Blank))
    val done = commit(rules(roll), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(general)).finishPlanWindows
    assertEquals(done.result.attackScore, 14)
    assertEquals(done.result.skullLosses, 3)
    assertEquals(lines(done), Vector(doubled, discarded))
    assertEquals(NoteText.said(FearsomeGeneral.id, FearsomeGeneral.noteKeys,
      done.events), Vector(NoteText.Said("added",
      "Skulls added 6 more swords.", covers = false)))
    assert(Look(done.state).inRegionalDiscard(card))

  test("doubled hollow swords make whole swords in pairs"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(losingDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Three hollow swords doubled are six, worth three swords.
    assertEquals(done.result.attackScore, 3)
    assertEquals(lines(done), Vector(doubled, discarded))
