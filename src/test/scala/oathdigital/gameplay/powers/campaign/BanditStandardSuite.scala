package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Bandit Standard: a free attacker's plan from a faceup relic, for one attack
  * die per bandit warband in the region of the attacker's pawn, never against
  * bandits in a Conquest. */
class BanditStandardSuite extends munit.FunSuite:
  private val relic = relicWith("relic.bandit-standard")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)

  /** In the Cradle with the origin, Ancient City. */
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  /** In the Provinces, away from the origin. */
  private val buriedGiant = CatalogNames.site("Buried Giant")

  test("an attacker adds one attack die per bandit warband in their pawn's " +
      "region, for nothing"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withRelic(base, relic))(_.bandits(brokenPeaks, 3)
      .bandits(buriedGiant, 2))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(
      picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is not offered in a Conquest against bandits"):
    val b = withRelic(board(warbands = 5, supply = 7), relic)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered when the region holds no bandit"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withRelic(base, relic))(_.bandits(buriedGiant, 2))
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is used in a Raid, counting the bandits at the pawn's site"):
    // The 2 bandits that rule the origin.
    val raided = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val b = on(withRelic(raided, relic))(_.bandits(raided.origin, 2))
    val run = commit(rules(winning), b, 3, raid = true)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(
      picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
