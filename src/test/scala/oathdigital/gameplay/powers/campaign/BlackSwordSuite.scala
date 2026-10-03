package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Black Sword: an attacker burns two secrets for five attack dice. */
class BlackSwordSuite extends munit.FunSuite:
  private val relic = CatalogNames.relic("Black Sword")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)

  test("an attacker burns two secrets for five attack dice"):
    val b = withSecrets(withRelic(board(warbands = 5, supply = 7), relic), 2)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 5)))
    assertEquals(Look(picked.state).player(b.actor).board.faceUpSecrets, 0)

  test("an attacker with one secret is not offered it"):
    val b = withSecrets(withRelic(board(warbands = 5, supply = 7), relic), 1)
    assert(awaits(commit(rules(winningDice), b, 2), b.actor,
      CampaignIds.sacrifice))

  test("a defender is not offered it"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withRelicFor(base, base.rival, relic))(
      _.secrets(base.rival, faceUp = 2))
    val run = commit(rules(winningDice), b, 2)
    // The defender holds the title, so its defense is offered and the choice
    // is asked; Black Sword is not among the options.
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))
