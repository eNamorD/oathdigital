package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Fire Talkers: a secret placed for three attack dice, added or removed,
  * offered only while its user holds the Darkest Secret. */
class FireTalkersSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Fire Talkers")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  /** `who` holds the Darkest Secret, with its 1 secret. */
  private def withDarkestSecret(b: Board, who: PlayerId): Board =
    on(b)(_.darkestSecret(Some(who), secrets = 1))

  /** p1 holds Fire Talkers faceup, 1 faceup secret and the Darkest Secret. */
  private def attackerHoldingFireTalkersAndTheDarkestSecret: Board =
    val b = withSecrets(
      withAdviser(board(warbands = 5, supply = 7), card, Orientation.FaceUp), 1)
    withDarkestSecret(b, b.actor)

  test("an attacker holding the Darkest Secret places a secret for three " +
      "attack dice"):
    val b = attackerHoldingFireTalkersAndTheDarkestSecret
    val picked = commit(rules(winning), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(0, 1))
    assertEquals(Look(picked.state).player(b.actor).board.faceUpSecrets, 0)

  test("it is not offered while another player holds the Darkest Secret"):
    val attacker = attackerHoldingFireTalkersAndTheDarkestSecret
    val b = withDarkestSecret(attacker, attacker.rival)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender pays off turn and removes three attack dice"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val armed = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1))
    val b = withDarkestSecret(armed, base.rival)
    val facedown = b.player(b.rival).board.faceDownSecrets
    val picked = commit(rules(winning), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    // Paid outside the defender's turn, the secret settles at once: it flips
    // facedown instead of resting on the card.
    assertEquals(Look(picked.state).adviserTokens(b.rival, card), Tokens.empty)
    assertEquals(Look(picked.state).player(b.rival).board.faceUpSecrets, 0)
    assertEquals(Look(picked.state).player(b.rival).board.faceDownSecrets,
      facedown + 1)
