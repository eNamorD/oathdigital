package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Refusals,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class SiegeEnginesSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val engines = CatalogNames.denizen("Siege Engines")
  private val source = DecisionOptionRef.Denizen(engines)
  private val victim = Table.p2
  /** p1's site, Ancient City, and Broken Peaks lie in the Cradle; Buried
    * Giant lies in the Provinces. */
  private val home = CatalogNames.site("Ancient City")
  private val neighbour = CatalogNames.site("Broken Peaks")
  private val faraway = CatalogNames.site("Buried Giant")

  private def forcesAt(ready: ReadyGame, site: SiteId) =
    ready.game.current.map.sites(site).forces
  private def choose(site: SiteId) = pick(DecisionOptionRef.Site(site))

  /** p1's Act at Ancient City with Siege Engines at the site and `favor`
    * favor. */
  private def stagedTable(favor: Int = 1): Table =
    Table.start.turn(actor, Phase.Act).pawn(actor, home)
      .denizen(engines, at = home).favor(actor, favor)
  private def staged(favor: Int = 1): ReadyGame = stagedTable(favor).ready

  /** The state Siege Engines was used from, the walk it started, and the
    * walk after the site was chosen. */
  private case class Killed(before: ReadyGame, started: OathTransition,
      answered: OathTransition)

  /** Uses it and answers `site`, with `forces` staged first. */
  private def killAt(site: SiteId, forces: Table => Table): Killed =
    val before = forces(stagedTable()).ready
    val started = accepted(use(before, SiegeEngines, source))
    Killed(before, started, accepted(answer(started, actor,
      SiegeEngines.decisionId, choose(site))))

  test("it places a favor on its card and offers every site in the " +
      "actor's region"):
    val t = accepted(use(staged(), SiegeEngines, source))
    assert(awaits(t, SiegeEngines.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor).map(_.toSet),
      Some(Set("site" -> home.value, "site" -> neighbour.value)))
    assertEquals(Look(after(t)).tokensOn(engines), Tokens(1, 0))
    assertEquals(Look(after(t)).player(actor).board.favor, 0)

  test("it kills two of the chosen site's warbands, which return to their " +
      "bank"):
    val Killed(ready, t, done) =
      killAt(neighbour, _.warbandsAt(neighbour, victim, 3))
    val kind = Look(ready).forceKind(victim)
    assertEquals(forcesAt(after(done), neighbour), SiteForces.Occupied(kind, 1))
    assertEquals(warbandBank(after(done), kind), warbandBank(after(t), kind) + 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 2 ${victim.value} warbands at ${neighbour.value}.",
        covers = false)))

  test("the actor's own warbands die too, and the emptied site fills with " +
      "bandits"):
    val done = killAt(home, _.warbandsAt(home, actor, 2)).answered
    val capacity = 3 // Ancient City's printed bandit capacity
    assertEquals(forcesAt(after(done), home),
      SiteForces.Occupied(ForceKind.Bandit, capacity))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 2 ${actor.value} warbands at ${home.value}.", covers = false)))

  test("a site with one warband loses it"):
    val done = killAt(neighbour, _.warbandsAt(neighbour, victim, 1)).answered
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 1 ${victim.value} warband at ${neighbour.value}.",
        covers = false)))

  test("bandits die with their own line"):
    val done = killAt(neighbour, _.bandits(neighbour, 3)).answered
    assertEquals(forcesAt(after(done), neighbour),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said("used.bandits",
        s"Killed 2 bandit warbands at ${neighbour.value}.", covers = false)))

  test("a site with no warband loses none, and the line says so"):
    // The quiet table leaves Broken Peaks empty.
    val done = killAt(neighbour, identity).answered
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said("used.none",
        s"${neighbour.value} had no warband to kill.", covers = false)))

  test("a site outside the actor's region is refused"):
    val t = accepted(use(staged(), SiegeEngines, source))
    assertEquals(
      answer(t, actor, SiegeEngines.decisionId, choose(faraway)).left.toOption,
      Some(Refusals.notOffered(SiegeEngines.decisionId)))

  test("it is unusable without a favor to place"):
    val ready = staged(favor = 0)
    assert(!usableNow(ready).exists(_.power.id == SiegeEngines.id))
    assertEquals(use(ready, SiegeEngines, source).left.toOption,
      Some(OathViolation.InsufficientFavor(1, 0)))
