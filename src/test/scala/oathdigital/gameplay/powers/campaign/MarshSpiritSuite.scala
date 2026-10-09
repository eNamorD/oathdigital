package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Marsh Spirit: players who target its site cannot use battle plans. */
class MarshSpiritSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Marsh Spirit")
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val outriders = CatalogNames.denizen("Outriders")

  /** `b`, with the actor holding Longbows faceup and Marsh Spirit standing
    * at `site`. */
  private def longbowsWithMarshAt(b: Board, site: SiteId): Board =
    withSiteCard(withAdviser(b, longbows, Orientation.FaceUp), site, card)

  test("an attacker who targets its site is not asked for a plan"):
    val base = board(warbands = 5, supply = 7)
    val b = longbowsWithMarshAt(base, base.origin)
    val run = commit(rules(winningDice), b, 3)
    assert(!awaits(run, b.actor, CampaignIds.attackerPlan))
    val ended = run.finishPlanWindows
    assert(!ended.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assertEquals(said(MarshSpirit, ended.events), Vector.empty)

  test("a site that is not targeted withholds nothing"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val b = longbowsWithMarshAt(base, base.extras.head)
    assert(commit(rules(winningDice), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, longbowsRef))

  test("an optional target counts"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val extra = base.extras.head
    val b = longbowsWithMarshAt(base, extra)
    val run = commit(rules(winningDice), b, 3,
      targets = Vector(DecisionOptionRef.Site(extra)))
    assert(!awaits(run, b.actor, CampaignIds.attackerPlan))

  test("the defender's plans are not affected"):
    // The rival rules the origin, where Marsh Spirit stands, and holds the
    // title and Longbows; the actor holds Outriders.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withSiteCard(withAdviserFor(withAdviser(base, outriders,
      Orientation.FaceUp), base.rival, longbows, Orientation.FaceUp),
      base.origin, card)
    val run = commit(rules(winningDice), b, 3)
    assert(!awaits(run, b.actor, CampaignIds.attackerPlan))
    assert(run.offers(b.actor, b.rival, CampaignIds.defenderPlan,
      longbowsRef))

  test("a Raid targets no site"):
    val base = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val b = longbowsWithMarshAt(on(base)(_.boardWarbands(base.rival, 1)),
      base.origin)
    assert(commit(rules(winningDice), b, 2, raid = true).offers(b.actor,
      b.actor, CampaignIds.attackerPlan, longbowsRef))

  test("its reason reads as ruled"):
    assertEquals(NoteText.sentence(MarshSpirit.noteKeys,
      MarshSpirit.mired(PowerSourceRef.Card(card))),
      "Players who target its site cannot use battle plans.")
