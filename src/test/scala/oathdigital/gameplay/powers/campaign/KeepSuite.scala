package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look}

/** Keep: a defender who rules Keep's site and loses a Raid gives up only
  * what was targeted, and their pawn goes to Keep's site. */
class KeepSuite extends munit.FunSuite:
  private val keep = CatalogNames.denizen("Keep")
  private val bookBurning = CatalogNames.denizen("Book Burning")

  /** An in-play site that is neither the Raid's origin nor where the
    * bystander stands, so a pawn sent there has moved. */
  private def keepSite(b: Board): SiteId =
    val bystander = Look(b.ready).player(b.bystander).pawnSite
    b.ready.game.current.map.inPlay
      .find(site => site != b.origin && !bystander.contains(site)).get

  /** The Raid board (the rival at the origin with Oracular Pig, a facedown
    * relic, three facedown advisers, both banners, 5 favor and 3 board
    * warbands), with Keep faceup at `site`, where `ruler` holds 2
    * warbands. */
  private def raidBoardWithKeepAt(site: Board => SiteId,
      ruler: Board => PlayerId): RaidBoard =
    val raid = raidBoard()
    val b = raid.board
    RaidBoard(on(b)(_.denizen(keep, at = site(b))
      .warbandsAt(site(b), ruler(b), 2)), raid.relic)

  /** A Raid on the rival's relic and People's Favor, committing the
    * actor's 4 warbands, played until it ends or asks something past the
    * plan windows. */
  private def raidOnRelicAndPeoplesFavor(b: Board, relic: RelicId,
      game: OathRules = rules(winningDice)): Run =
    commit(game, b, 4, targets = Vector(DecisionOptionRef.Relic(relic),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)), raid = true)
      .finishPlanWindows

  private def keepLine(defender: PlayerId, site: SiteId, covers: Boolean)
      : NoteText.Said = NoteText.Said("ended",
    s"Ended the Raid with ${defender.value}'s pawn at ${site.value}.",
    covers)

  test("a defeated defender who rules Keep's site loses only what was " +
      "targeted, and their pawn goes to Keep's site"):
    val raid = raidBoardWithKeepAt(keepSite, _.rival)
    val b = raid.board
    val site = keepSite(b)
    val done = raidOnRelicAndPeoplesFavor(b, raid.relic)
    assertEquals(done.winner, Some(true))
    val rival = Look(done.state).player(b.rival)
    // The takes stand, and the banner's favor goes back to the banks.
    assert(Look(done.state).player(b.actor).relics
      .exists(_.id == raid.relic))
    val after = ready(done.state).game.current
    assertEquals(BannerRules.holder(after, Banner.PeoplesFavor),
      Some(b.actor))
    assertEquals(after.banners.peoplesFavor.favor, 0)
    // No favor is burnt, and no facedown card is discarded or set aside.
    assertEquals(rival.board.favor, 5)
    assertEquals(rival.advisers.size, 3)
    assertEquals(rival.relics.map(_.id),
      Vector(RelicId("raid-facedown-relic")))
    assertEquals(after.setAsideRelics, Vector.empty)
    // The battle's losses stand: half of the 3 defending warbands.
    assertEquals(rival.board.warbands, 3 - 3 / 2)
    assertEquals(rival.pawnSite, Some(site))
    // Nothing more is asked: the Campaign is over.
    parked.assertResumed(done.state, Phase.Act, b.actor)
    assertEquals(said(KeepRule, done.events),
      Vector(keepLine(b.rival, site, covers = true)))
    assertEquals(ActPowerHarness.replayed(done.game, b.ready, done.events),
      ready(done.state))
    assertWireRoundTrips(done.events)

  test("with Keep at the Raid's own site, the defender's pawn stays " +
      "there"):
    val raid = raidBoardWithKeepAt(_.origin, _.rival)
    val b = raid.board
    val done = raidOnRelicAndPeoplesFavor(b, raid.relic)
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).player(b.rival).pawnSite, Some(b.origin))
    assertEquals(Look(done.state).player(b.rival).board.favor, 5)
    val pawnMoves = done.ops.collect {
      case move @ Move(Piece.Pawn(_), _, _, _) => move }
    assertEquals(pawnMoves, Vector.empty)
    parked.assertResumed(done.state, Phase.Act, b.actor)
    assertEquals(said(KeepRule, done.events),
      Vector(keepLine(b.rival, b.origin, covers = false)))

  test("Keep at a site another player rules changes nothing"):
    val raid = raidBoardWithKeepAt(keepSite, _.bystander)
    val b = raid.board
    val run = raidOnRelicAndPeoplesFavor(b, raid.relic)
    assert(awaits(run, b.actor, CampaignIds.relocation))
    assertEquals(Look(run.state).player(b.rival).board.favor, 5 - 5 / 2)
    assertEquals(Look(run.state).player(b.rival).advisers, Vector.empty)
    assertEquals(said(KeepRule, run.events), Vector.empty)

  test("the effects at the Campaign's end still run: Book Burning still " +
      "burns the defender's secrets"):
    val raid = raidBoardWithKeepAt(keepSite, _.rival)
    val b = withAdviser(on(raid.board)(_.secrets(raid.board.rival,
      faceUp = 2, faceDown = 2)), bookBurning, Orientation.FaceUp)
    val done = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(raid.relic)), raid = true)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(bookBurning))
      .finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).player(b.rival).pawnSite,
      Some(keepSite(b)))
    assertEquals(Look(done.state).player(b.rival).board.favor, 5)
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 1)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 0)
