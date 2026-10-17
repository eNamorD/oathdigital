package oathdigital.gameplay.campaign

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.QueuedStart
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes}

class CampaignOriginSuite extends munit.FunSuite:
  private val source =
    PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))

  /** The parked question's decision id, if any. */
  private def parkedDecision(run: Run): Option[String] =
    run.parkedHere.parkedDecision(run.state).map(_.decision)

  /** The warbands standing at `site`, whoever's they are. */
  private def warbandsAt(run: Run, site: SiteId): Int =
    Look(run.state).forces(site) match
      case SiteForces.Occupied(_, count) => count
      case SiteForces.Empty => 0

  test("a queued origin is where the Campaign judges from: a lone rival " +
      "there is the Raid's defender, though the pawn stands elsewhere"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val game = rules()
    val started = campaignQueuedFrom(game, b, away, source)
    val ready = Outcomes.ready(started.state)
    assertEquals(CampaignSetup.originOf(ready, b.actor), Some(away))
    assertEquals(CampaignSetup.raidDefenders(ready, b.actor),
      Vector(b.rival))
    assertEquals(Look(ready).pawn(b.actor), b.origin)

  test("a Campaign started without a source refuses a site argument"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val refused = rules().startWalker(Ready(b.ready), ActionRef.Campaign,
      b.actor, Vector(DecisionOptionRef.Site(away)))
    assert(refused.isLeft, refused)

  test("an entry with no source names no origin, so its site argument is " +
      "refused"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val sourceless = QueuedStart.startingFrom(b.ready,
      queuedCampaignEntry(b, away, source).copy(source = None))
    assertEquals(CampaignSetup.queuedOrigin(sourceless, b.actor), None)
    val refused = rules().startWalker(Ready(sourceless), ActionRef.Campaign,
      b.actor, Vector(DecisionOptionRef.Site(away)))
    assert(refused.isLeft, refused)

  test("the force question adds the warbands at the origin, names both " +
      "counts and suggests them all"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val game = rules()
    val run = Run(game, campaignQueuedFrom(game, b, away, source),
      Vector.empty)
    val asked = run.answeredUntil(CampaignIds.force)
    asked.query(b.actor) match
      case amount: DecisionQuery.ChooseAmount =>
        assertEquals((amount.min, amount.max, amount.suggested),
          (0, 3, Some(3)))
        val name = catalog.site(away).get.name
        assertEquals(amount.heading, Some("Commit warbands to the " +
          s"Campaign: 0 to 3 (1 on your board, 2 at $name), each adds " +
          "one attack die"))
      case other => fail(s"expected the force question, got $other")

  test("a queued Campaign spends no Supply"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val game = rules(winningDice)
    val done = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 3, raid = true)
    assertEquals(Look(done.state).supply(b.actor), 7)

  test("a queued Campaign from the pawn site with no warbands there keeps " +
      "today's force heading"):
    val b = withEnemyAtOrigin(CampaignFixture.board(warbands = 2,
      supply = 7))
    val game = rules()
    val run = Run(game, campaignQueuedFrom(game, b, b.origin, source),
      Vector.empty)
    run.answeredUntil(CampaignIds.force).query(b.actor) match
      case amount: DecisionQuery.ChooseAmount =>
        assertEquals(amount.heading, Some("Commit warbands to the " +
          "Campaign: 0 to 2, each adds one attack die"))
      case other => fail(s"expected the force question, got $other")

  test("a replayed queued Campaign reaches the same state"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val game = rules(winningDice)
    val done = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 3, raid = true)
    assertEquals(ActPowerHarness.replayed(game,
      dequeuedCampaignFrom(b, away, source), done.events),
      Outcomes.ready(done.state))

  test("a defeated force with a real choice asks how many of the dead " +
      "come from the origin, suggesting the least, and kills from both"):
    // Four committed, two swords against three: a defeat, so two die.
    // One stands on the board, so one or two come from the site.
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 3)
    val game = rules(losingDice)
    val fought = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 4, raid = true).finishPlanWindows
    assertEquals(parkedDecision(fought), Some(CampaignIds.originDeaths))
    fought.query(b.actor) match
      case amount: DecisionQuery.ChooseAmount =>
        assertEquals((amount.min, amount.max, amount.suggested),
          (1, 2, Some(1)))
        val name = catalog.site(away).get.name
        assertEquals(amount.heading, Some("Choose how many of the 2 dead " +
          s"warbands come from $name"))
      case other => fail(s"expected the split question, got $other")
    val done = fought.answer(b.actor, CampaignIds.originDeaths,
      ChooseAmountAnswer(2))
    assertEquals(Look(done.state).boardWarbands(b.actor), 1)
    assertEquals(warbandsAt(done, away), 1)

  test("a split with one possible value asks nothing and kills from the " +
      "site"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 0, atSite = 4)
    val game = rules(losingDice)
    val done = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 4, raid = true).finishPlanWindows
    assert(!awaits(done, b.actor, CampaignIds.originDeaths))
    assertEquals(done.winner, Some(false))
    assertEquals(warbandsAt(done, away), 2)

  test("the result records the origin's warbands and the board's before " +
      "any dies"):
    val (b, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 3)
    val game = rules(winningDice)
    val done = committed(game, campaignQueuedFrom(game, b, away, source),
      b, force = 4, raid = true).finishPlanWindows
    assertEquals(done.result.originForce,
      Some(OriginForce(away, atSite = 3, onBoard = 1)))
