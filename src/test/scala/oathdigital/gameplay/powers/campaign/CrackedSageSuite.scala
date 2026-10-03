package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.Look

/** Cracked Sage: a secret placed and a favor burnt for four attack dice,
  * added or removed, offered only when the enemy has a faceup arcane
  * adviser. */
class CrackedSageSuite extends munit.FunSuite:
  private val card = cardWith("denizen.cracked-sage")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val arcane = planlessCard("Golem Legions")

  private def funded(b: Board, who: PlayerId): Board =
    on(b)(_.favor(who, 2).secrets(who, faceUp = 1))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    Look(state).player(who).advisers.collectFirst {
      case held: DenizenState if held.id == card => held.tokens }

  /** The attacker holds the Sage; the defender, a player, holds an arcane
    * adviser on `enemy`'s face. */
  private def attacker(enemy: Orientation): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    funded(withAdviserFor(withAdviser(base, card, Orientation.FaceUp),
      base.rival, arcane, enemy), base.actor)

  test("an attacker places a secret and burns a favor for four attack dice"):
    val b = attacker(Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(0, 1)))
    assertEquals(Look(picked.state).player(b.actor).board.favor, 1)
    assertEquals(Look(picked.state).player(b.actor).board.faceUpSecrets, 0)

  test("it is not offered when the enemy's arcane adviser is facedown"):
    val b = attacker(Orientation.FaceDown)
    assert(!awaits(commit(rules(winning), b, 2), b.actor,
      CampaignIds.attackerPlan))

  test("it is never offered against bandits"):
    val base = board(warbands = 5, supply = 7)
    val b = funded(withAdviser(base, card, Orientation.FaceUp), base.actor)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender removes four attack dice when the attacker has " +

      "an arcane adviser"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = funded(withAdviserFor(withAdviser(base, arcane, Orientation.FaceUp),
      base.rival, card, Orientation.FaceUp), base.rival)
    val run = commit(rules(winning), b, 5)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))
