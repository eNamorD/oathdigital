package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Fearsome Shield: a defender burns two faceup secrets for two more defense
  * dice, and nothing is placed on the relic.
  */
class FearsomeShieldSuite extends munit.FunSuite:
  private val relic = relicWith("relic.fearsome-shield")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))

  private def defending(secrets: Int): Board =
    val base = againstPlayer(board())
    replacePlayer(withRelicFor(base, base.other, relic), base.other)(p =>
      p.copy(board = p.board.copy(faceUpSecrets = secrets, faceDownSecrets = 0)))

  private def secretsOf(state: OathState, who: PlayerId): (Int, Int) =
    val held = player(state, who).board
    held.faceUpSecrets -> held.faceDownSecrets

  test("a defender burns two secrets and adds two defense dice"):
    val b = defending(3)
    val run = commit(rules(losing), b, 4)
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assertEquals(picked.since(run).size, 2)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    assert(picked.ops.contains(PayCost(b.other, Location.OnCard(RelicId(relic)),
      Cost(secretBurnt = 2), intoOccupied = true)))
    assertEquals(secretsOf(picked.state, b.other), (1, 0))
    // Nothing rests on the relic.
    assertEquals(player(picked.state, b.other).relics.map(_.tokens),
      Vector(Tokens.empty))

  test("with fewer than two faceup secrets the defender cannot pay, and the plan is not offered"):
    val b = defending(1)
    val run = commit(rules(losing), b, 4)
    // Only the title's plan is left to choose.
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))
    val done = run.finish
    assertEquals(secretsOf(done.state, b.other), (1, 0))
    assert(!done.ops.exists(_.isInstanceOf[PayCost]))

  test("the relic must be faceup, and it is not the attacker's plan"):
    val base = againstPlayer(board())
    val up = replacePlayer(withRelicFor(base, base.other, relic), base.other)(
      p => p.copy(board = p.board.copy(faceUpSecrets = 3)))
    // Faceup, the same board offers it; facedown, it does not.
    assert(commit(rules(losing), up, 4).offered(up.actor).contains(ref))
    val down = replacePlayer(up, up.other)(p => p.copy(relics =
      p.relics.map(_.copy(orientation = Orientation.FaceDown))))
    val facedown = commit(rules(losing), down, 4)
    assert(!facedown.offered(down.actor).contains(ref))
    assertEquals(facedown.ops.count(_.isInstanceOf[PayCost]), 0)
    val attacker = replacePlayer(withRelic(board(), relic), board().actor)(p =>
      p.copy(board = p.board.copy(faceUpSecrets = 3)))
    assert(awaits(commit(rules(losing), attacker, 2), attacker.actor,
      CampaignIds.sacrifice))
