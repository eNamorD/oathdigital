package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Fire Talkers: a secret placed for three attack dice, added or removed,
  * offered only while its user holds the Darkest Secret. */
class FireTalkersSuite extends munit.FunSuite:
  private val card = cardWith("denizen.fire-talkers")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)

  private def withDarkestSecret(b: Board, who: PlayerId): Board =
    b.copy(ready = b.ready.updateCurrent(current => current.copy(banners =
      current.banners.copy(darkestSecret =
        current.banners.darkestSecret.copy(holder = Some(who))))))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  private def attacker: Board =
    val b = withSecrets(withAdviser(board(), card, Orientation.FaceUp), 1)
    withDarkestSecret(b, b.actor)

  test("an attacker holding the Darkest Secret places a secret for three attack dice"):
    val b = attacker
    val picked = commit(rules(winning), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(0, 1)))
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)

  test("it is not offered while another player holds the Darkest Secret"):
    val b = withDarkestSecret(attacker, attacker.other)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a defender pays off turn and removes three attack dice"):
    val base = againstPlayer(board())
    val armed = replacePlayer(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other)(p =>
      p.copy(board = p.board.copy(faceUpSecrets = 1)))
    val b = withDarkestSecret(armed, base.other)
    val facedown = player(OathState.Ready(b.ready), b.other).board.faceDownSecrets
    val picked = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(picked.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    // Paid outside the defender's turn, the secret settles at once: it flips
    // facedown instead of resting on the card.
    assertEquals(adviserTokens(picked.state, b.other), Some(Tokens.empty))
    assertEquals(player(picked.state, b.other).board.faceUpSecrets, 0)
    assertEquals(player(picked.state, b.other).board.faceDownSecrets, facedown + 1)

  test("the card is found in the catalog and registered once"):
    assertEquals(SimplePlans.forCatalog(catalog).count(_.id == FireTalkers.id), 1)
