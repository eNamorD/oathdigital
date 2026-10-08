package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.AttackDieFace.TwoSwordsSkull
import oathdigital.testkit.CatalogNames

/** Fearsome General: a free attacker's plan; each skull face is worth four
  * swords under every rule the face follows. The score math (the cap, the
  * ignored skulls, War Tortoise) is `AttackScoreSuite`'s; these tests prove
  * the card's rescoring and its notes are wired into a Campaign. */
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

  /** p1 holds Fearsome General, Longbows and Outriders faceup, and commits
    * one warband and Longbows' die: two dice, both skull faces. The plans
    * chosen are Fearsome General, Longbows and the `extra` ones. */
  private def twoSkullsForOneWarband(extra: Vector[DecisionOptionRef]): Run =
    val b = withAdviser(withAdviser(withAdviser(board(warbands = 5,
      supply = 7), card, Orientation.FaceUp), "Longbows", Orientation.FaceUp),
      "Outriders", Orientation.FaceUp)
    val roll = dice(attack = Vector.fill(2)(TwoSwordsSkull),
      defense = Vector.fill(2)(DefenseDieFace.Blank))
    (Vector(ref, longbows) ++ extra).foldLeft(commit(rules(roll), b, 1))(
      (run, plan) => run.pick(b.actor, CampaignIds.attackerPlan, plan))
      .finishPlanWindows

  // The Campaign's one proof that the card rescores: its ignored skull is
  // worth four swords. The other score cases are AttackScoreSuite's, whose
  // "an unpayable skull scores nothing, with Fearsome General too" and
  // "with Outriders ... four with Fearsome General" survive.
  test("an ignored skull scores four swords"):
    val done = twoSkullsForOneWarband(Vector(outriders))
    assertEquals(done.result.attackScore, 8)
    assertEquals(done.result.skullLosses, 0)
    assertEquals(lines(done), Vector(added(4)))

  test("its note counts only the swords the payable skull added"):
    val done = twoSkullsForOneWarband(Vector.empty)
    assertEquals(lines(done), Vector(added(2)))

  test("with no skull rolled it writes nothing"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(lines(done), Vector.empty)
