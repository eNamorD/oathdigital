package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** The Rampart edifice: two more defense dice for its ruler when their pawn is
  * at its site or the site is targeted (intact), one more when it is targeted
  * (ruined). It is a plan of the site's ruler, and a bandit ruler applies it
  * without choosing.
  */
class RampartSuite extends munit.FunSuite:
  // Towering Rampart intact, Cracked Rampart ruined.
  private val edifice = CatalogNames.edifice("Towering Rampart")
  private val defensePool = CampaignIds.defensePool

  /** The other player rules the origin, which the Campaign targets. */
  private def targeted(face: EdificeSide): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withEdifice(base, base.origin, edifice, face)

  /** The other player also rules a second site, which is not targeted. */
  private def untargeted(face: EdificeSide, pawnThere: Boolean): Board =
    val two = rivalRulesOriginAndHoldsOathkeeper(
      board(extras = 1, warbands = 5, supply = 7))
    val extra = two.extras.head
    val lineage = two.player(two.rival).lineage
    val ruled = two.copy(ready = two.ready.updateCurrent(current =>
      current.copy(map = current.map.copy(sites = current.map.sites.updated(
        extra, current.map.sites(extra).copy(forces = SiteForces.Occupied(
          ForceKind.Exile(lineage), 2)))))))
    val staged = withEdifice(ruled, extra, edifice, face)
    if !pawnThere then staged
    else staged.copy(ready = staged.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if p.player == staged.rival then p.copy(pawnSite = Some(extra)) else p))))

  private val edificeRef: DecisionOptionRef =
    DecisionOptionRef.Edifice(edifice)

  test("Towering Rampart at a targeted site adds two defense dice"):
    val b = targeted(EdificeSide.Intact)
    val run = commit(rules(losing), b, 4)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, edificeRef)
    assertEquals(picked.since(run), Vector[CoreOperation](
      ModifyDicePool(defensePool, 2)))

  test("Towering Rampart at a site that is neither targeted nor the ruler's pawn's is not offered"):
    val b = untargeted(EdificeSide.Intact, pawnThere = false)
    val run = commit(rules(losing), b, 4)
    // Only the title's plan is left to choose.
    assertEquals(run.offered(b.actor), Vector[DecisionOptionRef](
      DecisionOptionRef.Button("title"), CampaignIds.finish))

  test("Towering Rampart is offered where the ruler's pawn stands, though the site is not targeted"):
    val b = untargeted(EdificeSide.Intact, pawnThere = true)
    val run = commit(rules(losing), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, edificeRef)
    assertEquals(picked.since(run), Vector[CoreOperation](
      ModifyDicePool(defensePool, 2)))

  test("Cracked Rampart adds one defense die at a targeted site"):
    val b = targeted(EdificeSide.Ruined)
    val run = commit(rules(losing), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, edificeRef)
    assertEquals(picked.since(run), Vector[CoreOperation](
      ModifyDicePool(defensePool, 1)))

  test("Cracked Rampart is not offered where the site is not targeted, whatever the pawn does"):
    val b = untargeted(EdificeSide.Ruined, pawnThere = true)
    assertEquals(commit(rules(losing), b, 4).offered(b.actor),
      Vector[DecisionOptionRef](DecisionOptionRef.Button("title"),
        CampaignIds.finish))

  test("the intact face is the Towering plan and the ruined face the Cracked plan, never both"):
    val b = targeted(EdificeSide.Intact)
    val picked = commit(rules(losing), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, edificeRef)
    assertEquals(picked.offered(b.actor), Vector[DecisionOptionRef](
      DecisionOptionRef.Button("title"), CampaignIds.finish))

  test("a bandit ruler applies it without choosing, at a targeted site"):
    val base = board(warbands = 5, supply = 7)
    val b = withEdifice(base, base.origin, edifice, EdificeSide.Intact)
    def defensePoolChanges(run: Run): Int = run.ops.count:
      case ModifyDicePool(`defensePool`, _, _) => true
      case _ => false
    val before = commit(rules(losing), board(warbands = 5, supply = 7), 2)
    val run = commit(rules(losing), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assertEquals(defensePoolChanges(run), defensePoolChanges(before) + 1)
