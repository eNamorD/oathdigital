package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Weeping Banner: a defender's relic plan, a secret placed, under which
  * each skull kills two warbands. */
class WeepingBannerSuite extends munit.FunSuite:
  import AttackDieFace.{OneSword, TwoSwordsSkull}
  import DefenseDieFace.TwoShields

  private val relic = CatalogNames.relic("Weeping Banner")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)
  private val general = CatalogNames.denizen("Fearsome General")
  private val outriders = CatalogNames.denizen("Outriders")
  // Two double shields and the two warbands defend with 6, so the attacker
  // loses every battle here and nothing is placed.
  private val shields = Vector(TwoShields, TwoShields)

  /** p2 rules the origin with 2 warbands and the Oathkeeper title, and
    * holds Weeping Banner faceup and one faceup secret. p1 has 5 warbands.
    */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withRelicFor(base, base.rival, relic))(
      _.secrets(base.rival, faceUp = 1))

  /** Two skulls, then a sword for each committed warband beyond two. */
  private def skulls(force: Int): Vector[AttackDieFace] =
    Vector(TwoSwordsSkull, TwoSwordsSkull) ++ Vector.fill(force - 2)(OneSword)

  test("each skull kills two warbands, and the secret is paid off turn"):
    val b = defender
    val facedown = b.player(b.rival).board.faceDownSecrets
    val done = commit(rules(dice(skulls(4), shields)), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 4)
    // Both skulls are paid: two swords each, and two single swords.
    assertEquals(done.result.attackScore, 6)
    assertEquals(Look(done.state).player(b.rival).board.faceDownSecrets,
      facedown + 1)
    assertEquals(said(WeepingBanner, done.events), Vector.empty)

  test("the four warbands die at the attack result, before the defense roll"):
    val b = defender
    val done = commit(rules(dice(skulls(4), shields)), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    val killed = Kill(Piece.Warbands(
      ForceKind.Exile(b.player(b.actor).lineage), 4),
      PositionedLocation(Location.PlayArea(b.actor)))
    assert(killedBeforeTheDefenseRoll(done.events, killed), done.events)

  test("with an odd force the cap kills every warband at the attack result"):
    val b = defender
    val done = commit(rules(dice(skulls(3), shields)), b, 3)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    val killed = Kill(Piece.Warbands(
      ForceKind.Exile(b.player(b.actor).lineage), 3),
      PositionedLocation(Location.PlayArea(b.actor)))
    assert(killedBeforeTheDefenseRoll(done.events, killed), done.events)

  test("with an odd force the last warband dies and its skull scores " +
      "nothing"):
    val b = defender
    val done = commit(rules(dice(skulls(3), shields)), b, 3)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 3)
    assertEquals(done.result.attackScore, 1 + 2)

  test("with Fearsome General a paid skull is worth four, as without it"):
    val b = withAdviser(defender, general, Orientation.FaceUp)
    val done = commit(rules(dice(skulls(3), shields)), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(general))
      .finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 3)
    assertEquals(done.result.attackScore, 1 + 4)

  test("with the skulls ignored nothing dies and every face scores"):
    val b = withAdviser(defender, outriders, Orientation.FaceUp)
    val done = commit(rules(dice(skulls(3), shields)), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(outriders))
      .finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 0)
    assertEquals(done.result.attackScore, 1 + 4)
