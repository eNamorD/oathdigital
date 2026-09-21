package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer._

/** Warning Signals: a defender rearranges their warbands over their board and the
  * sites they rule, before their force is scored, and the card is discarded when
  * the Campaign has resolved.
  */
class WarningSignalsSuite extends munit.FunSuite {
  private val card = cardWith("denizen.warning-signals")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val decision = WarningSignals.decisionId

  /** The other player rules the origin and a second site, two warbands each,
    * holds three on their board, and holds Warning Signals. The Campaign targets
    * the origin only.
    */
  private def defending: Board = {
    val two = againstPlayer(board(extras = 1))
    val extra = two.extras.head
    val lineage = two.player(two.other).lineage
    val ruled = two.copy(ready = two.ready.updateCurrent(current =>
      current.copy(map = current.map.copy(sites = current.map.sites.updated(
        extra, current.map.sites(extra).copy(forces = SiteForces.Occupied(
          ForceKind.Exile(lineage), 2)))))))
    replacePlayer(withAdviserFor(ruled, ruled.other, card, Orientation.FaceUp),
      ruled.other)(p => p.copy(board = p.board.copy(warbands = 3)))
  }

  /** The second site the other player rules, which the Campaign does not target. */
  private def secondSite(b: Board): SiteId =b.ready.game.current.map.inPlay.find(
    site => site != b.origin && b.ready.game.current.map.sites(site).forces ==
      exile(b, 2)).get

  private def forcesAt(state: OathState, site: SiteId): SiteForces =
    ready(state).game.current.map.sites(site).forces

  private def exile(b: Board, count: Int): SiteForces =
    SiteForces.Occupied(ForceKind.Exile(b.player(b.other).lineage), count)

  private def arrangement(b: Board, board: Int, origin: Int, extra: Int) =
    DistributeAnswer(Vector(
      DistributeAmount(DecisionOptionRef.Player(b.other), board),
      DistributeAmount(DecisionOptionRef.Site(b.origin), origin),
      DistributeAmount(DecisionOptionRef.Site(secondSite(b)), extra)))

  private def chosen(b: Board): Run = commit(rules(losing), b, 4).pick(b.other,
    CampaignIds.defenderPlan, ref)

  test("choosing it asks the defender to arrange their board and every ruled site") {
    val b = defending
    val run = chosen(b)
    assertEquals(run.continue, awaits(b.other, decision))
    // Nothing has moved yet, and the card has not been discarded.
    assertEquals(forcesAt(run.state, b.origin), exile(b, 2))
    assertEquals(player(run.state, b.other).board.warbands, 3)
  }

  test("the answer moves warbands between the board and the sites, keeping the total") {
    val b = defending
    val run = chosen(b)
    val moved = run.answer(b.other, decision, arrangement(b, board = 0, origin = 4,
      extra = 3))
    assertEquals(forcesAt(moved.state, b.origin), exile(b, 4))
    assertEquals(forcesAt(moved.state, secondSite(b)), exile(b, 3))
    assertEquals(player(moved.state, b.other).board.warbands, 0)
    // The window then offers what is left, so the defender finishes it.
    assertEquals(moved.continue, awaits(b.other, CampaignIds.defenderPlan))
  }

  test("warbands may also go from the sites to the board") {
    val b = defending
    val moved = chosen(b).answer(b.other, decision, arrangement(b, board = 5,
      origin = 1, extra = 1))
    assertEquals(forcesAt(moved.state, b.origin), exile(b, 1))
    assertEquals(forcesAt(moved.state, secondSite(b)), exile(b, 1))
    assertEquals(player(moved.state, b.other).board.warbands, 5)
  }

  test("the defense is scored with the force the rearrangement left at the target") {
    val b = defending
    val done = chosen(b).answer(b.other, decision, arrangement(b, board = 0,
      origin = 4, extra = 3)).finish
    assertEquals(ready(done.state).game.current.lastCampaignResult.get
      .defenseScore, 4)
  }

  test("a site must keep a warband, and the total must be kept") {
    val b = defending
    val run = chosen(b)
    assert(run.refused(b.other, decision, arrangement(b, board = 3, origin = 0,
      extra = 4)).nonEmpty)
    assert(run.refused(b.other, decision, arrangement(b, board = 2, origin = 2,
      extra = 2)).nonEmpty)
    assert(run.refused(b.other, decision, arrangement(b, board = 4, origin = 3,
      extra = 3)).nonEmpty)
    assert(run.refused(b.other, decision, arrangement(b, board = 3, origin = 2,
      extra = 2)).isEmpty)
  }

  test("the query names the board and each ruled site, with what each holds now") {
    val b = defending
    val slots = chosen(b).query(b.actor) match {
      case DecisionQuery.Distribute(slots, min, max, _, _) =>
        assertEquals((min, max), (7, 7))
        slots
      case other => fail(s"expected a distribution, got $other")
    }
    assertEquals(slots.map(s => (s.ref, s.minimum, s.maximum, s.suggested)),
      Vector(
        (DecisionOptionRef.Player(b.other), 0, 7, Some(3)),
        (DecisionOptionRef.Site(b.origin), 1, 6, Some(2)),
        (DecisionOptionRef.Site(secondSite(b)), 1, 6, Some(2))))
  }

  test("it is discarded when the Campaign has resolved, won or lost") {
    val b = defending
    def discarded(state: OathState) = ready(state).game.current.commonCards
      .regionalDiscards.values.exists(_.contains(id))
    val lost = chosen(b).answer(b.other, decision, arrangement(b, 3, 2, 2)).finish
    assertEquals(ready(lost.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assert(discarded(lost.state))
    assertEquals(player(lost.state, b.other).advisers.collectFirst {
      case held: DenizenState if held.id == id => held }, None)
    val won = commit(rules(winning), b, 4).pick(b.other, CampaignIds.defenderPlan,
      ref).answer(b.other, decision, arrangement(b, 3, 2, 2)).finish
    assertEquals(ready(won.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(true))
    assert(discarded(won.state))
  }

  test("a defender that rules no site has nowhere to move to, so nothing is asked, and the card is still discarded") {
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = replacePlayer(withAdviserFor(base, base.other, card, Orientation.FaceUp),
      base.other)(p => p.copy(board = p.board.copy(warbands = 3)))
    val run = commit(rules(losing), b, 2, raid = true).pick(b.other,
      CampaignIds.defenderPlan, ref)
    assertEquals(run.continue, awaits(b.actor, CampaignIds.sacrifice))
    val done = run.finish
    assert(ready(done.state).game.current.commonCards.regionalDiscards.values
      .exists(_.contains(id)))
  }

  test("a bandit defender never uses it, and the attacker's own copy is no defender's plan") {
    val two = board(extras = 1)
    val bandit = withSiteCard(two, two.extras.head, card)
    assertEquals(commit(rules(losing), bandit, 2).continue,
      awaits(bandit.actor, CampaignIds.sacrifice))
    val attacker = withAdviser(board(), card, Orientation.FaceUp)
    assertEquals(commit(rules(losing), attacker, 2).continue,
      awaits(attacker.actor, CampaignIds.sacrifice))
  }

  test("the recorded moves replay to the same state, and survive the journal wire") {
    val b = defending
    val g = rules(losing)
    val run = commit(g, b, 4).pick(b.other, CampaignIds.defenderPlan, ref)
      .answer(b.other, decision, arrangement(b, board = 0, origin = 4, extra = 3))
    assertEquals(PaidActionHarness.replayed(g, b.ready, run.events),
      ready(run.state))
    assert(PaidActionHarness.wireRoundTrips(run.events))
  }
}
