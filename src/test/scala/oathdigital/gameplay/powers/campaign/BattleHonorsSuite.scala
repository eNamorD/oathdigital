package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Battle Honors: a free plan for either side that gains two favor from the Order
  * bank if its user wins, as much as the bank holds.
  */
class BattleHonorsSuite extends munit.FunSuite {
  private val card = cardWith("denizen.battle-honors")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  private def orderBank(state: OathState): Int =
    ready(state).banks.favor.getOrElse(Suit.Order, 0)

  private def favor(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  private def attacking: Board = withAdviser(board(), card, Orientation.FaceUp)

  private def defending: Board = {
    val base = againstPlayer(board())
    withAdviserFor(base, base.other, card, Orientation.FaceUp)
  }

  test("an attacker that wins gains two favor from the Order bank") {
    val b = attacking
    val bank = orderBank(asState(b))
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor) + 2)
    assertEquals(orderBank(done.state), bank - 2)
  }

  test("an attacker that loses gains nothing") {
    val b = attacking
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor))
  }

  test("choosing it costs nothing and changes no dice") {
    val b = attacking
    val run = commit(rules(winning), b, 4)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists(op => op.isInstanceOf[PayCost] ||
      op.isInstanceOf[ModifyDicePool]))
  }

  test("a defender that wins gains two favor, and one that loses gains nothing") {
    val b = defending
    val won = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(ready(won.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(favor(won.state, b.other), favor(asState(b), b.other) + 2)
    val lost = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(favor(lost.state, b.other), favor(asState(b), b.other))
  }

  test("an Order bank with one favor gives one") {
    val b0 = attacking
    val b = b0.copy(ready = b0.ready.copy(banks = b0.ready.banks.copy(
      favor = b0.ready.banks.favor.updated(Suit.Order, 1))))
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor) + 1)
    assertEquals(orderBank(done.state), 0)
  }

  private def banditHolds: Board = {
    val two = board(extras = 1)
    withSiteCard(two, two.extras.head, card)
  }

  test("a bandit defender that wins gains two favor, settled into the shared bank, without choosing") {
    val b = banditHolds
    val bank = orderBank(asState(b))
    val run = commit(rules(losing), b, 2)
    // It applied the free plan by itself, so nothing was asked of the attacker.
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    val done = run.finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(orderBank(done.state), bank - 2)
    assertEquals(favor(done.state, b.actor), favor(asState(b), b.actor))
  }

  test("a bandit defender that loses gains nothing") {
    val b = banditHolds
    val bank = orderBank(asState(b))
    val done = commit(rules(winning), b, 4).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(orderBank(done.state), bank)
  }

  test("the gain is best-effort for bandits too") {
    val b0 = banditHolds
    val b = b0.copy(ready = b0.ready.copy(banks = b0.ready.banks.copy(
      favor = b0.ready.banks.favor.updated(Suit.Order, 1))))
    val done = commit(rules(losing), b, 2).finish
    assertEquals(orderBank(done.state), 0)
  }

  private def asState(b: Board): OathState = OathState.Ready(b.ready)
}
