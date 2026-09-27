package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Hospital: once chosen, each of its user's warbands the Campaign kills is
  * saved, and placed on Hospital's site at the end of the Campaign if the
  * user rules it then. */
class HospitalSuite extends munit.FunSuite:
  private val card = cardWith("denizen.hospital")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))

  private def kind(b: Board, who: PlayerId): ForceKind =
    ForceKind.Exile(b.player(who).lineage)

  /** `who` rules `site` with two warbands, and Hospital stands there. */
  private def hospitalFor(b: Board, who: PlayerId, site: SiteId): Board =
    val held = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      map = current.map.copy(sites = current.map.sites.updated(site,
        current.map.sites(site).copy(forces =
          SiteForces.Occupied(kind(b, who), 2)))))))
    withSiteCard(held, site, card)

  /** A site that is neither the origin nor a bandit-ruled extra. */
  private def spare(b: Board): SiteId = b.ready.game.current.map.inPlay
    .find(site => site != b.origin && !b.extras.contains(site)).get

  private def forces(state: OathState, site: SiteId): SiteForces =
    ready(state).game.current.map.sites(site).forces

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

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
    val base = board()
    val site = spare(base)
    val b = hospitalFor(base, base.actor, site)
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    // Half of the four survivors die in the losses, then come back to
    // Hospital's site from the supply when the Campaign ends.
    assert(done.ops.contains(Kill(Piece.Warbands(kind(b, b.actor), 2),
      PositionedLocation(Location.PlayArea(b.actor)))))
    assert(done.ops.contains(returned(b, b.actor, 2, site)))
    assertEquals(forces(done.state, site), SiteForces.Occupied(kind(b, b.actor), 4))
    assertEquals(player(done.state, b.actor).board.warbands, 3)
    assertEquals(lines(done), Vector(placed(2, b.actor, site)))

  test("a defeated defender keeps the returned half on its board, and the rest go to Hospital's site"):
    val base = againstPlayer(board())
    val site = spare(base)
    val b = hospitalFor(base, base.other, site)
    val before = b.player(b.other).board.warbands
    val done = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(true))
    // Two warbands held the origin: one returns to the board as usual, and
    // the one that died is placed on Hospital's site.
    assertEquals(player(done.state, b.other).board.warbands, before + 1)
    assertEquals(forces(done.state, site), SiteForces.Occupied(kind(b, b.other), 3))
    assertEquals(lines(done), Vector(placed(1, b.other, site)))

  test("a site its user no longer rules at the end saves nothing"):
    val base = againstPlayer(board())
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(true))
    assert(!done.ops.contains(returned(b, b.other, 1, b.origin)))
    // No saved warband comes back to the lost site.
    assert(forces(done.state, b.origin) match
      case SiteForces.Occupied(held, _) => held != kind(b, b.other)
      case SiteForces.Empty => true)
    assertEquals(lines(done), Vector.empty)

  test("the warbands Sticky Fire kills are saved too"):
    val base = againstPlayer(board())
    val site = spare(base)
    val fire = relicWith("relic.sticky-fire")
    val b = withRelicFor(hospitalFor(base, base.actor, site), base.other, fire)
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, DecisionOptionRef.Relic(RelicId(fire)))
      .pick(b.other, CampaignIds.defenderPlan, CampaignIds.finish)
      .answer(b.actor, CampaignIds.sacrifice, DecisionAnswer.ChooseAmountAnswer(0))
      .pick(b.other, StickyFire.decisionId, StickyFire.yes).finish
    assertEquals(winner(done), Some(false))
    // The two the defeat kills, then the three Sticky Fire kills from the
    // board, all come back to Hospital's site.
    assertEquals(player(done.state, b.actor).board.warbands, 0)
    assertEquals(forces(done.state, site), SiteForces.Occupied(kind(b, b.actor), 7))
    assertEquals(lines(done), Vector(placed(5, b.actor, site)))

  private val wrestlers = cardWith("denizen.wrestlers")
  private val wrestlersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(wrestlers))

  /** The defender rules the origin and Hospital's site, and holds Wrestlers,
    * which sacrifices a warband from the origin, the only target. The
    * attacker wins: four swords against the one warband left and blank dice.
    */
  private def wrestling: (Board, SiteId) =
    val base = againstPlayer(board())
    val site = spare(base)
    (withAdviserFor(hospitalFor(base, base.other, site), base.other, wrestlers,
      Orientation.FaceUp), site)

  test("a sacrifice made after Hospital was chosen is saved, and placed at the end"):
    val (b, site) = wrestling
    val before = b.player(b.other).board.warbands
    val picked = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
      .pick(b.other, CampaignIds.defenderPlan, wrestlersRef)
    assert(picked.ops.exists(_.isInstanceOf[Sacrifice]))
    // Until the end the sacrificed warband is dead: it leaves the force.
    assertEquals(forces(picked.state, b.origin),
      SiteForces.Occupied(kind(b, b.other), 1))
    assertEquals(forces(picked.state, site), SiteForces.Occupied(kind(b, b.other), 2))
    val done = picked.finish
    assertEquals(winner(done), Some(true))
    // The last origin warband dies and returns to the board as the returned
    // half; only the sacrificed one is saved.
    assertEquals(player(done.state, b.other).board.warbands, before + 1)
    assertEquals(forces(done.state, site), SiteForces.Occupied(kind(b, b.other), 3))
    assertEquals(lines(done), Vector(placed(1, b.other, site)))

  test("a sacrifice made before Hospital was chosen is not saved"):
    val (b, site) = wrestling
    val done = commit(rules(winning), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, wrestlersRef)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(true))
    assertEquals(forces(done.state, site), SiteForces.Occupied(kind(b, b.other), 2))
    assertEquals(lines(done), Vector.empty)

  test("a player who does not rule its site is not offered it"):
    val base = board()
    val b = withSiteCard(base, spare(base), card)
    assert(awaits(commit(rules(winning), b, 2), b.actor, CampaignIds.sacrifice))

  test("a bandit defender does not use it"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(winning), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assert(!ready(run.state).game.current.rollPools.contains(
      CampaignPlans.appliedMarker(ref)))

  test("the card is found in the catalog and registered once"):
    assertEquals(PlanRules.forCatalog(catalog).count(_.id == Hospital.id), 1)
