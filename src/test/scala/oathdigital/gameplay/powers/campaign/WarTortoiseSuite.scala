package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.{skullThenSwords,
  swordsCyclingDefense}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** War Tortoise: a favor placed. Used by the defender, the attacker's skull
  * faces score nothing and payable skulls still kill; used by the attacker,
  * the defender's double shields score nothing. */
class WarTortoiseSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("War Tortoise")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val general = CatalogNames.denizen("Fearsome General")
  private val noAttack = NoteText.Said("attack",
    "Double swords added no attack.", covers = false)
  private val noDefense = NoteText.Said("defense",
    "Double shields added no defense.", covers = false)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(WarTortoise.id, WarTortoise.noteKeys, run.events)

  /** `run` with `actor`'s plan window finished, if it is still open. */
  private def attackerDone(run: Run, actor: PlayerId): Run =
    if awaits(run, actor, CampaignIds.attackerPlan) then
      run.pick(actor, CampaignIds.attackerPlan, CampaignIds.finish)
    else run

  /** p2 rules the origin with two warbands, holds the Oathkeeper title, War
    * Tortoise faceup and one favor. */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.favor(base.rival, 1))

  test("a defender pays off turn; the skull face scores nothing and still " +
      "kills, and it says so"):
    val b = defender
    val picked = commit(rules(skullThenSwords), b, 2)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(Look(picked.state).player(b.rival).board.favor, 0)
    val done = picked.finishPlanWindows
    // A skull face and a sword: the skull face scores nothing.
    assertEquals(done.result.attackScore, 1)
    assertEquals(done.result.skullLosses, 1)
    assertEquals(lines(done), Vector(noAttack))

  test("used by the defender it silences Fearsome General's four swords"):
    val b = withAdviser(defender, general, Orientation.FaceUp)
    val chosen = commit(rules(skullThenSwords), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, DecisionOptionRef.Denizen(general))
    val done = attackerDone(chosen, b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.attackScore, 1)
    assertEquals(NoteText.said(FearsomeGeneral.id, FearsomeGeneral.noteKeys,
      done.events), Vector.empty)
    assertEquals(lines(done), Vector(noAttack))

  test("an attacker places a favor and the double shields score nothing"):
    val base = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val b = on(base)(_.favor(base.actor, 1))
    val picked = commit(rules(swordsCyclingDefense(DefenseDieFace.TwoShields,
      DefenseDieFace.OneShield)), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(1, 0))
    val done = picked.finishPlanWindows
    // Two shields score nothing; one shield and the two bandits remain.
    assertEquals(done.result.defenseScore, 1 + 2)
    assertEquals(lines(done), Vector(noDefense))

  test("a bandit defender never applies it, since it costs"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winningDice), b, 2).finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
