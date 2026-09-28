package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Black Sword: an attacker burns two secrets for five attack dice. */
class BlackSwordSuite extends munit.FunSuite:
  private val relic = relicWith("relic.black-sword")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))

  test("an attacker burns two secrets for five attack dice"):
    val b = withSecrets(withRelic(board(), relic), 2)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 5)))
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)

  test("an attacker with one secret is not offered it"):
    val b = withSecrets(withRelic(board(), relic), 1)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender is not offered it"):
    val base = againstPlayer(board())
    val b = replacePlayer(withRelicFor(base, base.other, relic), base.other)(p =>
      p.copy(board = p.board.copy(faceUpSecrets = 2)))
    val run = commit(rules(winning), b, 2)
    // The defender holds the title, so its defense is offered and the choice
    // is asked; Black Sword is not among the options.
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))
