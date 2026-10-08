package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Shield Wall: a defender's plan that adds two defense dice, and kills its
  * user's whole force when they are defeated. */
class ShieldWallSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Shield Wall")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(ShieldWall.id, ShieldWall.noteKeys, run.events)

  private def killed(who: PlayerId): NoteText.Said = NoteText.Said("killed",
    s"${who.value}'s whole force was killed.", covers = false)

  /** The rival rules the origin with two warbands, has three on its board,
    * holds the Oathkeeper title and Shield Wall faceup, and has the favor to
    * place on it. */
  private def rivalHoldsShieldWall: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.boardWarbands(base.rival, 3).favor(base.rival, ShieldWall.Favor))

  test("it adds two defense dice"):
    val b = rivalHoldsShieldWall
    val run = commit(rules(winningDice), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    // Printed on the card, not read from the power's constant.
    assertEquals(Look(picked.state).diceInPool(CampaignIds.defensePool) -
      Look(run.state).diceInPool(CampaignIds.defensePool), 2)

  test("a defeated Conquest defender gets none of its warbands back"):
    val b = rivalHoldsShieldWall
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Four swords beat the two warbands and four blank dice. Both warbands
    // at the origin die, and the one the victory returns dies again.
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.rival), 3)
    assertEquals(lines(done), Vector(killed(b.rival)))

  test("a defeated Raid defender loses its whole board"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.boardWarbands(base.rival, 3).favor(base.rival, ShieldWall.Favor))
    val won = commit(rules(winningDice), b, 4, raid = true)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Four swords beat the board's three warbands and four blank dice.
    assert(awaits(won, b.actor, CampaignIds.relocation))
    val done = won.pick(b.actor, CampaignIds.relocation,
      won.offered(b.actor).head)
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).boardWarbands(b.rival), 0)
    assertEquals(lines(done), Vector(killed(b.rival)))

  test("a defender who wins loses nothing more"):
    val b = rivalHoldsShieldWall
    val done = commit(rules(losingDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    // Four hollow swords make 2, against the origin's two warbands.
    assertEquals(done.winner, Some(false))
    assertEquals(Look(done.state).boardWarbands(b.rival), 3)
    assertEquals(ready(done.state).game.current.map.sites(b.origin).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 2))
    assertEquals(lines(done), Vector.empty)

  test("Hospital saves the warbands Shield Wall kills"):
    val hospital = CatalogNames.denizen("Hospital")
    // Broken Peaks, the first site after the origin, which nobody else rules.
    val site = CatalogNames.site("Broken Peaks")
    val shielded = rivalHoldsShieldWall
    val b = withSiteCard(on(shielded)(_.warbandsAt(site, shielded.rival, 2)),
      site, hospital)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(hospital))
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    // Both origin warbands stayed dead, the returned one killed again, and
    // Hospital places both on its site beside the two already there.
    assertEquals(Look(done.state).boardWarbands(b.rival), 3)
    assertEquals(ready(done.state).game.current.map.sites(site).forces,
      SiteForces.Occupied(ForceKind.Exile(b.player(b.rival).lineage), 4))
    assertEquals(lines(done), Vector(killed(b.rival)))

  test("with Sticky Fire also killing the force, the returned warband dies " +
      "once"):
    val fire = CatalogNames.relic("Sticky Fire")
    val b = withRelic(rivalHoldsShieldWall, fire)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, DecisionOptionRef.Relic(fire))
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .finishDefenderPlans(b.rival)
      .declineSacrifice(b.actor)
      .pick(b.actor, StickyFire.decisionId, StickyFire.yes).finishPlanWindows
    assertEquals(done.winner, Some(true))
    // Shield Wall kills the returned warband. Sticky Fire finds nothing more
    // to kill, and the three the rival held before the Campaign stay.
    assertEquals(Look(done.state).boardWarbands(b.rival), 3)
    assertEquals(lines(done), Vector(killed(b.rival)))
