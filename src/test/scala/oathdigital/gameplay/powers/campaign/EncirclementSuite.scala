package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Encirclement: a favor placed for two attack dice, added or removed, offered
  * only while its user's force is strictly larger than the enemy's. */
class EncirclementSuite extends munit.FunSuite:
  private val card = cardWith("denizen.encirclement")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val wrestlers = cardWith("denizen.wrestlers")

  /** The actor holds Encirclement and a favor. */
  private def attacker(b: Board): Board =
    on(withAdviser(b, card, Orientation.FaceUp))(_.favor(b.actor, 1))

  /** The other player rules the origin with three warbands and holds
    * Encirclement and a favor. */
  private def defender: Board =
    val base = againstPlayer(board())
    on(withAdviserFor(base, base.other, card, Orientation.FaceUp))(
      _.warbandsAt(base.origin, base.other, 3).favor(base.other, 1))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  test("an attacker whose force is larger places a favor for two attack dice"):
    val b = attacker(board())
    val run = commit(rules(winning), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 2)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(1, 0)))

  test("it is not offered when the forces are equal"):
    val b = attacker(board())
    assert(!commit(rules(winning), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("in a Raid the defender's force is their board"):
    val base = withEnemyAtOrigin(board())
    val b = attacker(on(base)(_.warbands(base.other, 1)))
    assert(commit(rules(winning), b, 2, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))
    assert(!commit(rules(winning), b, 1, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender whose force is larger removes two attack dice"):
    val b = defender
    val run = commit(rules(winning), b, 2)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a plan chosen earlier in the window counts: a Wrestlers sacrifice " +
      "evens the forces"):
    val b = withAdviserFor(defender, defender.other, wrestlers,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(run.offers(b.actor, b.other, CampaignIds.defenderPlan, ref))
    val sacrificed = run.pick(b.other, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(DenizenId(wrestlers)))
    // The title's defense keeps the window open, so this is a real re-offer.
    assert(awaits(sacrificed, b.other, CampaignIds.defenderPlan))
    assert(!sacrificed.offers(b.actor, b.other, CampaignIds.defenderPlan, ref))
