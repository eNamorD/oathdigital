package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Village Constable: a free, site-only plan for two attack dice, added or
  * removed, unless the enemy holds the People's Favor. */
class VillageConstableSuite extends munit.FunSuite:
  private val card = cardWith("denizen.village-constable")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  /** The actor also rules a site outside the Campaign, where Village
    * Constable stands. */
  private def attacker(b: Board): Board =
    // Broken Peaks: these boards have no extras, so nobody else rules it.
    val site = CatalogNames.site("Broken Peaks")
    withSiteCard(actorRules(b, site), site, card)

  test("an attacker adds two attack dice against bandits, for nothing"):
    val b = attacker(board(warbands = 5, supply = 7))
    val run = commit(rules(winning), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(
      picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is not offered while the defender holds the People's Favor"):
    val base = attacker(
      rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)))
    assert(commit(rules(winning), base, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val b = on(base)(_.peoplesFavor(Some(base.rival), 1))
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes two attack dice"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(winning), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a bandit defender applies it unless the attacker holds " +
      "the People's Favor"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    assert(commit(rules(winning), b, 4).finishPlanWindows.ops
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    val favored = on(b)(_.peoplesFavor(Some(b.actor), 1))
    assert(!commit(rules(winning), favored, 4).finishPlanWindows.ops
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
