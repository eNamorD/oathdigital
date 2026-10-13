package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer,
  DistributeAnswer}
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, MenuPlay}
import oathdigital.testkit.Outcomes.assertWireRoundTrips

/** Vow of Union's Campaign half: at the start of any Campaign its holder
  * may move warbands, in one direction, between their board and the sites
  * they rule in their region. A move offers the attacker the Skip, and a
  * move that leaves nothing to attack ends the Campaign. */
class VowOfUnionSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Union")
  /** In the Cradle, with Ancient City, every board's origin. */
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  /** In the Provinces. */
  private val buriedGiant = CatalogNames.site("Buried Giant")
  private val game = rules(winningDice)
  private val toBoardHeading = "Vow of Union: move warbands from the sites " +
    "you rule in your region to your board"
  private val toSitesHeading = "Vow of Union: move warbands from your " +
    "board to the sites you rule in your region"

  /** p1's Campaign on `b`, started and parked on its first question. */
  private def campaignStarted(b: Board): Run =
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign,
      b.actor).fold(error => fail(s"no start: $error"), identity)
    Run(game, started, started.events)

  /** p3, the bystander, holds Vow of Union on `orientation` and rules
    * Broken Peaks, in their region, with 2 warbands, and Buried Giant,
    * outside it, with 1. Their board holds `onBoard` warbands. p1 can only
    * conquer the Bandits at Ancient City. */
  private def bystanderHoldsVowRulingTwoSites(onBoard: Int = 3,
      orientation: Orientation = Orientation.FaceUp): Board =
    val b = board(warbands = 4, supply = 7)
    on(withAdviserFor(b, b.bystander, vow, orientation))(_
      .warbandsAt(brokenPeaks, b.bystander, 2)
      .warbandsAt(buriedGiant, b.bystander, 1)
      .boardWarbands(b.bystander, onBoard))

  /** A distribution slot from `min` to `max`, opening on `now`. */
  private def slotOpeningOn(ref: DecisionOptionRef, min: Int, max: Int,
      now: Int): DistributeSlot = DistributeSlot(ref, min, max, Some(now))

  /** A distribution answer: the player's board, then each site. */
  private def arranged(onBoard: (PlayerId, Int),
      sites: (SiteId, Int)*): DistributeAnswer =
    DistributeAnswer(DistributeAmount(DecisionOptionRef.Player(onBoard._1),
      onBoard._2) +: sites.toVector.map((site, count) =>
      DistributeAmount(DecisionOptionRef.Site(site), count)))

  private def siteWarbands(run: Run, site: SiteId): Int =
    Look(run.state).forces(site) match
      case SiteForces.Occupied(_, count) => count
      case _ => 0

  /** The kind question's options: `kinds`, then Skip Campaign. */
  private def kindsThenSkip(kinds: (String, String)*): Vector[DecisionOption] =
    kinds.toVector.map((key, label) => DecisionOption.Button(
      DecisionOptionRef.Button(key), label)) :+
      DecisionOption.Button(CampaignIds.skip, "Skip Campaign")

  test("an off-turn holder moves warbands from the sites they rule in " +
      "their region to their board, and the attacker may then skip"):
    val b = bystanderHoldsVowRulingTwoSites()
    val asked = campaignStarted(b)
    assert(awaits(asked, b.bystander, VowOfUnion.directionDecisionId))
    assertEquals(asked.options(b.actor), Vector[DecisionOption](
      DecisionOption.Button(VowOfUnion.toBoard,
        "Move warbands to your board"),
      DecisionOption.Button(VowOfUnion.toSites,
        "Move warbands to your sites"),
      DecisionOption.Button(VowOfUnion.stay, "Move none")))
    val way = asked.pick(b.bystander, VowOfUnion.directionDecisionId,
      VowOfUnion.toBoard)
    // Buried Giant lies outside the holder's region, so it has no slot.
    assertEquals(way.query(b.actor), DecisionQuery.Distribute.exactly(
      Vector(slotOpeningOn(DecisionOptionRef.Player(b.bystander), 3, 5, 3),
        slotOpeningOn(DecisionOptionRef.Site(brokenPeaks), 0, 2, 2)),
      5, Some(toBoardHeading), "Move warbands"))
    val done = way.answer(b.bystander, VowOfUnion.arrangeDecisionId,
      arranged(b.bystander -> 5, brokenPeaks -> 0))
    assertEquals(Look(done.state).boardWarbands(b.bystander), 5)
    assertEquals(siteWarbands(done, brokenPeaks), 0)
    assertEquals(siteWarbands(done, buriedGiant), 1)
    assert(awaits(done, b.actor, CampaignIds.kind))
    assertEquals(done.options(b.actor),
      kindsThenSkip("conquest" -> "Conquest"))
    assertEquals(said(VowOfUnion, done.events), Vector(NoteText.Said(
      "moved.board.holder",
      s"${b.bystander.value} moved 2 warbands from sites to their board.",
      covers = false)))
    assertWireRoundTrips(done.events)

  test("an attacker holding it commits the warbands it moved to their " +
      "board, and the line leaves them unnamed"):
    val base = board(warbands = 1, supply = 7)
    val b = on(withAdviser(base, vow, Orientation.FaceUp))(
      _.warbandsAt(brokenPeaks, base.actor, 3))
    val way = campaignStarted(b).pick(b.actor,
      VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
    val moved = way.answer(b.actor, VowOfUnion.arrangeDecisionId,
      arranged(b.actor -> 4, brokenPeaks -> 0))
    assertEquals(said(VowOfUnion, moved.events), Vector(NoteText.Said(
      "moved.board", "Moved 3 warbands from sites to the board.",
      covers = false)))
    val done = moved.pick(b.actor, CampaignIds.kind,
      DecisionOptionRef.Button("conquest"))
    // The force question reads the board after the moves.
    done.query(b.actor) match
      case DecisionQuery.ChooseAmount(min, max, _, _, _) =>
        assertEquals((min, max), (0, 4))
      case other => fail(s"expected the force question, got $other")
    val committed = done.answer(b.actor, CampaignIds.force,
      ChooseAmountAnswer(4))
    assert(!awaits(committed, b.actor, CampaignIds.force))

  test("an off-turn holder moves warbands from their board to their " +
      "sites, and no site may lose any"):
    val b = bystanderHoldsVowRulingTwoSites()
    val way = campaignStarted(b).pick(b.bystander,
      VowOfUnion.directionDecisionId, VowOfUnion.toSites)
    assertEquals(way.query(b.actor), DecisionQuery.Distribute.exactly(
      Vector(slotOpeningOn(DecisionOptionRef.Player(b.bystander), 0, 3, 3),
        slotOpeningOn(DecisionOptionRef.Site(brokenPeaks), 2, 5, 2)),
      5, Some(toSitesHeading), "Move warbands"))
    assert(way.refused(b.bystander, VowOfUnion.arrangeDecisionId,
      arranged(b.bystander -> 4, brokenPeaks -> 1)).isDefined)
    val done = way.answer(b.bystander, VowOfUnion.arrangeDecisionId,
      arranged(b.bystander -> 0, brokenPeaks -> 5))
    assertEquals(siteWarbands(done, brokenPeaks), 5)
    assertEquals(said(VowOfUnion, done.events), Vector(NoteText.Said(
      "moved.sites.holder",
      s"${b.bystander.value} moved 3 warbands from their board to sites.",
      covers = false)))

  test("it asks nothing without a ruled site in the holder's region or " +
      "facedown; moving nothing offers no Skip; an empty board's direction " +
      "is unavailable"):
    val base = board(warbands = 4, supply = 7)
    val outside = on(withAdviserFor(base, base.bystander, vow,
      Orientation.FaceUp))(_.warbandsAt(buriedGiant, base.bystander, 1))
    assert(awaits(campaignStarted(outside), base.actor, CampaignIds.force))
    val hidden = bystanderHoldsVowRulingTwoSites(
      orientation = Orientation.FaceDown)
    assert(awaits(campaignStarted(hidden), base.actor, CampaignIds.force))
    val b = bystanderHoldsVowRulingTwoSites()
    val stayed = campaignStarted(b).pick(b.bystander,
      VowOfUnion.directionDecisionId, VowOfUnion.stay)
    assert(awaits(stayed, b.actor, CampaignIds.force))
    assertEquals(Look(stayed.state).boardWarbands(b.bystander), 3)
    assertEquals(siteWarbands(stayed, brokenPeaks), 2)
    assertEquals(said(VowOfUnion, stayed.events), Vector.empty)
    val unchanged = campaignStarted(b).pick(b.bystander,
      VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
      .answer(b.bystander, VowOfUnion.arrangeDecisionId,
        arranged(b.bystander -> 3, brokenPeaks -> 2))
    assert(awaits(unchanged, b.actor, CampaignIds.force))
    assertEquals(said(VowOfUnion, unchanged.events), Vector.empty)
    val empty = bystanderHoldsVowRulingTwoSites(onBoard = 0)
    assertEquals(campaignStarted(empty).withheld(base.actor,
      VowOfUnion.toSites),
      Some(OptionReason.Written(VowOfUnion.noBoardWarbands)))

  test("emptying the attacker's only target ends the Campaign with its " +
      "Supply spent; with a Raid still legal, the attacker chooses"):
    val base = board(warbands = 4, supply = 7)
    /** p2, at Broken Peaks with Vow of Union, rules Ancient City, p1's
      * site, with 2 warbands. */
    val rivalWithVowRulesOrigin =
      on(withAdviserFor(base, base.rival, vow, Orientation.FaceUp))(
        _.warbandsAt(base.origin, base.rival, 2))
    def emptiedOrigin(b: Board): Run =
      val way = campaignStarted(b).pick(base.rival,
        VowOfUnion.directionDecisionId, VowOfUnion.toBoard)
      assertEquals(way.query(base.actor), DecisionQuery.Distribute.exactly(
        Vector(slotOpeningOn(DecisionOptionRef.Player(base.rival), 3, 5, 3),
          slotOpeningOn(DecisionOptionRef.Site(base.origin), 0, 2, 2)),
        5, Some(toBoardHeading), "Move warbands"))
      way.answer(base.rival, VowOfUnion.arrangeDecisionId,
        arranged(base.rival -> 5, base.origin -> 0))
    val ended = emptiedOrigin(rivalWithVowRulesOrigin)
    val current = ready(ended.state).game.current
    assertEquals(MenuPlay.procedureBeyondMenu(current), None)
    assertEquals(current.lastCampaignResult, None)
    // The emptied site is refilled with Bandits when the action ends.
    assertEquals(Look(ended.state).forces(base.origin),
      SiteForces.Occupied(ForceKind.Bandit, 3))
    assertEquals(Look(ended.state).supply(base.actor),
      7 - CampaignIds.SupplyCost)
    assertWireRoundTrips(ended.events)
    // With p2's pawn at Ancient City a Raid stays legal.
    val raidable = on(rivalWithVowRulesOrigin)(
      _.pawn(base.rival, at = base.origin))
    val asked = emptiedOrigin(raidable)
    assert(awaits(asked, base.actor, CampaignIds.kind))
    assertEquals(asked.options(base.actor), kindsThenSkip("raid" -> "Raid"))
