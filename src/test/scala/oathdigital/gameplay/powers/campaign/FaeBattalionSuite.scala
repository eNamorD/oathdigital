package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Fae Battalion: a free attacker's plan, offered while its user holds the
  * Darkest Secret, that counts each hollow sword as a sword. */
class FaeBattalionSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Fae Battalion")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val counted = NoteText.Said("counted",
    "Hollow swords counted as swords.", covers = false)

  /** p1 holds Fae Battalion and Longbows faceup; Longbows keeps the
    * attacker's window open. */
  private def attacker: Board = withAdviser(withAdviser(
    board(warbands = 5, supply = 7), card, Orientation.FaceUp), "Longbows",
    Orientation.FaceUp)

  test("with the Darkest Secret each hollow sword counts as a sword, and it " +
      "says so"):
    val base = attacker
    val b = on(base)(_.darkestSecret(Some(base.actor), secrets = 1))
    val done = commit(rules(losingDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Three hollow swords score 3 instead of 1.
    assertEquals(done.result.attackScore, 3)
    assertEquals(NoteText.said(FaeBattalion.id, FaeBattalion.noteKeys,
      done.events), Vector(counted))

  test("it is not offered while its user lacks the Darkest Secret"):
    val base = attacker
    val b = on(base)(_.darkestSecret(Some(base.rival), secrets = 1))
    val run = commit(rules(losingDice), b, 3)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    assert(!run.offered(b.actor).contains(ref))
