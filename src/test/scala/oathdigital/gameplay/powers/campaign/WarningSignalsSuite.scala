package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.model.DecisionAnswer._
import oathdigital.testkit.ActPowerHarness
import oathdigital.testkit.Unwrap.present
import oathdigital.testkit.Outcomes.assertWireRoundTrips

/** Warning Signals: a defender rearranges their warbands over their board and
  * the sites they rule, before their force is scored, and the card is discarded
  * when the Campaign has resolved. */
class WarningSignalsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Warning Signals")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val decision = WarningSignals.decisionId

  /** The other player rules the origin and a second site, two warbands each,
    * holds three on their board, and holds Warning Signals. The Campaign
    * targets the origin only. */
  private def defending: Board =
    val two = rivalRulesOriginAndHoldsOathkeeper(
      board(extras = 1, warbands = 5, supply = 7))
    val ruled = on(two)(_.warbandsAt(two.extras.head, two.rival, 2))
    on(withAdviserFor(ruled, ruled.rival, card, Orientation.FaceUp))(
      _.boardWarbands(ruled.rival, 3))

  /** The second site the other player rules, which the Campaign does not
    * target. */
  private def secondSite(b: Board): SiteId =
    val map = b.ready.game.current.map
    present(map.inPlay.find(site =>
      site != b.origin && map.sites(site).forces == exile(b, 2)),
      "a second site the rival rules")

  private def exile(b: Board, count: Int): SiteForces =
    SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), count)

  private def arrangement(b: Board, board: Int, origin: Int, extra: Int) =
    DistributeAnswer(Vector(
      DistributeAmount(DecisionOptionRef.Player(b.rival), board),
      DistributeAmount(DecisionOptionRef.Site(b.origin), origin),
      DistributeAmount(DecisionOptionRef.Site(secondSite(b)), extra)))

  private def chosen(b: Board): Run = commit(rules(losingDice), b, 4)
    .pick(b.rival, CampaignIds.defenderPlan, ref)

  test("choosing it asks the defender to arrange their board and " +
      "every ruled site"):
    val b = defending
    val run = chosen(b)
    assert(awaits(run, b.rival, decision))
    // Nothing has moved yet, and the card has not been discarded.
    assertEquals(Look(run.state).forces(b.origin), exile(b, 2))
    assertEquals(Look(run.state).player(b.rival).board.warbands, 3)

  test("the answer moves warbands between the board and the sites, keeping " +
      "the total"):
    val b = defending
    val run = chosen(b)
    val moved = run.answer(b.rival, decision,
      arrangement(b, board = 0, origin = 4, extra = 3))
    assertEquals(Look(moved.state).forces(b.origin), exile(b, 4))
    assertEquals(Look(moved.state).forces(secondSite(b)), exile(b, 3))
    assertEquals(Look(moved.state).player(b.rival).board.warbands, 0)
    // The window then offers what is left, so the defender finishes it.
    assert(awaits(moved, b.rival, CampaignIds.defenderPlan))

  test("warbands may also go from the sites to the board"):
    val b = defending
    val moved = chosen(b).answer(b.rival, decision, arrangement(b, board = 5,
      origin = 1, extra = 1))
    assertEquals(Look(moved.state).forces(b.origin), exile(b, 1))
    assertEquals(Look(moved.state).forces(secondSite(b)), exile(b, 1))
    assertEquals(Look(moved.state).player(b.rival).board.warbands, 5)

  test("the defense is scored with the force the rearrangement left " +
      "at the target"):
    val b = defending
    val done = chosen(b).answer(b.rival, decision, arrangement(b, board = 0,
      origin = 4, extra = 3)).finishPlanWindows
    assertEquals(done.result.defenseScore, 4)

  test("a site must keep a warband, and the total must be kept"):
    val b = defending
    val run = chosen(b)
    // The rival's 7 warbands: a ruled site holds 1 to 6.
    val outsideRange = s"decision $decision distributes to " +
      s"site/${b.origin.value} outside 1..6"
    val wrongTotal = s"decision $decision distributes an amount other than " +
      "its total of 7"
    assertEquals(run.refused(b.rival, decision, arrangement(b, board = 3,
      origin = 0, extra = 4)), Some(OathViolation.InvalidEventOrder(
      outsideRange)))
    assertEquals(run.refused(b.rival, decision, arrangement(b, board = 2,
      origin = 2, extra = 2)), Some(OathViolation.InvalidEventOrder(
      wrongTotal)))
    assertEquals(run.refused(b.rival, decision, arrangement(b, board = 4,
      origin = 3, extra = 3)), Some(OathViolation.InvalidEventOrder(
      wrongTotal)))
    assertEquals(run.refused(b.rival, decision, arrangement(b, board = 3,
      origin = 2, extra = 2)), None)

  test("the query names the board and each ruled site, with what " +
      "each holds now"):
    val b = defending
    val slots = chosen(b).query(b.actor) match
      case DecisionQuery.Distribute(slots, min, max, _, _) =>
        // The rival's 7 warbands: 3 on the board and 2 at each ruled site.
        assertEquals((min, max), (7, 7))
        slots
      case other => fail(s"expected a distribution, got $other")
    // Each ruled site keeps at least 1, so it holds 1 to 6 of the 7; each
    // suggestion is what the slot holds now.
    assertEquals(slots.map(s => (s.ref, s.minimum, s.maximum, s.suggested)),
      Vector(
        (DecisionOptionRef.Player(b.rival), 0, 7, Some(3)),
        (DecisionOptionRef.Site(b.origin), 1, 6, Some(2)),
        (DecisionOptionRef.Site(secondSite(b)), 1, 6, Some(2))))

  test("it is discarded when the Campaign has resolved, won or lost"):
    val b = defending
    val keep = arrangement(b, board = 3, origin = 2, extra = 2)
    val lost = chosen(b).answer(b.rival, decision, keep).finishPlanWindows
    assertEquals(lost.winner, Some(false))
    assert(Look(lost.state).inRegionalDiscard(card))
    assert(!Look(lost.state).player(b.rival).advisers.exists(_.id == card))
    val won = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .answer(b.rival, decision, keep).finishPlanWindows
    assertEquals(won.winner, Some(true))
    assert(Look(won.state).inRegionalDiscard(card))

  test("a defender that rules no site has nowhere to move to, so nothing is " +
      "asked, and the card is still discarded"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.boardWarbands(base.rival, 3))
    val run = commit(rules(losingDice), b, 2, raid = true).pick(b.rival,
      CampaignIds.defenderPlan, ref)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    val done = run.finishPlanWindows
    assert(Look(done.state).inRegionalDiscard(card))

  test("a defender with an empty board and one warband on each ruled site " +
      "has nothing to move, so nothing is asked, and the card is still " +
      "discarded"):
    val two = defending
    val spare = secondSite(two)
    val sparse = on(two)(_.warbandsAt(two.origin, two.rival, 1)
      .warbandsAt(spare, two.rival, 1).boardWarbands(two.rival, 0))
    val run = commit(rules(losingDice), sparse, 4).pick(sparse.rival,
      CampaignIds.defenderPlan, ref)
    assert(!awaits(run, sparse.rival, decision))
    assertEquals(said(power, run.events), Vector.empty)
    assert(Look(run.finishPlanWindows.state).inRegionalDiscard(card))

  test("a bandit defender never uses it, and the attacker's own copy is no " +
      "defender's plan"):
    val two = board(extras = 1, warbands = 5, supply = 7)
    val bandit = withSiteCard(two, two.extras.head, card)
    assert(awaits(commit(rules(losingDice), bandit, 2), bandit.actor,
      CampaignIds.sacrifice))
    val attacker = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    assert(awaits(commit(rules(losingDice), attacker, 2), attacker.actor,
      CampaignIds.sacrifice))

  test("the recorded moves replay to the same state, and survive " +
      "the journal wire"):
    val b = defending
    val g = rules(losingDice)
    val run = commit(g, b, 4).pick(b.rival, CampaignIds.defenderPlan, ref)
      .answer(b.rival, decision,
        arrangement(b, board = 0, origin = 4, extra = 3))
    assertEquals(ActPowerHarness.replayed(g, b.ready, run.events),
      ready(run.state))
    assertWireRoundTrips(run.events)

  // ---- Lines ----

  private val power = WarningSignals.forCatalog(catalog)
  test("the arrangement is written as the Signals' line, after the answer"):
    val b = defending
    val asked = chosen(b)
    assertEquals(said(power, asked.events), Vector.empty)
    val done = asked.answer(b.rival, decision,
      arrangement(b, board = 1, origin = 3, extra = 3))
    assertEquals(said(power, done.events), Vector(NoteText.Said("redistributed",
      s"${b.rival.value} redistributed their warbands.", covers = false)))

  test("a defender with nowhere to move to writes nothing"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.boardWarbands(base.rival, 3))
    val done = commit(rules(losingDice), b, 2, raid = true).pick(b.rival,
      CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(said(power, done.events), Vector.empty)
