package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Sticky Fire: when its user wins, they may kill the whole of the enemy's force,
  * and if they do they give the loser a favor. The question is asked in the
  * losses, by the winner, before anything dies.
  */
class StickyFireSuite extends munit.FunSuite {
  private val relic = relicWith("relic.sticky-fire")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))
  private val decision = StickyFire.decisionId

  private def favored(b: Board, who: PlayerId, favor: Int): Board =
    replacePlayer(b, who)(p => p.copy(board = p.board.copy(favor = favor)))

  private def warbands(state: OathState, who: PlayerId): Int =
    player(state, who).board.warbands

  private def favorOf(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  // ---- the attacker wins a Conquest against a player ----------------------

  private def conquest: Board = {
    val base = againstPlayer(board())
    favored(withRelic(base, relic), base.actor, 2)
  }

  private def bank(state: OathState): Int =
    ready(state).banks.favor.values.sum

  test("the winner is asked before anything dies, and the defender keeps no returned warband if the attacker says yes") {
    val b = conquest
    val g = rules(winning)
    val before = warbands(OathState.Ready(b.ready), b.other)
    val asked = commit(g, b, 4).pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertEquals(asked.continue, awaits(b.actor, decision))
    // The question is asked first: nobody has died yet.
    assertEquals(ready(asked.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.other).lineage), 2))
    val done = asked.pick(b.actor, decision, StickyFire.yes).finish
    assertEquals(warbands(done.state, b.other), before)
    // The winner gave the loser a favor.
    assertEquals(favorOf(done.state, b.actor), 1)
    assertEquals(favorOf(done.state, b.other),
      favorOf(OathState.Ready(b.ready), b.other) + 1)
  }

  test("if the attacker says no, the defender keeps the returned half and nothing is given") {
    val b = conquest
    val before = warbands(OathState.Ready(b.ready), b.other)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.no).finish
    assertEquals(warbands(done.state, b.other), before + 1)
    assertEquals(favorOf(done.state, b.actor), 2)
  }

  test("nothing is asked when its user loses, or when it was not chosen") {
    val b = conquest
    val lost = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertEquals(ready(lost.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertNotEquals(lost.continue, awaits(b.actor, decision))
    assertNotEquals(lost.continue, awaits(b.other, decision))
    val unchosen = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, CampaignIds.finish)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertNotEquals(unchosen.continue, awaits(b.actor, decision))
  }

  test("the favor is given only if the winner has one to give") {
    val b = favored(conquest, conquest.actor, 0)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.yes).finish
    assertEquals(favorOf(done.state, b.other),
      favorOf(OathState.Ready(b.ready), b.other))
  }

  // ---- the attacker wins a Raid -------------------------------------------

  test("in a Raid every warband on the defender's board dies, not half") {
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = favored(withRelic(replacePlayer(base, base.other)(p => p.copy(
      board = p.board.copy(warbands = 3))), relic), base.actor, 1)
    val g = rules(winning)
    val done = commit(g, b, 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.yes)
    assertEquals(warbands(done.state, b.other), 0)
    assertEquals(favorOf(done.state, b.actor), 0)
    // The Raid then burns half of the defender's favor, the given one included.
    val given = favorOf(OathState.Ready(b.ready), b.other) + 1
    assertEquals(favorOf(done.state, b.other), given - given / 2)
  }

  test("the same Raid without Sticky Fire kills half the board") {
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = replacePlayer(base, base.other)(p => p.copy(
      board = p.board.copy(warbands = 3)))
    val done = commit(rules(winning), b, 4, raid = true)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertEquals(warbands(done.state, b.other), 2)
  }

  // ---- the defender wins --------------------------------------------------

  private def defending: Board = {
    val base = againstPlayer(board())
    favored(withRelicFor(base, base.other, relic), base.other, 2)
  }

  test("a defender that wins is asked, and every warband on the attacker's board dies, committed or not") {
    val b = defending
    val g = rules(losing)
    val asked = commit(g, b, 4).pick(b.other, CampaignIds.defenderPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
    assertEquals(ready(asked.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(asked.continue, awaits(b.other, decision))
    val done = asked.pick(b.other, decision, StickyFire.yes).finish
    assertEquals(warbands(done.state, b.actor), 0)
    assertEquals(favorOf(done.state, b.other), 1)
    assertEquals(favorOf(done.state, b.actor),
      favorOf(OathState.Ready(b.ready), b.actor) + 1)
  }

  // ---- against bandits ----------------------------------------------------

  test("against bandits the favor is burnt instead of given") {
    val base = board()
    val b = favored(withRelic(base, relic), base.actor, 2)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.actor, decision, StickyFire.yes).finish
    assertEquals(favorOf(done.state, b.actor), 1)
    // The favor left every player's board without reaching a suit bank.
    assertEquals(bank(done.state), bank(OathState.Ready(b.ready)))
  }

  test("a player who does not hold the relic is never asked") {
    val base = againstPlayer(board())
    val done = commit(rules(winning), favored(base, base.actor, 2), 4)
      .pick(base.other, CampaignIds.defenderPlan, CampaignIds.finish).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assertEquals(done.ops.count(_.isInstanceOf[Give]), 0)
  }
}
