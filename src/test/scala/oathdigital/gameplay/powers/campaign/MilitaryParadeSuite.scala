package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Military Parade: a free plan for either side. If its user wins, they gain a
  * favor for each faceup adviser the enemy holds then, from that adviser's
  * suit bank. Bandits hold no advisers, so an attacker is not offered it
  * against them. */
class MilitaryParadeSuite extends munit.FunSuite:
  private val card = cardWith("denizen.military-parade")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val honors = cardWith("denizen.battle-honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(honors))
  private val orders = inert(Suit.Order, 2)
  private val beast = inert(Suit.Beast, 1).head
  private val arcane = inert(Suit.Arcane, 1).head

  private def favor(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  private def bank(state: OathState, suit: Suit): Int =
    ready(state).banks.favor.getOrElse(suit, 0)

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  /** The attacker holds Military Parade. The other player rules the origin and
    * holds two order advisers and a beast adviser faceup, and an arcane
    * adviser facedown. */
  private def attacking: Board =
    val base = againstPlayer(board())
    val held = Vector(orders(0) -> Orientation.FaceUp,
      orders(1) -> Orientation.FaceUp, beast -> Orientation.FaceUp,
      arcane -> Orientation.FaceDown).foldLeft(base) {
      case (b, (adviser, face)) => withAdviserFor(b, b.other, adviser, face) }
    withAdviser(held, card, Orientation.FaceUp)

  test("an attacker that wins gains a favor per faceup adviser of the enemy, from each adviser's bank"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(true))
    // The facedown arcane adviser has no suit and gives nothing.
    assertEquals(favor(done.state, b.actor), favor(before, b.actor) + 3)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 2)
    assertEquals(bank(done.state, Suit.Beast), bank(before, Suit.Beast) - 1)
    assertEquals(bank(done.state, Suit.Arcane), bank(before, Suit.Arcane))

  test("an attacker that loses gains nothing"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assertEquals(favor(done.state, b.actor), favor(before, b.actor))

  test("a defender that wins gains for the attacker's faceup advisers"):
    val base = againstPlayer(board())
    val b = withAdviser(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), orders(0), Orientation.FaceUp)
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assertEquals(favor(done.state, b.other), favor(before, b.other) + 1)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 1)

  test("an attacker is not offered it against bandits"):
    val b = withAdviser(withAdviser(board(), card, Orientation.FaceUp), honors,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))

  test("a bandit defender that wins moves the favor from the banks to the shared bank, without choosing"):
    val base = board()
    val b = withAdviser(withSiteCard(base, base.origin, card), orders(0),
      Orientation.FaceUp)
    val before = OathState.Ready(b.ready)
    val run = commit(rules(losing), b, 2)
    // It applied the free plan by itself, so nothing was asked of the attacker.
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    val done = run.finish
    assertEquals(winner(done), Some(false))
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 1)
    assertEquals(favor(done.state, b.actor), favor(before, b.actor))

  test("the gain is best-effort: an Order bank with one favor gives one"):
    val b = on(attacking)(_.bankFavor(Suit.Order, 1))
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(bank(done.state, Suit.Order), 0)
    assertEquals(favor(done.state, b.actor), favor(before, b.actor) + 2)
