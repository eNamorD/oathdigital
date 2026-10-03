package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.Look

/** Battle Honors: a free plan for either side that gains two favor from the
  * Order bank if its user wins, as much as the bank holds. */
class BattleHonorsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.battle-honors")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def orderBank(state: OathState): Int =
    ready(state).banks.favor.getOrElse(Suit.Order, 0)

  private def favor(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.favor

  /** p1 holds Battle Honors faceup. */
  private def attackerHoldingBattleHonors: Board =
    withAdviser(board(warbands = 5, supply = 7), card, Orientation.FaceUp)

  /** The rival rules the origin and holds Battle Honors faceup. */
  private def defenderHoldingBattleHonors: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, card, Orientation.FaceUp)

  test("an attacker that wins gains two favor from the Order bank"):
    val b = attackerHoldingBattleHonors
    val bank = orderBank(asState(b))
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor) + 2)
    assertEquals(orderBank(done.state), bank - 2)

  test("an attacker that loses gains nothing"):
    val b = attackerHoldingBattleHonors
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor))

  test("choosing it costs nothing and changes no dice"):
    val b = attackerHoldingBattleHonors
    val run = commit(rules(winning), b, 4)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists(op => op.isInstanceOf[PayCost] ||
      op.isInstanceOf[ModifyDicePool]))

  test("a defender that wins gains two favor, and one that " +
      "loses gains nothing"):
    val b = defenderHoldingBattleHonors
    val won = commit(rules(losing), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(ready(won.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(favor(won.state, b.rival), favor(asState(b), b.rival) + 2)
    val lost = commit(rules(winning), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(favor(lost.state, b.rival), favor(asState(b), b.rival))

  test("an Order bank with one favor gives one"):
    val b = on(attackerHoldingBattleHonors)(_.bankFavor(Suit.Order, 1))
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor) + 1)
    assertEquals(orderBank(done.state), 0)

  /** Battle Honors stands at an extra site the bandits rule. */
  private def banditSiteHoldingBattleHonors: Board =
    val two = board(extras = 1, warbands = 5, supply = 7)
    withSiteCard(two, two.extras.head, card)

  test("a bandit defender that wins takes two favor from the Order bank " +
      "without choosing"):
    val b = banditSiteHoldingBattleHonors
    val bank = orderBank(asState(b))
    val run = commit(rules(losing), b, 2)
    // It applied the free plan by itself, so nothing was asked of the attacker.
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    val done = run.finishPlanWindows
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(orderBank(done.state), bank - 2)
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor))

  test("a bandit defender that loses gains nothing"):
    val b = banditSiteHoldingBattleHonors
    val bank = orderBank(asState(b))
    val done = commit(rules(winning), b, 4).finishPlanWindows
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(orderBank(done.state), bank)

  test("a bandit defender takes the last favor of an Order bank of one"):
    val b = on(banditSiteHoldingBattleHonors)(_.bankFavor(Suit.Order, 1))
    val done = commit(rules(losing), b, 2).finishPlanWindows
    assertEquals(orderBank(done.state), 0)

  private def asState(b: Board): OathState = OathState.Ready(b.ready)
