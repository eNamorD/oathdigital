package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Longbows: a free plan for one more attack die, or one fewer when its user
  * defends. */
class LongbowsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.longbows")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  test("an attacker adds one attack die, for nothing"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("a defender removes one attack die"):
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))

  test("a bandit defender applies it at a site the bandits rule"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(winning), b, 2)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == Longbows.id), 1)
