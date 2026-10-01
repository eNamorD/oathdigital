package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Tribute Spoils: a plan for either side, a favor placed, offered only in a
  * Conquest. If its user wins, they gain a favor for each denizen and edifice
  * at the targets, from that card's suit bank. */
class TributeSpoilsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.tribute-spoils")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val honors = cardWith("denizen.battle-honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(honors))
  private val orders = inert(Suit.Order, 3)
  private val beastEdifice =
    catalog.edifices.find(_.suit == Suit.Beast).get.id.value
  private val relic = catalog.relics.head.name

  private def favor(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  private def bank(state: OathState, suit: Suit): Int =
    ready(state).banks.favor.getOrElse(suit, 0)

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  /** The attacker holds Tribute Spoils and a favor. The origin, which two
    * bandits rule, holds two faceup order denizens, a facedown order denizen
    * and a ruined beast edifice. */
  private def attacking: Board =
    val base = board()
    val cards = withEdifice(withSiteCard(withSiteCard(
      withAdviser(base, card, Orientation.FaceUp), base.origin, orders(0)),
      base.origin, orders(1)), base.origin, beastEdifice, EdificeSide.Ruined)
    on(cards)(_.denizen(DenizenId(orders(2)), at = base.origin, facedown = true)
      .favor(base.actor, 1))

  test("an attacker that wins a Conquest gains a favor per card at the targets, from that card's bank"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(true))
    // The favor placed on the card, then two from the Order bank and one from
    // the Beast bank: the facedown denizen has no suit and gives nothing, and
    // the ruined edifice keeps its suit.
    assertEquals(favor(done.state, b.actor), 1 - 1 + 3)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 2)
    assertEquals(bank(done.state, Suit.Beast), bank(before, Suit.Beast) - 1)

  test("an attacker that loses gains nothing"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assertEquals(favor(done.state, b.actor), 0)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order))

  test("a defender that wins gains for the cards at its own targeted site, and a relic counts nothing"):
    val base = againstPlayer(board())
    val b = on(withSiteCard(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.origin, orders(0)))(
      _.relicAt(relic, base.origin).favor(base.other, 1))
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(false))
    // Paid off turn, the placed favor went to the Nomad bank; one favor came
    // back from the Order bank.
    assertEquals(favor(done.state, b.other), 1)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 1)

  test("it is not offered in a Raid"):
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = on(withAdviser(withAdviser(base, card, Orientation.FaceUp), honors,
      Orientation.FaceUp))(_.warbands(base.other, 3).favor(base.actor, 1))
    val run = commit(rules(winning), b, 4, raid = true)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))
