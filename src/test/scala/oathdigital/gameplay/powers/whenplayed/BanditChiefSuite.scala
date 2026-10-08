package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.{p1, p2}

class BanditChiefSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[BanditChief.type]
  private val chief = power.cardId
  private val atHome = hookAt(chief, homeSite)
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")

  test("one warband dies at every site not ruled by bandits, p1's own " +
      "included, with one line per site in map order"):
    // Bandit Chief stands at p1's site, where p1 has 2 warbands. p2 has 1
    // at Broken Peaks, and bandits hold Buried Giant.
    val ready = Table.start.denizen(chief, at = homeSite)
      .warbandsAt(homeSite, p1, 2).warbandsAt(peaks, p2, 1)
      .bandits(giant, 2).ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    val end = Look(done.treeless)
    assertEquals(end.forces(homeSite),
      SiteForces.Occupied(end.forceKind(p1), 1))
    assertEquals(end.forces(peaks), SiteForces.Empty)
    assertEquals(end.forces(giant), SiteForces.Occupied(ForceKind.Bandit, 2))
    val mapOrder = ready.game.current.map.inPlay
    assertEquals(said(power, done.events), Vector(homeSite -> p1, peaks -> p2)
      .sortBy(entry => mapOrder.indexOf(entry._1))
      .map((site, owner) => NoteText.Said("killed",
        s"Killed 1 ${owner.value} warband at ${site.value}.",
        covers = false)))
    assertJournalRebuilds(ready, done.events, done.treeless)

  test("with only bandits and empty sites nothing dies, and the line says " +
      "so"):
    val ready = Table.start.denizen(chief, at = homeSite).bandits(giant, 2)
      .ready
    val done = Outcomes.finished(fire(ready, power, atHome))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      "No warband was killed.", covers = false)))

  test("played from a Search, a site the kill empties is refilled with " +
      "bandits"):
    val ready = Table.from(SearchFixture.staged(Vector(chief), supply = 5))
      .warbandsAt(peaks, p2, 1).ready
    val placed = SearchFixture.play(ready, Vector.empty, chief,
      PlayedAs.AtSite)
    val capacity = catalog.site(peaks).fold(0)(_.capacity)
    assertEquals(Look(SearchFixture.after(placed)).forces(peaks),
      SiteForces.Occupied(ForceKind.Bandit, capacity))
