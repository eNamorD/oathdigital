package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Banner Breakers: a favor placed and a favor burnt for three attack dice,
  * offered only while the defender holds a banner. */
class BannerBreakersSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Banner Breakers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  /** The actor holds Banner Breakers and 2 favor. Nobody holds a banner. */
  private def funded(b: Board): Board =
    on(withAdviser(b, card, Orientation.FaceUp))(_.favor(b.actor, 2)
      .peoplesFavor(None, 0).darkestSecret(None, 1))

  private val base: Board = funded(
    rivalRulesOriginAndHoldsOathkeeper(board(warbands = 5, supply = 7)))

  test("an attacker places a favor and burns one for three attack dice " +
      "against the Darkest Secret"):
    val b = on(base)(_.darkestSecret(Some(base.rival), 1))
    val run = commit(rules(winningDice), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(1, 0))
    assertEquals(Look(picked.state).player(b.actor).board.favor, 0)

  test("it is offered against the People's Favor"):
    val b = on(base)(_.peoplesFavor(Some(base.rival), 1))
    assert(commit(rules(winningDice), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered when the defender holds no banner"):
    assert(!commit(rules(winningDice), base, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("it is never offered against bandits"):
    val b = funded(board(warbands = 5, supply = 7))
    assert(!commit(rules(winningDice), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))
