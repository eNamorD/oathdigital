package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.WalkerDiceFixture.skullThenSwords
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** The Great Levy: two favor placed for three attack dice, added or removed,
  * unless the enemy holds the People's Favor; an attacker also ignores every
  * skull, as with Outriders. */
class GreatLevySuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("The Great Levy")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val outriders = CatalogNames.denizen("Outriders")
  private val outridersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(outriders)
  private val line = NoteText.Said("ignored", "Skulls ignored.", covers = false)

  /** `who` holds The Great Levy and `favor` favor. */
  private def holder(b: Board, who: PlayerId, favor: Int = 2): Board =
    on(withAdviserFor(b, who, card, Orientation.FaceUp))(_.favor(who, favor))

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(GreatLevy.id, Vector(Outriders.ignored), run.events)

  test("an attacker places two favor for three attack dice, ignores every " +
      "skull, and says so"):
    val base = board(warbands = 5, supply = 7)
    val b = holder(base, base.actor)
    val run = commit(rules(skullThenSwords), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(
      picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(2, 0))
    assertEquals(Look(picked.state).player(b.actor).board.favor, 0)
    val done = picked.finishPlanWindows
    assertEquals(done.result.skullLosses, 0)
    assertEquals(done.result.attackScore,
      AttackDieFace.score(done.result.attackFaces))
    assertEquals(lines(done), Vector(line))

  test("it is not offered while the defender holds the People's Favor"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val funded = holder(base, base.actor)
    assert(commit(rules(winningDice), funded, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val b = on(funded)(_.peoplesFavor(Some(base.rival), 1))
    assert(!commit(rules(winningDice), b, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered to a player with one favor"):
    val base = board(warbands = 5, supply = 7)
    val b = holder(base, base.actor, favor = 1)
    assert(!commit(rules(winningDice), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes three attack dice, and the attack's skulls " +
      "still count"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = holder(base, base.rival)
    val run = commit(rules(skullThenSwords), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    // One die is left, and it rolls the skull.
    val done = run.finishPlanWindows
    assertEquals(done.result.skullLosses, 1)
    assertEquals(lines(done), Vector.empty)

  test("with Outriders also chosen, the attack is scored the same"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(holder(base, base.actor), outriders, Orientation.FaceUp)
    val done = commit(rules(skullThenSwords), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, outridersRef)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.result.skullLosses, 0)
    assertEquals(done.result.attackScore,
      AttackDieFace.score(done.result.attackFaces))
    assertEquals(lines(done), Vector(line))
    assertEquals(NoteText.said(Outriders.id, Vector(Outriders.ignored),
      done.events), Vector(line))

  test("a bandit defender never applies it, since it costs"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winningDice), b, 2).finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
