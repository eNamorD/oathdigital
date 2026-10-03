package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.Look

/** Disgraced Captain: a favor placed and a favor burnt for four attack dice,
  * added or removed, offered only while the Campaign's defender rules an
  * order card. */
class DisgracedCaptainSuite extends munit.FunSuite:
  private val card = cardWith("denizen.disgraced-captain")
  private val id = card
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val order = planless("Martial Culture").head

  /** `who` holds Disgraced Captain and 2 favor. */
  private def holder(b: Board, who: PlayerId): Board =
    on(withAdviserFor(b, who, card, Orientation.FaceUp))(_.favor(who, 2))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    Look(state).player(who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker places a favor and burns one for four attack dice " +
      "against a defender with an order adviser"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(holder(base, base.actor), base.rival, order,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))
    assertEquals(Look(picked.state).player(b.actor).board.favor, 0)

  test("an order card at a site the defender rules counts; a facedown " +
      "order adviser does not"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val funded = holder(base, base.actor)
    val facedown = withAdviserFor(funded, base.rival, order,
      Orientation.FaceDown)
    assert(!commit(rules(winning), facedown, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val atSite = withSiteCard(funded, base.origin, order)
    assert(commit(rules(winning), atSite, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("against bandits it holds when an order card stands at any site " +
      "bandits rule"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val funded = holder(base, base.actor)
    assert(!commit(rules(winning), funded, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val b = withSiteCard(funded, base.extras.head, order)
    assert(commit(rules(winning), b, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender's use reads the defender's own cards"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val defender = holder(base, base.rival)
    val own = withAdviserFor(defender, base.rival, order, Orientation.FaceUp)
    val run = commit(rules(winning), own, 4)
      .pick(base.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))
    // The attacker's order card does not count: the plan reads the defender.
    val enemy = withAdviserFor(defender, base.actor, order, Orientation.FaceUp)
    val parked = commit(rules(winning), enemy, 4)
    assert(awaits(parked, base.rival, CampaignIds.defenderPlan))
    assert(!parked.offered(base.actor).contains(ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(withSiteCard(base, base.origin, card), base.origin,
      order)
    assert(!commit(rules(winning), b, 2).finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
