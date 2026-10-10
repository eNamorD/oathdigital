package oathdigital.gameplay.actions.dice

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

/** The roll step: after a roll its roller picks a reroll a power offers, or
  * keeps the roll (catalog batch 7, slice 1, "The roll step"). The tests'
  * own `FreeReroll` offers it. */
class RerollChoiceSuite extends munit.FunSuite:
  import AttackDieFace.{HollowSword, OneSword}
  import DefenseDieFace.{Blank, TwoShields}
  import FreeReroll.bracken

  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(bracken)
  private val powers = WalkerPowers(Vector(FreeReroll))
  private val attackReroll = RerollChoice.decisionId(CampaignIds.attackPool, 1)
  private val defenseReroll =
    RerollChoice.decisionId(CampaignIds.defensePool, 1)

  test("Bracken offers nothing of its own"):
    assertEquals(planlessCard("Bracken"), bracken)

  /** p1 attacks the bandit-ruled origin with 2 warbands, holding
    * Bracken faceup. */
  private def attacker: Board =
    withAdviser(board(warbands = 2, supply = 7), bracken,
      Orientation.FaceUp)

  test("a roller who may reroll is asked after the roll, and a reroll " +
      "replaces the dice"):
    val b = attacker
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(HollowSword, HollowSword), Vector(OneSword, OneSword),
      Vector(Blank, Blank)))
    val asked = commit(game, b, 2, powers = powers).finishPlanWindows
    assert(awaits(asked, b.actor, attackReroll))
    assertEquals(asked.options(b.actor, powers).map(_.ref),
      Vector(ref, RerollChoice.keep))
    val done = asked.pick(b.actor, attackReroll, ref).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, OneSword))

  test("a power used on a throw is not offered again, so nothing more is " +
      "asked"):
    val b = attacker
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(HollowSword, HollowSword), Vector(OneSword, HollowSword),
      Vector(Blank, Blank)))
    val done = commit(game, b, 2, powers = powers).finishPlanWindows
      .pick(b.actor, attackReroll, ref).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, HollowSword))

  test("keeping the roll changes nothing"):
    val b = attacker
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(HollowSword, HollowSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2, powers = powers).finishPlanWindows
      .pick(b.actor, attackReroll, RerollChoice.keep).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, HollowSword))

  test("with nothing offered, the roll step asks nothing"):
    val b = board(warbands = 2, supply = 7)
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(HollowSword, HollowSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2, powers = powers).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, HollowSword))

  test("a player defender rerolls the defense inside the attacker's walk"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 4, supply = 7))
    val b = withAdviserFor(base, base.rival, bracken, Orientation.FaceUp)
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector.fill(4)(OneSword), Vector(Blank, Blank),
      Vector(TwoShields, TwoShields)))
    val asked = commit(game, b, 4, powers = powers).finishPlanWindows
    assert(awaits(asked, b.rival, defenseReroll))
    val done = asked.pick(b.rival, defenseReroll, ref).finishPlanWindows
    assertEquals(done.result.defenseFaces,
      Vector[DefenseDieFace](TwoShields, TwoShields))

  test("bandits roll for nobody, so no one rerolls their defense"):
    // The attacker holds Bracken, but only the defense's roller may
    // reroll the defense, and bandits are nobody. The attack is kept.
    val b = attacker
    val game = rulesWith(Vector(FreeReroll), scripted(
      Vector(OneSword, OneSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2, powers = powers).finishPlanWindows
      .pick(b.actor, attackReroll, RerollChoice.keep).finishPlanWindows
    assertEquals(done.result.defenseFaces,
      Vector[DefenseDieFace](Blank, Blank))

  test("each Recover roll is its own throw, with its own decision"):
    val recover = RerollChoice.decisionId(PoolKey("recover"), 2)
    assertEquals(recover, "recover.reroll.2")
    assertEquals(RerollChoice.poolOf(recover), Some(PoolKey("recover")))
    assertEquals(RerollChoice.poolOf(attackReroll),
      Some(CampaignIds.attackPool))
    assertEquals(RerollChoice.poolOf("campaign.sacrifice"), None)
    assert(RerollChoice.isDecision(attackReroll))
