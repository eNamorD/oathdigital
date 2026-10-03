package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.WalkerDiceFixture.swordsCyclingDefense
import oathdigital.model._
import oathdigital.model.DefenseDieFace.{Doubler, OneShield, TwoShields}
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** Garrison Armory: a defender's plan, a favor placed, in a Conquest, so that
  * the warbands at the targets add their defense twice. */
class GarrisonArmorySuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Garrison Armory")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val bag = CatalogNames.relic("Bag of Siegeworks")
  private val bagRef: DecisionOptionRef = DecisionOptionRef.Relic(bag)
  private val provisions = CatalogNames.denizen("Extra Provisions")
  private val twoAdded = NoteText.Said("added",
    "Warbands on the targets added 2 more defense.", covers = false)

  /** The other player rules the origin with two warbands and holds Garrison
    * Armory and a favor. */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.favor(base.rival, 1))

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    GarrisonArmory.id, Vector(GarrisonArmory.added), run.events)

  test("a defender places a favor, off turn, and the targets' warbands add " +
      "their defense twice, and it says so"):
    val b = defender
    val run = commit(rules(swordsCyclingDefense(TwoShields)), b, 5)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    // Paid off turn, the favor goes to the bank at once, not onto the card.
    assertEquals(Look(picked.state).adviserTokens(b.rival, card), Tokens.empty)
    assertEquals(Look(picked.state).player(b.rival).board.favor, 0)
    val done = picked.finishPlanWindows
    // Two dice of two shields, then the two warbands at the origin, twice.
    assertEquals(done.result.defenseScore, 4 + 2 + 2)
    assertEquals(lines(done), Vector(twoAdded))

  test("the doubler multiplies the dice only"):
    val done = commit(rules(swordsCyclingDefense(TwoShields,
      Doubler)), defender, 5)
      .pick(defender.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Two shields doubled is 4; the warbands add 2, then 2 more, undoubled.
    assertEquals(done.result.defenseScore, 8)

  test("single shields are ignored before the warbands are added"):
    val b = withSecrets(withRelic(defender, bag), 1)
    val bagged = commit(rules(swordsCyclingDefense(OneShield,
      TwoShields)), b, 5)
      .pick(b.actor, CampaignIds.attackerPlan, bagRef)
    val attackDone =
      if awaits(bagged, b.actor, CampaignIds.attackerPlan) then
        bagged.answer(b.actor, CampaignIds.attackerPlan,
          ChooseOneAnswer(CampaignIds.finish))
      else bagged
    val done = attackDone.pick(b.rival, CampaignIds.defenderPlan,
      ref).finishPlanWindows
    // The single shield scores nothing, so the dice score 2; the warbands add
    // 2, twice.
    assertEquals(done.result.defenseScore, 6)
    assertEquals(lines(done), Vector(twoAdded))

  test("it is not offered in a Raid"):
    val base = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    // Extra Provisions keeps the defender's window open.
    val b = on(withAdviserFor(withAdviserFor(base, base.rival, card,
      Orientation.FaceUp), base.rival, provisions, Orientation.FaceUp))(
      _.favor(base.rival, 2))
    val run = commit(rules(winningDice), b, 3, raid = true)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winningDice), b, 2).finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
