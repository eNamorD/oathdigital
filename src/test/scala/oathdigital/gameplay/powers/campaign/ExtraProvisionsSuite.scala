package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Extra Provisions: a defender's plan, a favor placed for one defense die. */
class ExtraProvisionsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.extra-provisions")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  /** The other player defends the origin, holding Extra Provisions and
    * `favor` favor. */
  private def defender(favor: Int): Board =
    val base = againstPlayer(board())
    on(withAdviserFor(base, base.other, card, Orientation.FaceUp))(
      _.favor(base.other, favor))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("a defender places a favor, off turn, for one defense die"):
    val b = defender(1)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.defensePool, 1)))
    // Paid off turn, the favor goes to the bank at once, not onto the card.
    assertEquals(adviserTokens(picked.state, b.other), Some(Tokens.empty))
    assertEquals(player(picked.state, b.other).board.favor, 0)
    assertEquals(ready(picked.state).banks.favor.values.sum,
      ready(run.state).banks.favor.values.sum + 1)

  test("it is not offered to a defender with no favor"):
    val b = defender(0)
    assert(!commit(rules(winning), b, 3).offers(b.actor, b.other,
      CampaignIds.defenderPlan, ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 2).finish.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
