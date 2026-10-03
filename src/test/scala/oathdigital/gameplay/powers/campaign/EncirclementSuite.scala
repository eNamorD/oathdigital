package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.Look

/** Encirclement: a favor placed for two attack dice, added or removed, offered
  * only while its user's force is strictly larger than the enemy's. */
class EncirclementSuite extends munit.FunSuite:
  private val card = cardWith("denizen.encirclement")
  private val id = card
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val wrestlers = cardWith("denizen.wrestlers")

  /** The actor holds Encirclement and a favor. */
  private def attacker(b: Board): Board =
    on(withAdviser(b, card, Orientation.FaceUp))(_.favor(b.actor, 1))

  /** The other player rules the origin with three warbands and holds
    * Encirclement and a favor. */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.warbandsAt(base.origin, base.rival, 3).favor(base.rival, 1))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    Look(state).player(who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker whose force is larger places a favor for two attack dice"):
    val b = attacker(board(warbands = 5, supply = 7))
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))

  test("it is not offered when the forces are equal"):
    val b = attacker(board(warbands = 5, supply = 7))
    assert(!commit(rules(winning), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("in a Raid the defender's force is their board"):
    val base = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val b = attacker(on(base)(_.boardWarbands(base.rival, 1)))
    assert(commit(rules(winning), b, 2, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))
    assert(!commit(rules(winning), b, 1, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender whose force is larger removes two attack dice"):
    val b = defender
    val run = commit(rules(winning), b, 2)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a bandit defender never applies it, since it costs a favor"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    // Two bandits against one committed warband: the bandits' force is larger.
    assert(!commit(rules(winning), b, 1).finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))

  test("a plan chosen earlier in the window counts: a Wrestlers sacrifice " +
      "evens the forces"):
    val b = withAdviserFor(defender, defender.rival, wrestlers,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(run.offers(b.actor, b.rival, CampaignIds.defenderPlan, ref))
    val sacrificed = run.pick(b.rival, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(wrestlers))
    // The title's defense keeps the window open, so this is a real re-offer.
    assert(awaits(sacrificed, b.rival, CampaignIds.defenderPlan))
    assert(!sacrificed.offers(b.actor, b.rival, CampaignIds.defenderPlan, ref))
