package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Disgraced Captain: a favor placed and a favor burnt for four attack dice,
  * added or removed, offered only while the Campaign's defender rules an
  * order card. */
class DisgracedCaptainSuite extends munit.FunSuite:
  private val card = cardWith("denizen.disgraced-captain")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val order = inert(Suit.Order, 1).head

  /** `who` holds Disgraced Captain and 2 favor. */
  private def holder(b: Board, who: PlayerId): Board =
    on(withAdviserFor(b, who, card, Orientation.FaceUp))(_.favor(who, 2))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker places a favor and burns one for four attack dice " +
      "against a defender with an order adviser"):
    val base = againstPlayer(board())
    val b = withAdviserFor(holder(base, base.actor), base.other, order,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))
    assertEquals(player(picked.state, b.actor).board.favor, 0)

  test("an order card at a site the defender rules counts; a facedown " +
      "order adviser does not"):
    val base = againstPlayer(board())
    val funded = holder(base, base.actor)
    val facedown = withAdviserFor(funded, base.other, order,
      Orientation.FaceDown)
    assert(!commit(rules(winning), facedown, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val atSite = withSiteCard(funded, base.origin, order)
    assert(commit(rules(winning), atSite, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("against bandits it holds when an order card stands at any site " +
      "bandits rule"):
    val base = board(extras = 1)
    val funded = holder(base, base.actor)
    assert(!commit(rules(winning), funded, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val b = withSiteCard(funded, base.extras.head, order)
    assert(commit(rules(winning), b, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender's use reads the defender's own cards"):
    val base = againstPlayer(board())
    val defender = holder(base, base.other)
    val own = withAdviserFor(defender, base.other, order, Orientation.FaceUp)
    val run = commit(rules(winning), own, 4)
      .pick(base.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))
    // The attacker's order card does not count: the plan reads the defender.
    val enemy = withAdviserFor(defender, base.actor, order, Orientation.FaceUp)
    val parked = commit(rules(winning), enemy, 4)
    assert(awaits(parked, base.other, CampaignIds.defenderPlan))
    assert(!parked.offered(base.actor).contains(ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board()
    val b = withSiteCard(withSiteCard(base, base.origin, card), base.origin,
      order)
    assert(!commit(rules(winning), b, 2).finish.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
