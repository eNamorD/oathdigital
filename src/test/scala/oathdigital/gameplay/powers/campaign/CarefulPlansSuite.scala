package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Careful Plans: one favor placed, and the defender rolls before the
  * attacker; the sacrifice stays after the attack roll. */
class CarefulPlansSuite extends munit.FunSuite:
  import AttackDieFace.{OneSword, TwoSwordsSkull}
  import DefenseDieFace.{Blank, OneShield}

  private val card = CatalogNames.denizen("Careful Plans")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val attackFaces = Vector[DieFace](OneSword, TwoSwordsSkull)
  private val defenseFaces = Vector[DieFace](Blank, OneShield)

  /** p1 attacks the bandit-ruled origin with 5 warbands, holding Careful
    * Plans faceup, `favor` favor, and Longbows, a free plan that keeps the
    * attacker's window asked. Ancient City prints 2 defense dice. */
  private def attacker(favor: Int): Board =
    val b = withAdviser(withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp), longbows, Orientation.FaceUp)
    on(b)(_.favor(b.actor, favor))

  /** The pools that hold a roll now. */
  private def rolled(run: Run): Set[PoolKey] =
    ready(run.state).game.current.rollOutcomes.keySet

  test("the defense rolls first, and the sacrifice is asked with both " +
      "rolls known"):
    val b = attacker(favor = 1)
    // The scripted dice hand out rolls in the order they are asked for.
    val game = rules(scripted(defenseFaces, attackFaces))
    val picked = commit(game, b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card),
      Tokens(1, 0))
    val asked = picked.finishAttackerPlans(b.actor)
    assert(awaits(asked, b.actor, CampaignIds.sacrifice))
    assertEquals(rolled(asked),
      Set(CampaignIds.attackPool, CampaignIds.defensePool))
    val done = asked.answer(b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assertEquals(done.result.defenseFaces,
      Vector[DefenseDieFace](Blank, OneShield))
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, TwoSwordsSkull))
    // A shield and the two Bandits defend with 3; a sword, a paid skull and
    // the sacrifice attack with 4.
    assertEquals(done.result.defenseScore, 3)
    assertEquals(done.result.attackTotal, 4)
    assertEquals(said(CarefulPlans, done.events), Vector.empty)

  test("chosen, the skull's warband dies once the attack is rolled, and the " +
      "sacrifice before the losses"):
    val b = attacker(favor = 1)
    val game = rules(scripted(defenseFaces, attackFaces))
    val asked = commit(game, b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .finishAttackerPlans(b.actor)
    assert(awaits(asked, b.actor, CampaignIds.sacrifice))
    assertEquals(Look(asked.state).boardWarbands(b.actor), 4)
    val done = asked.answer(b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(1))
    assertEquals(Look(done.state).boardWarbands(b.actor), 3)

  test("unchosen, the attack rolls first"):
    val b = attacker(favor = 1)
    val game = rules(scripted(attackFaces, defenseFaces))
    val asked = commit(game, b, 2).finishAttackerPlans(b.actor)
    assert(awaits(asked, b.actor, CampaignIds.sacrifice))
    assertEquals(rolled(asked), Set(CampaignIds.attackPool))

  test("without a favor it is shown unavailable"):
    val b = attacker(favor = 0)
    val run = commit(rules(losingDice), b, 2)
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 1 favor; you have 0")))
