package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Bear Traps: a free defender's plan that removes an attack die and kills
  * a warband on the attacker's board, beyond the committed force. */
class BearTrapsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Bear Traps")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  /** One warband killed from `player`'s board. */
  private def killedFrom(b: Board, player: PlayerId): CoreOperation =
    Kill(Piece.Warbands(Look(b.ready).forceKind(player), 1),
      PositionedLocation(Location.PlayArea(player)))

  /** p2 rules the origin with 2 warbands and the Oathkeeper title, and
    * holds Bear Traps faceup. p1 has 5 warbands. */
  private def defender: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, card, Orientation.FaceUp)

  test("a defender removes an attack die and kills a warband beyond the " +
      "force"):
    val b = defender
    val run = commit(rules(losingDice), b, 3)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, -1)))
    assert(picked.since(run).contains(killedFrom(b, b.actor)))
    assertEquals(Look(picked.state).boardWarbands(b.actor), 4)
    assertEquals(said(BearTraps, picked.events), Vector(NoteText.Said(
      "killed", s"Killed 1 warband on ${b.actor.value}'s board.",
      covers = true)))

  test("Bear Traps kills a board warband the force does not need, " +
      "counting the origin's"):
    // p1 rules the rival's site with 2 warbands and holds 1 on the board;
    // a force of 2 leaves the board's warband outside it.
    val (base, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val b = withAdviserFor(base, base.rival, card, Orientation.FaceUp)
    val game = rules(losingDice)
    val run = committed(game, campaignQueuedFrom(game, b, away,
      PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))), b,
      force = 2, raid = true).finishAttackerPlans(b.actor)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(killedFrom(b, b.actor)))
    assertEquals(Look(picked.state).boardWarbands(b.actor), 0)

  test("with every warband on the board and at the origin committed " +
      "nothing dies"):
    val (base, away) = rivalAloneAtASiteTheActorHolds(board = 1, atSite = 2)
    val b = withAdviserFor(base, base.rival, card, Orientation.FaceUp)
    val game = rules(losingDice)
    val run = committed(game, campaignQueuedFrom(game, b, away,
      PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))), b,
      force = 3, raid = true).finishAttackerPlans(b.actor)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(Look(picked.state).boardWarbands(b.actor), 1)

  test("with every warband committed nothing dies"):
    val b = defender
    val run = commit(rules(losingDice), b, 5)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, -1)))
    assertEquals(Look(picked.state).boardWarbands(b.actor), 5)
    assertEquals(said(BearTraps, picked.events), Vector.empty)

  test("bandits apply it at a site they rule"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    // Three of four dice are left; three swords beat the two Bandits, so
    // no warband of the force dies.
    val done = commit(rules(winningDice), b, 4).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -1)))
    assert(done.ops.contains(killedFrom(b, b.actor)))
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.actor), 4)
