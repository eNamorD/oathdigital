package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Cursed Cauldron: a victorious user gains a warband per enemy warband that
  * died in the Campaign, net of those that came back. */
class CursedCauldronSuite extends munit.FunSuite:
  private val relic = CatalogNames.relic("Cursed Cauldron")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)

  private def gained(who: PlayerId, amount: Int): NoteText.Said =
    val noun = if amount == 1 then "warband" else "warbands"
    NoteText.Said("gained", s"${who.value} gained $amount $noun.",
      covers = false)

  test("a victorious attacker gains a warband per bandit killed"):
    val base = board(warbands = 5, supply = 7)
    val b = withRelic(base, relic)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Both bandits at the origin died, and no survivor was placed.
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.actor), 7)
    assertEquals(said(CursedCauldron, done.events), Vector(gained(b.actor, 2)))

  test("a Conquest defender's returned warband did not die"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withRelic(base, relic)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    // Two died at the origin, and one of them returned to the rival's board.
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.actor), 6)
    assertEquals(said(CursedCauldron, done.events), Vector(gained(b.actor, 1)))

  test("a victorious defender gains a warband per attacker warband killed"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withRelicFor(base, base.rival, relic)
    val before = b.player(b.rival).board.warbands
    val done = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // The defeat killed half of the attacker's four survivors.
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).boardWarbands(b.rival), before + 2)
    assertEquals(said(CursedCauldron, done.events), Vector(gained(b.rival, 2)))

  test("a defeated user gains nothing"):
    val base = board(warbands = 5, supply = 7)
    val b = withRelic(base, relic)
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).boardWarbands(b.actor), 3)
    assertEquals(said(CursedCauldron, done.events), Vector.empty)

  test("a warband the enemy's Hospital places on its site did not die"):
    val hospital = CatalogNames.denizen("Hospital")
    // Broken Peaks, the first site after the origin, which nobody else rules.
    val site = CatalogNames.site("Broken Peaks")
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withRelic(withSiteCard(
      on(base)(_.warbandsAt(site, base.rival, 2)), site, hospital), relic)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(hospital))
      .finishPlanWindows
    // Two died at the origin: one returned to the rival's board, and Hospital
    // places the other on its site.
    assertEquals(done.winner, Some(true))
    assertEquals(ready(done.state).game.current.map.sites(site).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 3))
    assertEquals(Look(done.state).boardWarbands(b.actor), 5)
    assertEquals(said(CursedCauldron, done.events), Vector.empty)
