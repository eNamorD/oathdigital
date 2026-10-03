package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Longbows: a free plan for one more attack die, or one fewer when its user
  * defends. */
class LongbowsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Longbows")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  test("an attacker adds one attack die, for nothing"):
    val b = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("a defender removes one attack die"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(base, base.rival, card, Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))

  test("a bandit defender applies it at a site the bandits rule"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(winningDice), b, 2)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))
