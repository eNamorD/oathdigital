package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Wrestlers: a defender sacrifices a warband from its force for one more
  * defense die, and the force the defense is scored with is one lower.
  */
class WrestlersSuite extends munit.FunSuite {
  private val card = cardWith("denizen.wrestlers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  private def conquest: Board = {
    val base = againstPlayer(board())
    withAdviserFor(base, base.other, card, Orientation.FaceUp)
  }

  private def raid(defenderWarbands: Int): Board = {
    val base = withEnemyAtOrigin(board(warbands = 4))
    replacePlayer(withAdviserFor(base, base.other, card, Orientation.FaceUp),
      base.other)(p => p.copy(board = p.board.copy(warbands = defenderWarbands)))
  }

  private def siteForces(state: OathState, b: Board): SiteForces =
    ready(state).game.current.map.sites(b.origin).forces

  test("a Conquest defender sacrifices a warband at the target site for a defense die") {
    val b = conquest
    val run = commit(rules(losing), b, 4)
    assertEquals(run.continue, awaits(b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assertEquals(siteForces(picked.state, b),
      SiteForces.Occupied(ForceKind.Exile(b.player(b.other).lineage), 1))
    assertEquals(picked.since(run).map(_.getClass.getSimpleName),
      Vector("Sacrifice", "ModifyDicePool"))
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
  }

  test("the sacrificed warband lowers the recorded force by one") {
    val b = conquest
    val without = commit(rules(losing), b, 4).finish
    val with_ = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    val score = (run: Run) => ready(run.state).game.current.lastCampaignResult
      .get.defenseScore
    assertEquals(score(without), 2)
    assertEquals(score(with_), 1)
  }

  test("a Raid defender sacrifices from the board") {
    val b = raid(3)
    val run = commit(rules(losing), b, 2, raid = true)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assertEquals(player(picked.state, b.other).board.warbands, 2)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
  }

  test("a defender with no warband in its force cannot pay, so the plan is not offered") {
    val b = raid(0)
    assertEquals(commit(rules(losing), b, 2, raid = true).continue,
      awaits(b.actor, CampaignIds.sacrifice))
  }

  test("it is a defender's plan only") {
    val base = board()
    val b = withAdviser(base, card, Orientation.FaceUp)
    assertEquals(commit(rules(losing), b, 2).continue,
      awaits(b.actor, CampaignIds.sacrifice))
  }

  test("a bandit defender never uses it, since it costs a warband") {
    val two = board(extras = 1)
    val b = withSiteCard(two, two.extras.head, card)
    val run = commit(rules(losing), b, 2)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    assert(!run.ops.exists(_.isInstanceOf[Sacrifice]))
  }
}
