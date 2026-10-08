package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.{NoteText, SearchFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Forced Labor's ruler toll on a Search. The toll itself, including the
  * favor burnt when bandits rule, is shared with Curfew (`RulerToll`) and
  * pinned in [[oathdigital.gameplay.powers.economy.CurfewSuite]]. */
class ForcedLaborSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val labor = CatalogNames.denizen("Forced Labor")
  private val power = ForcedLabor.forCatalog(catalog)
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** Where Forced Labor stands. */
  private val peaks = CatalogNames.site("Broken Peaks")
  /** Plain denizens for the world deck; none is played. */
  private val deckTop = Vector("Wrestlers", "Errand Boy", "Mercenaries")
    .map(CatalogNames.denizen(_))
  private val startingSupply = 5

  /** p1 in Act at Ancient City with `startingSupply` Supply and the start's
    * 1 favor, the world deck topped by three plain cards, and Forced Labor
    * at Broken Peaks. Nobody rules either site yet. */
  private def laborTable: Table = Table.start.pawn(p1, at = home)
    .worldDeckTop(deckTop*)
    .supply(p1, startingSupply).denizen(labor, at = peaks)

  /** p2 rules Broken Peaks, so Forced Labor is p2's, and Ancient City, where
    * p1's pawn stands. */
  private def rivalRulesBoth: Table =
    laborTable.warbandsAt(peaks, p2, 1).warbandsAt(home, p2, 1)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("an enemy whose pawn stands at a site the ruler rules gives the ruler " +
      "1 favor on top of the Search's Supply"):
    val ready = rivalRulesBoth.ready
    val started = accepted(SearchFixture.start(ready))
    val after = Look(SearchFixture.after(started))
    assertEquals(after.favor(p1), 0)
    assertEquals(after.favor(p2), Look(ready).favor(p2) + 1)
    // The first world Search costs 2 Supply.
    assertEquals(after.supply(p1), startingSupply - 2)
    assertEquals(said(started.events), Vector(NoteText.Said("gave",
      s"Gave 1 favor to ${p2.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assertWireRoundTrips(started.events)

  test("a searcher who cannot pay is not offered the Search"):
    val automatic = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    val startable = (ready: ReadyGame) =>
      SearchProcedure.startable(catalog, ready, p1, automatic)
    assert(startable(rivalRulesBoth.ready))
    val broke = rivalRulesBoth.favor(p1, 0).ready
    assert(!startable(broke))
    assert(SearchFixture.start(broke).isLeft)

  test("the ruler searches without paying, and so does an enemy whose pawn " +
      "stands at a site the ruler does not rule"):
    val rulerSearches = laborTable.warbandsAt(peaks, p1, 1)
      .warbandsAt(home, p1, 1).ready
    val unruledSite = laborTable.warbandsAt(peaks, p2, 1).ready
    Vector(rulerSearches, unruledSite).foreach { ready =>
      val started = accepted(SearchFixture.start(ready))
      assertEquals(Look(SearchFixture.after(started)).favor(p1),
        Look(ready).favor(p1))
      assertEquals(said(started.events), Vector.empty)
    }
