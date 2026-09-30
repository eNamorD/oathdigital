package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Cracking Ground: a free plan for one attack die per targeted site, added
  * or removed. */
class CrackingGroundSuite extends munit.FunSuite:
  private val card = cardWith("denizen.cracking-ground")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  test("an attacker adds one attack die per targeted site, for nothing"):
    val b = withAdviser(board(extras = 1), card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 2,
      targets = Vector(DecisionOptionRef.Site(b.extras.head)))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("it is not offered in a Raid, which targets no site"):
    val b = withAdviser(withEnemyAtOrigin(board()), card, Orientation.FaceUp)
    assert(commit(rules(winning), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))
    assert(!commit(rules(winning), b, 2, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes one attack die per targeted site"):
    val base = againstPlayer(board())
    val b = withAdviserFor(base, base.other, card, Orientation.FaceUp)
    val run = commit(rules(winning), b, 3)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))

  test("a bandit defender applies it at a site it rules"):
    val base = board(extras = 1)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4,
      targets = Vector(DecisionOptionRef.Site(b.extras.head))).finish
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
