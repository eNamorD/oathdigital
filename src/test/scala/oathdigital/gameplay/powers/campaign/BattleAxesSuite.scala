package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Battle Axes: a free plan for two attack dice, added or removed, offered
  * only while the enemy rules a beast card. */
class BattleAxesSuite extends munit.FunSuite:
  private val card = cardWith("denizen.battle-axes")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val beasts = inert(Suit.Beast, 2)

  test("an attacker adds two attack dice, for nothing, when the defender " +
      "rules a beast card"):
    val base = againstPlayer(board())
    val b = withAdviserFor(withAdviser(base, card, Orientation.FaceUp),
      base.other, beasts(0), Orientation.FaceUp)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))

  test("the user's own beast card and one at a site nobody rules count " +
      "nothing"):
    val base = againstPlayer(board())
    val site = base.ready.game.current.map.inPlay.find(_ != base.origin).get
    val b = withSiteCard(withAdviser(withAdviser(base, card,
      Orientation.FaceUp), beasts(0), Orientation.FaceUp), site, beasts(1))
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("against bandits it holds when a beast card stands at any site " +
      "bandits rule"):
    val base = board(extras = 1)
    val held = withAdviser(base, card, Orientation.FaceUp)
    assert(!commit(rules(winning), held, 3).offers(held.actor, held.actor,
      CampaignIds.attackerPlan, ref))
    val b = withSiteCard(held, base.extras.head, beasts(0))
    assert(commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes two attack dice when the attacker rules a beast " +
      "card"):
    val base = againstPlayer(board())
    val b = withAdviser(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), beasts(0), Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a bandit defender applies it only when the attacker rules a beast " +
      "card"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 4).finish.ops
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    val armed = withAdviser(b, beasts(0), Orientation.FaceUp)
    assert(commit(rules(winning), armed, 4).finish.ops
      .contains(ModifyDicePool(CampaignIds.attackPool, -2)))
