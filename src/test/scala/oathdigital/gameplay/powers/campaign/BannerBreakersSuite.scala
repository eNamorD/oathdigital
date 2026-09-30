package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Banner Breakers: a favor placed and a favor burnt for three attack dice,
  * offered only while the defender holds a banner. */
class BannerBreakersSuite extends munit.FunSuite:
  private val card = cardWith("denizen.banner-breakers")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  /** The actor holds Banner Breakers and 2 favor. Nobody holds a banner. */
  private def funded(b: Board): Board =
    on(withAdviser(b, card, Orientation.FaceUp))(_.favor(b.actor, 2)
      .peoplesFavor(None, 0).darkestSecret(None, 1))

  private val base: Board = funded(againstPlayer(board()))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker places a favor and burns one for three attack dice " +
      "against the Darkest Secret"):
    val b = on(base)(_.darkestSecret(Some(base.other), 1))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))
    assertEquals(player(picked.state, b.actor).board.favor, 0)

  test("it is offered against the People's Favor"):
    val b = on(base)(_.peoplesFavor(Some(base.other), 1))
    assert(commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered when the defender holds no banner"):
    assert(!commit(rules(winning), base, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("it is never offered against bandits"):
    val b = funded(board())
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))
