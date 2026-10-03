package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Look

/** Tribute Spoils: a plan for either side, a favor placed, offered only in a
  * Conquest. If its user wins, they gain a favor for each denizen and edifice
  * at the targets, from that card's suit bank. */
class TributeSpoilsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Tribute Spoils")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val honors = CatalogNames.denizen("Battle Honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(honors)
  private val orders = planless("Martial Culture", "Code of Honor", "Palanquin")
  // Beast, on either face: Hallowed Spring intact, Hiding Place ruined.
  private val beastEdifice = CatalogNames.edifice("Hallowed Spring")
  // A relic at the site, which Tribute Spoils does not count.
  private val relic = "Sticky Fire"

  /** The attacker holds Tribute Spoils and a favor. The origin, which two
    * bandits rule, holds two faceup order denizens, a facedown order denizen
    * and a ruined beast edifice. */
  private def attacking: Board =
    val base = board(warbands = 5, supply = 7)
    val cards = withEdifice(withSiteCard(withSiteCard(
      withAdviser(base, card, Orientation.FaceUp), base.origin, orders(0)),
      base.origin, orders(1)), base.origin, beastEdifice, EdificeSide.Ruined)
    on(cards)(_.denizen(orders(2), at = base.origin, facedown = true)
      .favor(base.actor, 1))

  test("an attacker that wins a Conquest gains a favor per card at the " +
      "targets, from that card's bank"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    // The favor placed on the card, then two from the Order bank and one from
    // the Beast bank: the facedown denizen has no suit and gives nothing, and
    // the ruined edifice keeps its suit.
    assertEquals(Look(done.state).favor(b.actor), 1 - 1 + 3)
    assertEquals(Look(done.state).bankFavor(Suit.Order),
      Look(before).bankFavor(Suit.Order) - 2)
    assertEquals(Look(done.state).bankFavor(Suit.Beast),
      Look(before).bankFavor(Suit.Beast) - 1)

  test("an attacker that loses gains nothing"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).favor(b.actor), 0)
    assertEquals(Look(done.state).bankFavor(Suit.Order),
      Look(before).bankFavor(Suit.Order))

  test("a defender that wins gains for the cards at its own targeted site, " +
      "and a relic counts nothing"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withSiteCard(withAdviserFor(base, base.rival, card,
      Orientation.FaceUp), base.origin, orders(0)))(
      _.relicAt(relic, base.origin).favor(base.rival, 1))
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    // Paid off turn, the placed favor went to the Nomad bank; one favor came
    // back from the Order bank.
    assertEquals(Look(done.state).favor(b.rival), 1)
    assertEquals(Look(done.state).bankFavor(Suit.Order),
      Look(before).bankFavor(Suit.Order) - 1)

  test("it is not offered in a Raid"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = on(withAdviser(withAdviser(base, card, Orientation.FaceUp), honors,
      Orientation.FaceUp))(_.boardWarbands(base.rival, 3).favor(base.actor, 1))
    val run = commit(rules(winningDice), b, 4, raid = true)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))
