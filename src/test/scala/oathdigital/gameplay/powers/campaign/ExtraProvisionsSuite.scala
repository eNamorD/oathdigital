package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Extra Provisions: a defender's plan, a favor placed for one defense die. */
class ExtraProvisionsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Extra Provisions")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  /** The other player defends the origin, holding Extra Provisions and
    * `favor` favor. */
  private def defender(favor: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.favor(base.rival, favor))

  test("a defender places a favor, off turn, for one defense die"):
    val b = defender(1)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    // Paid off turn, the favor goes to the bank at once, not onto the card.
    assertEquals(Look(picked.state).adviserTokens(b.rival, card), Tokens.empty)
    assertEquals(Look(picked.state).player(b.rival).board.favor, 0)
    assertEquals(ready(picked.state).banks.favor.values.sum,
      ready(run.state).banks.favor.values.sum + 1)

  test("it is not offered to a defender with no favor"):
    val b = defender(0)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.rival,
      CampaignIds.defenderPlan, ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 2).finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
