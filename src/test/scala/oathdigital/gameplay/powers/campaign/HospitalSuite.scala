package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Hospital: once chosen, each of its user's warbands the Campaign kills is
  * saved, and placed on Hospital's site at the end of the Campaign if the
  * user rules it then. */
class HospitalSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Wandering")
  private val card = CatalogNames.denizen("Hospital")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def kind(b: Board, who: PlayerId): ForceKind =
    ForceKind.Exile(b.player(who).lineage)

  /** `who` rules `site` with two warbands, and Hospital stands there. */
  private def hospitalFor(b: Board, who: PlayerId, site: SiteId): Board =
    withSiteCard(on(b)(_.warbandsAt(site, who, 2)), site, card)

  /** Hospital's site, Broken Peaks: the first site after the origin. These
    * boards have no extras, so nobody else rules it. */
  private val hospitalSite = CatalogNames.site("Broken Peaks")

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Hospital.id, Vector(Hospital.placed), run.events)

  private def placed(amount: Int, who: PlayerId, site: SiteId): NoteText.Said =
    val noun = if amount == 1 then "warband" else "warbands"
    NoteText.Said("placed",
      s"Placed $amount ${who.value} $noun at ${site.value} instead.",
      covers = false)

  private def returned(b: Board, who: PlayerId, amount: Int, site: SiteId)
      : CoreOperation = Move(Piece.Warbands(kind(b, who), amount),
    PositionedLocation(Location.WarbandBank(kind(b, who))),
    PositionedLocation(Location.Site(site)))

  test("a defeated attacker's dead are placed on Hospital's site at the end"):
    val base = board(warbands = 5, supply = 7)
    val b = hospitalFor(base, base.actor, hospitalSite)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    // Half of the four survivors die in the losses, then come back to
    // Hospital's site from the supply when the Campaign ends.
    assert(done.ops.contains(Kill(Piece.Warbands(kind(b, b.actor), 2),
      PositionedLocation(Location.PlayArea(b.actor)))))
    assert(done.ops.contains(returned(b, b.actor, 2, hospitalSite)))
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.actor), 4))
    assertEquals(Look(done.state).player(b.actor).board.warbands, 3)
    assertEquals(lines(done), Vector(placed(2, b.actor, hospitalSite)))

  test("a defeated defender keeps the returned half on its board, and the " +
      "rest go to Hospital's site"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = hospitalFor(base, base.rival, hospitalSite)
    val before = b.player(b.rival).board.warbands
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    // Two warbands held the origin: one returns to the board as usual, and
    // the one that died is placed on Hospital's site.
    assertEquals(Look(done.state).player(b.rival).board.warbands, before + 1)
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.rival), 3))
    assertEquals(lines(done), Vector(placed(1, b.rival, hospitalSite)))

  test("a site its user no longer rules at the end saves nothing"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(!done.ops.contains(returned(b, b.rival, 1, b.origin)))
    // No saved warband comes back to the lost site.
    assert(Look(done.state).forces(b.origin) match
      case SiteForces.Occupied(held, _) => held != kind(b, b.rival)
      case SiteForces.Empty => true)
    assertEquals(lines(done), Vector.empty)

  test("the warbands Sticky Fire kills are saved too"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val fire = CatalogNames.relic("Sticky Fire")
    val held = hospitalFor(base, base.actor, hospitalSite)
    val b = withRelicFor(held, base.rival, fire)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan, DecisionOptionRef.Relic(fire))
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
      .pick(b.rival, StickyFire.decisionId, StickyFire.yes).finishPlanWindows
    assertEquals(done.winner, Some(false))
    // The two the defeat kills, then the three Sticky Fire kills from the
    // board, all come back to Hospital's site.
    assertEquals(Look(done.state).player(b.actor).board.warbands, 0)
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.actor), 7))
    assertEquals(lines(done), Vector(placed(5, b.actor, hospitalSite)))

  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val wrestlersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(wrestlers)

  /** The defender rules the origin and Hospital's site, and holds Wrestlers,
    * which sacrifices a warband from the origin, the only target. The
    * attacker wins: four swords against the one warband left and blank dice.
    */
  private def wrestling: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(hospitalFor(base, base.rival, hospitalSite), base.rival,
      wrestlers, Orientation.FaceUp)

  test("a sacrifice made after Hospital was chosen is saved, and placed at " +
      "the end"):
    val b = wrestling
    val before = b.player(b.rival).board.warbands
    val picked = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan, wrestlersRef)
    assertEquals(picked.ops.collect { case s: Sacrifice => s },
      Vector(Sacrifice(b.rival, Piece.Warbands(kind(b, b.rival), 1),
        PositionedLocation(Location.Site(b.origin)))))
    // Until the end the sacrificed warband is dead: it leaves the force.
    assertEquals(Look(picked.state).forces(b.origin),
      SiteForces.Occupied(kind(b, b.rival), 1))
    assertEquals(Look(picked.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.rival), 2))
    val done = picked.finishPlanWindows
    assertEquals(done.winner, Some(true))
    // The last origin warband dies and returns to the board as the returned
    // half; only the sacrificed one is saved.
    assertEquals(Look(done.state).player(b.rival).board.warbands, before + 1)
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.rival), 3))
    assertEquals(lines(done), Vector(placed(1, b.rival, hospitalSite)))

  test("a sacrifice made before Hospital was chosen is not saved"):
    val b = wrestling
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, wrestlersRef)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.rival), 2))
    assertEquals(lines(done), Vector.empty)

  test("a Raid defender's dead from the board are placed on Hospital's site " +
      "at the end"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = on(hospitalFor(base, base.rival, hospitalSite))(
      _.boardWarbands(base.rival, 3))
    val won = commit(rules(winningDice), b, 4, raid = true)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // The defeated pawn is moved on before the Campaign ends.
    assert(awaits(won, b.actor, CampaignIds.relocation))
    val done = won.pick(b.actor, CampaignIds.relocation,
      won.offered(b.actor).head)
    parked.assertNotParked(done.state)
    assertEquals(done.winner, Some(true))
    // The Raid kills half of the three on the board, rounded down; the one
    // killed comes back to Hospital's site, not to the board.
    assertEquals(Look(done.state).player(b.rival).board.warbands, 2)
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.rival), 3))
    assertEquals(lines(done), Vector(placed(1, b.rival, hospitalSite)))

  test("a player who does not rule its site is not offered it"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, hospitalSite, card)
    assert(awaits(commit(rules(winningDice), b, 2), b.actor,
      CampaignIds.sacrifice))

  test("a bandit defender does not use it"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assert(!ready(run.state).game.current.rollPools.contains(
      CampaignPlans.appliedMarker(ref)))

  test("a holder of Vow of Wandering has the dead killed as usual"):
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(hospitalFor(base, base.actor, hospitalSite), vow,
      Orientation.FaceUp)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assert(!done.ops.contains(returned(b, b.actor, 2, hospitalSite)))
    assertEquals(Look(done.state).forces(hospitalSite),
      SiteForces.Occupied(kind(b, b.actor), 2))
    assertEquals(lines(done), Vector.empty)
