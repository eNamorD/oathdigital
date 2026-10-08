package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.PowerFixture.warbandBank
import oathdigital.gameplay.WalkerRecordedOpsReducer
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.rejected

class GarrisonSuite extends munit.FunSuite with WalkerRecordedOpsReducer:
  import WhenPlayedHarness._

  private val power = registered[Garrison.type]
  private val card = power.cardId
  private val played = hook(card)
  private val kind = Look(Table.start.ready).forceKind(p1)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val buriedGiant = CatalogNames.site("Buried Giant")
  private val sites = Vector(ancientCity, brokenPeaks, buriedGiant)

  /** p1 holds Garrison, rules the first `counts.size` of Ancient City, Broken
    * Peaks and Buried Giant with `counts` warbands each, and has `onBoard`
    * warbands on the board.
    */
  private def ruling(counts: Vector[Int], onBoard: Int): ReadyGame =
    sites.zip(counts).foldLeft(Table.start.adviser(p1, card)) {
      case (table, (site, n)) => table.warbandsAt(site, p1, n)
    }.boardWarbands(p1, onBoard).ready

  private def forcesAt(ready: ReadyGame, site: SiteId): SiteForces =
    ready.game.current.map.sites(site).forces

  private def chooseSites(chosen: SiteId*): DecisionAnswer =
    DecisionAnswer.ChooseManyAnswer(
      chosen.toVector.map(DecisionOptionRef.Site(_)))

  test("it gains one warband per ruled site and puts one on each"):
    val ready = ruling(Vector(1, 1, 1), onBoard = 5)
    val done = Outcomes.finished(play(ready, power, card))
    sites.foreach(site => assertEquals(forcesAt(done.treeless, site),
      SiteForces.Occupied(kind, 2)))
    // 5 on the board, 3 gained from the bank and 3 placed.
    assertEquals(Look(done.treeless).player(p1).board.warbands, 5)
    assertJournalRebuilds(ready, done.events, done.treeless)

  test("a player who rules no site gains and places nothing"):
    val ready = ruling(Vector.empty, onBoard = 5)
    val done = Outcomes.finished(play(ready, power, card))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(Look(done.treeless).player(p1).board.warbands, 5)

  test("a short bank leaves a short board, and the player chooses the sites"):
    // 13 of the lineage's 14 warbands sit at the three ruled sites and none
    // on the board, so the bank holds one and the gain is capped at one.
    val ready = ruling(Vector(4, 4, 5), onBoard = 0)
    assertEquals(warbandBank(ready, kind), 1)
    val first = Outcomes.parked(play(ready, power, card))
    // The state the walk had reached at the choice: its recorded gain applied.
    val atChoice = foldRecordedOps(ready, first.events, "gain did not replay")
    assertEquals(Look(atChoice).player(p1).board.warbands, 1)
    val decide = question(atChoice, power, played, first.tree)
    assertEquals(decide.decisionId, Garrison.decisionId)
    assertEquals(decide.owner, p1)
    assertEquals(decide.query, DecisionQuery.ChooseMany(1, 1, sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))),
      decide.query.heading))

    val done = Outcomes.finished(resume(atChoice, power, played, first.tree,
      Garrison.decisionId, chooseSites(brokenPeaks)))
    assertEquals(forcesAt(done.treeless, brokenPeaks),
      SiteForces.Occupied(kind, 4 + 1))
    assertEquals(forcesAt(done.treeless, ancientCity),
      SiteForces.Occupied(kind, 4))
    assertEquals(forcesAt(done.treeless, buriedGiant),
      SiteForces.Occupied(kind, 5))
    assertEquals(Look(done.treeless).player(p1).board.warbands, 0)
    assertJournalRebuilds(ready, first.events ++ done.events, done.treeless)

  test("an empty board and an empty bank ask nothing"):
    // All 14 of the lineage's warbands are at the sites.
    val ready = ruling(Vector(5, 5, 4), onBoard = 0)
    assertEquals(warbandBank(ready, kind), 0)
    val done = Outcomes.finished(play(ready, power, card))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)

  test("a choice naming an unruled site is rejected"):
    val ready = ruling(Vector(4, 4, 5), onBoard = 0)
    val first = Outcomes.parked(play(ready, power, card))
    // The state the walk had reached at the choice: its recorded gain applied.
    val atChoice = foldRecordedOps(ready, first.events, "gain did not replay")
    val unruled = CatalogNames.site("Deep Woods")
    assertEquals(rejected(resume(atChoice, power, played, first.tree,
      Garrison.decisionId, chooseSites(unruled))), OathViolation
      .InvalidEventOrder(s"decision ${Garrison.decisionId} does not offer " +
        "a selected option"))
