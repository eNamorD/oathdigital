package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Military Parade: a free plan for either side. If its user wins, they gain a
  * favor for each faceup adviser the enemy holds then, from that adviser's
  * suit bank. Bandits hold no advisers, so an attacker is not offered it
  * against them. */
class MilitaryParadeSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Military Parade")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val honors = CatalogNames.denizen("Battle Honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(honors)
  private val orders = planless("Martial Culture", "Baron")
  private val beast = planlessCard("Errand Boy")
  private val arcane = planlessCard("Ward of Silence")
  /** Implemented, but its power offers no battle plan. */
  private val marriage = CatalogNames.denizen("Marriage")

  /** The attacker holds Military Parade. The other player rules the origin and
    * holds two order advisers and a beast adviser faceup, and an arcane
    * adviser facedown. */
  private def attacking: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val held = Vector(orders(0) -> Orientation.FaceUp,
      orders(1) -> Orientation.FaceUp, beast -> Orientation.FaceUp,
      arcane -> Orientation.FaceDown).foldLeft(base) {
      case (b, (adviser, face)) => withAdviserFor(b, b.rival, adviser, face) }
    withAdviser(held, card, Orientation.FaceUp)

  test("an attacker that wins gains a favor per faceup adviser of the enemy, " +
      "from each adviser's bank"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    // The facedown arcane adviser has no suit and gives nothing.
    assertEquals(Look(done.state).favor(b.actor),
      Look(before).favor(b.actor) + 3)
    assertEquals(Look(done.state).bankFavor(Suit.Order),
      Look(before).bankFavor(Suit.Order) - 2)
    assertEquals(Look(done.state).bankFavor(Suit.Beast),
      Look(before).bankFavor(Suit.Beast) - 1)
    assertEquals(Look(done.state).bankFavor(Suit.Arcane),
      Look(before).bankFavor(Suit.Arcane))

  test("an attacker that loses gains nothing"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).favor(b.actor), Look(before).favor(b.actor))

  test("a defender that wins gains for the attacker's faceup advisers"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviser(withAdviserFor(base, base.rival, card,
      Orientation.FaceUp), orders(0), Orientation.FaceUp)
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).favor(b.rival),
      Look(before).favor(b.rival) + 1)
    assertEquals(Look(done.state).bankFavor(Suit.Order),
      Look(before).bankFavor(Suit.Order) - 1)

  test("an attacker is not offered it against bandits"):
    val advised = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    val b = withAdviser(advised, honors, Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 4)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))

  test("a bandit defender that wins takes the favor from the Order bank, " +
      "without choosing"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(withSiteCard(base, base.origin, card), orders(0),
      Orientation.FaceUp)
    val before = OathState.Ready(b.ready)
    val run = commit(rules(losingDice), b, 2)
    // It applied the free plan by itself, so nothing was asked of the attacker.
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    val done = run.finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).bankFavor(Suit.Order),
      Look(before).bankFavor(Suit.Order) - 1)
    assertEquals(Look(done.state).favor(b.actor), Look(before).favor(b.actor))

  test("the gain is best-effort: an Order bank with one favor gives one"):
    val b = on(attacking)(_.bankFavor(Suit.Order, 1))
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(Look(done.state).bankFavor(Suit.Order), 0)
    // The two Order advisers get the Order bank's only favor; the Beast
    // adviser gets 1 from the Beast bank.
    assertEquals(Look(done.state).favor(b.actor),
      Look(before).favor(b.actor) + 1 + 1)

  test("an enemy's faceup Marriage gives two Hearth favor"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviser(withAdviserFor(base, base.rival, marriage,
      Orientation.FaceUp), card, Orientation.FaceUp)
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).favor(b.actor),
      Look(before).favor(b.actor) + 2)
    assertEquals(Look(done.state).bankFavor(Suit.Hearth),
      Look(before).bankFavor(Suit.Hearth) - 2)
