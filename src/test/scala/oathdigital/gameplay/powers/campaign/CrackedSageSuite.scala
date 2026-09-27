package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Cracked Sage: a secret placed and a favor burnt for four attack dice,
  * added or removed, offered only when the enemy has a faceup arcane
  * adviser. */
class CrackedSageSuite extends munit.FunSuite:
  private val card = cardWith("denizen.cracked-sage")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val arcane = inert(Suit.Arcane, 1).head

  private def funded(b: Board, who: PlayerId): Board =
    replacePlayer(b, who)(p =>
      p.copy(board = p.board.copy(favor = 2, faceUpSecrets = 1)))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  /** The attacker holds the Sage; the defender, a player, holds an arcane
    * adviser on `enemy`'s face. */
  private def attacker(enemy: Orientation): Board =
    val base = againstPlayer(board())
    funded(withAdviserFor(withAdviser(base, card, Orientation.FaceUp),
      base.other, arcane, enemy), base.actor)

  test("an attacker places a secret and burns a favor for four attack dice"):
    val b = attacker(Orientation.FaceUp)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 4)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(0, 1)))
    assertEquals(player(picked.state, b.actor).board.favor, 1)
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)

  test("it is not offered when the enemy's arcane adviser is facedown"):
    val b = attacker(Orientation.FaceDown)
    assert(!awaits(commit(rules(winning), b, 2), b.actor,
      CampaignIds.attackerPlan))

  test("it is never offered against bandits"):
    val base = board()
    val b = funded(withAdviser(base, card, Orientation.FaceUp), base.actor)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender removes four attack dice when the attacker has an arcane adviser"):
    val base = againstPlayer(board())
    val b = funded(withAdviserFor(withAdviser(base, arcane, Orientation.FaceUp),
      base.other, card, Orientation.FaceUp), base.other)
    val run = commit(rules(winning), b, 5)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -4)))

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == CrackedSage.id), 1)
