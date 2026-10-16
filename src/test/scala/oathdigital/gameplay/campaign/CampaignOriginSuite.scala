package oathdigital.gameplay.campaign

import oathdigital.gameplay.CampaignFixture
import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes}

class CampaignOriginSuite extends munit.FunSuite:
  private val source =
    PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))

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
