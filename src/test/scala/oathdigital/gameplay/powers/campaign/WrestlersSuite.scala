package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Wrestlers: a defender sacrifices a warband from its force for one more
  * defense die, and the force the defense is scored with is one lower.
  */
class WrestlersSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Wrestlers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def conquest: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, card, Orientation.FaceUp)

  test("a Conquest defender sacrifices a warband at the target site for a " +
      "defense die"):
    val b = conquest
    val run = commit(rules(losingDice), b, 4)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(Look(picked.state).forces(b.origin),
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 1))
    assertEquals(picked.since(run).map(_.getClass.getSimpleName),
      Vector("Sacrifice", "ModifyDicePool"))
    assert(
      picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 1)))

  test("the sacrificed warband lowers the recorded force by one"):
    val b = conquest
    val without = commit(rules(losingDice), b, 4).finishPlanWindows
    val with_ = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    val score = (run: Run) => run.result.defenseScore
    // The rival's 2 warbands at the origin, with blank dice; the sacrifice
    // takes one.
    assertEquals(score(without), 2)
    assertEquals(score(with_), 2 - 1)

  test("it is a defender's plan only"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(base, card, Orientation.FaceUp)
    assert(awaits(commit(rules(losingDice), b, 2), b.actor,
      CampaignIds.sacrifice))

  test("a bandit defender never uses it, since it costs a warband"):
    val two = board(extras = 1, warbands = 5, supply = 7)
    val b = withSiteCard(two, two.extras.head, card)
    val run = commit(rules(losingDice), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assert(!run.ops.exists(_.isInstanceOf[Sacrifice]))

  private val shield = CatalogNames.relic("Fearsome Shield")

  /** `conquest`, with the rival's only warband at the origin, and Fearsome
    * Shield (burn 2 secrets for 2 defense dice) as a second plan they can pay.
    */
  private def lastWarbandAtOriginAndShield: Board =
    on(withRelicFor(conquest, conquest.rival, shield))(_
      .warbandsAt(conquest.origin, conquest.rival, 1)
      .secrets(conquest.rival, faceUp = 2))

  /** The actor commits 2 warbands; the rival plays Wrestlers, emptying the
    * origin, and then Fearsome Shield. */
  private def wrestledThenShielded(b: Board): Run =
    val wrestled = commit(rules(winningDice), b, 2)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(Look(wrestled.state).forces(b.origin), SiteForces.Empty)
    // The next pass still offers the defender's other plan.
    assert(wrestled.offers(b.actor, b.rival, CampaignIds.defenderPlan,
      DecisionOptionRef.Relic(shield)))
    val shielded = wrestled.pick(b.rival, CampaignIds.defenderPlan,
      DecisionOptionRef.Relic(shield))
    assert(shielded.since(wrestled)
      .contains(ModifyDicePool(CampaignIds.defensePool, 2)))
    shielded

  test("sacrificing the last warband at the origin leaves the rival the " +
      "defender for the later plans, the battle and the result"):
    val b = lastWarbandAtOriginAndShield
    val result = wrestledThenShielded(b).finishPlanWindows.result
    assertEquals(result.defender, CampaignDefender.Player(b.rival))
    assertEquals(result.targetSites, Vector(b.origin))
    // Two swords against blank defense dice and no warband left to add.
    assertEquals((result.attackScore, result.defenseScore), (2, 0))
    assert(result.attackerWins)

  test("an attacker who wins conquers the origin Wrestlers emptied"):
    val b = lastWarbandAtOriginAndShield
    val shielded = wrestledThenShielded(b)
    val placing = shielded.finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
    assert(awaits(placing, b.actor, CampaignIds.placement))
    val placed = placing.answer(b.actor, CampaignIds.placement,
      DecisionAnswer.ChooseAmountAnswer(2))
    assertEquals(placed.winner, Some(true))
    assertEquals(Look(placed.state).forces(b.origin),
      SiteForces.Occupied(ForceKind.Exile(b.player(b.actor).lineage), 2))
