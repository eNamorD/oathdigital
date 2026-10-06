package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.AttackDieFace.TwoSwordsSkull
import oathdigital.testkit.CatalogNames

/** Fearsome General: a free attacker's plan; each skull face is worth four
  * swords under every rule the face follows. */
class FearsomeGeneralSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Fearsome General")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows: DecisionOptionRef =
    DecisionOptionRef.Denizen(CatalogNames.denizen("Longbows"))
  private val outriders: DecisionOptionRef =
    DecisionOptionRef.Denizen(CatalogNames.denizen("Outriders"))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(FearsomeGeneral.id, FearsomeGeneral.noteKeys, run.events)

  private def added(n: Int): NoteText.Said = NoteText.Said("added",
    s"Skulls added $n more swords.", covers = false)

  /** p1 holds Fearsome General and Longbows faceup, and commits one warband
    * and Longbows' die: two dice, both skull faces. */
  private def twoSkullsForOneWarband(extra: Vector[DecisionOptionRef]): Run =
    val b = withAdviser(withAdviser(withAdviser(board(warbands = 5,
      supply = 7), card, Orientation.FaceUp), "Longbows", Orientation.FaceUp),
      "Outriders", Orientation.FaceUp)
    val roll = dice(attack = Vector.fill(2)(TwoSwordsSkull),
      defense = Vector.fill(2)(DefenseDieFace.Blank))
    (Vector(ref, longbows) ++ extra).foldLeft(commit(rules(roll), b, 1))(
      (run, plan) => run.pick(b.actor, CampaignIds.attackerPlan, plan))
      .finishPlanWindows

  test("an unpayable skull scores nothing, so only the payable one adds"):
    val done = twoSkullsForOneWarband(Vector.empty)
    assertEquals(done.result.attackScore, 4)
    assertEquals(done.result.skullLosses, 1)
    assertEquals(lines(done), Vector(added(2)))

  test("an ignored skull scores four swords"):
    val done = twoSkullsForOneWarband(Vector(outriders))
    assertEquals(done.result.attackScore, 8)
    assertEquals(done.result.skullLosses, 0)
    assertEquals(lines(done), Vector(added(4)))

  test("with no skull rolled it writes nothing"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 2)
    assertEquals(lines(done), Vector.empty)
