package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Bandit Standard: a free attacker's plan from a faceup relic, for one attack
  * die per bandit warband in the region of the attacker's pawn, never against
  * bandits in a Conquest. */
class BanditStandardSuite extends munit.FunSuite:
  private val relic = relicWith("relic.bandit-standard")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))

  /** A site other than the origin, in the origin's region when `same` is set
    * and in another region otherwise. */
  private def site(b: Board, same: Boolean): SiteId =
    val map = b.ready.game.current.map
    map.inPlay.find(candidate => candidate != b.origin &&
      (map.regionOf(candidate) == map.regionOf(b.origin)) == same).get

  test("an attacker adds one attack die per bandit warband in their pawn's " +
      "region, for nothing"):
    val base = againstPlayer(board())
    val b = on(withRelic(base, relic))(_.bandits(site(base, same = true), 3)
      .bandits(site(base, same = false), 2))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is not offered in a Conquest against bandits"):
    val b = withRelic(board(), relic)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered when the region holds no bandit"):
    val base = againstPlayer(board())
    val b = on(withRelic(base, relic))(_.bandits(site(base, same = false), 2))
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is used in a Raid, counting the bandits at the pawn's site"):
    val b = withRelic(withEnemyAtOrigin(board()), relic)
    val run = commit(rules(winning), b, 3, raid = true)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
