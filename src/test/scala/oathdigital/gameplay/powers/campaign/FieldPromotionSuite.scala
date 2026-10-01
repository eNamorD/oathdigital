package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.{NoteText, PlayerFacts}
import oathdigital.gameplay.powers.PowerFixture.warbandBank
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Field Promotion: a plan for either side, a favor placed, that gains three
  * warbands if its user wins, or what their supply holds. */
class FieldPromotionSuite extends munit.FunSuite:
  private val card = cardWith("denizen.field-promotion")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val honors = cardWith("denizen.battle-honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(honors))

  private def kind(b: Board, who: PlayerId): ForceKind =
    PlayerFacts.forceKind(b.ready, who).toOption.get

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    FieldPromotion.id, Vector(FieldPromotion.gained), run.events)

  private def gained(who: PlayerId, count: Int): NoteText.Said =
    NoteText.Said("gained", s"${who.value} gained $count " +
      (if count == 1 then "warband." else "warbands."), covers = false)

  /** The attacker holds Field Promotion and a favor. */
  private def attacking: Board =
    val base = board()
    on(withAdviser(base, card, Orientation.FaceUp))(_.favor(base.actor, 1))

  test("an attacker that wins places a favor on it and gains three warbands, and says so"):
    val b = attacking
    val run = commit(rules(winning), b, 4)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(player(picked.state, b.actor).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens },
      Some(Tokens(1, 0)))
    val done = picked.finish
    assertEquals(winner(done), Some(true))
    assert(done.ops.contains(Gain.Warbands(b.actor, kind(b, b.actor), 3)))
    assertEquals(lines(done), Vector(gained(b.actor, 3)))

  test("an attacker that loses gains nothing and writes nothing"):
    val b = attacking
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assert(!done.ops.exists(_.isInstanceOf[Gain.Warbands]))
    assertEquals(lines(done), Vector.empty)

  test("a defender that wins pays off turn and gains three warbands"):
    val base = againstPlayer(board())
    val b = on(withAdviserFor(base, base.other, card, Orientation.FaceUp))(
      _.favor(base.other, 1))
    val picked = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    // Paid off turn, the favor goes to the bank at once, not onto the card.
    assertEquals(player(picked.state, b.other).board.favor, 0)
    val done = picked.finish
    assertEquals(winner(done), Some(false))
    assertEquals(lines(done), Vector(gained(b.other, 3)))

  test("a short supply gives what it holds"):
    val base = attacking
    val force = kind(base, base.actor)
    // Every warband of the actor's kind but one is on their board.
    val b = replacePlayer(base, base.actor)(p => p.copy(board = p.board.copy(
      warbands = p.board.warbands + warbandBank(base.ready, force) - 1)))
    assertEquals(warbandBank(b.ready, force), 1)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(true))
    assertEquals(warbandBank(ready(done.state), force), 0)
    assertEquals(lines(done), Vector(gained(b.actor, 1)))

  test("an attacker with no favor is not offered it"):
    val base = board()
    val b = on(withAdviser(withAdviser(base, card, Orientation.FaceUp), honors,
      Orientation.FaceUp))(_.favor(base.actor, 0))
    val run = commit(rules(winning), b, 4)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))

  test("a bandit defender never uses it, since it costs a favor"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(losing), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assert(!ready(run.state).game.current.rollPools.contains(
      CampaignPlans.appliedMarker(ref)))
