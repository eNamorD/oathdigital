package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PlayerFacts,
  PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SiegeEnginesSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val engines = DenizenId("116")
  private val source = DecisionOptionRef.Denizen(engines)
  private val victim = others(base).head

  private def region(ready: ReadyGame): Region =
    ready.game.current.map.regionOf(home(ready)).get
  private def inRegion(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(
      ready.game.current.map.regionOf(_).contains(region(ready)))
  /** Another site in the actor's region. */
  private def neighbour(ready: ReadyGame): SiteId =
    inRegion(ready).find(_ != home(ready)).get
  private def faraway(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(!inRegion(ready).contains(_)).get
  private def withForces(ready: ReadyGame, site: SiteId, forces: SiteForces) =
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces = forces)))))
  private def forcesAt(ready: ReadyGame, site: SiteId) =
    ready.game.current.map.sites(site).forces
  private def kindOf(ready: ReadyGame, id: PlayerId) =
    PlayerFacts.forceKind(ready, id).toOption.get
  private def choose(site: SiteId) = pick(DecisionOptionRef.Site(site))
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == engines => d }.get

  /** Siege Engines at the actor's site with `favor` on the actor's board. */
  private def staged(favor: Int = 1) = inPhase(withBoard(atHome(base,
    engines))(_.copy(favor = favor)), Phase.Act)

  /** Uses it and answers `site`, with `forces` staged there first. */
  private def killAt(site: ReadyGame => SiteId, forces: ReadyGame => SiteForces) =
    val ready = withForces(staged(), site(staged()), forces(staged()))
    val t = use(ready, SiegeEngines, source).toOption.get
    val done = answer(t, actor, SiegeEngines.decisionId, choose(site(ready)))
      .toOption.get
    (ready, t, done)

  test("Siege Engines is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(SiegeEngines.id).isDefined)

  test("it places a favor on its card and offers every site in the " +
      "actor's region"):
    val t = use(staged(), SiegeEngines, source).toOption.get
    assert(awaits(t, SiegeEngines.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor).map(_.toSet),
      Some(inRegion(staged()).map(site => "site" -> site.value).toSet))
    assertEquals(cardOf(after(t)).tokens, Tokens(1, 0))
    assertEquals(player(after(t)).board.favor, 0)

  test("it kills two of the chosen site's warbands, which return to their " +
      "bank"):
    val (ready, t, done) = killAt(neighbour,
      r => SiteForces.Occupied(kindOf(r, victim), 3))
    val kind = kindOf(ready, victim)
    assertEquals(forcesAt(after(done), neighbour(ready)),
      SiteForces.Occupied(kind, 1))
    assertEquals(warbandBank(after(done), kind), warbandBank(after(t), kind) + 2)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 2 ${victim.value} warbands at ${neighbour(ready).value}.",
        covers = false)))

  test("the actor's own warbands die too, and the emptied site fills with " +
      "bandits"):
    val (ready, _, done) = killAt(home,
      r => SiteForces.Occupied(kindOf(r, actor), 2))
    val capacity = catalog.site(home(ready)).get.capacity
    assertEquals(forcesAt(after(done), home(ready)),
      SiteForces.Occupied(ForceKind.Bandit, capacity))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 2 ${actor.value} warbands at ${home(ready).value}.",
        covers = false)))

  test("a site with one warband loses it"):
    val (ready, _, done) = killAt(neighbour,
      r => SiteForces.Occupied(kindOf(r, victim), 1))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Killed 1 ${victim.value} warband at ${neighbour(ready).value}.",
        covers = false)))

  test("bandits die with their own line"):
    val (ready, _, done) = killAt(neighbour,
      _ => SiteForces.Occupied(ForceKind.Bandit, 3))
    assertEquals(forcesAt(after(done), neighbour(ready)),
      SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said("used.bandits",
        s"Killed 2 bandit warbands at ${neighbour(ready).value}.",
        covers = false)))

  test("a site with no warband loses none, and the line says so"):
    val (ready, _, done) = killAt(neighbour, _ => SiteForces.Empty)
    assertEquals(NoteText.said(SiegeEngines, done.events), Vector(
      NoteText.Said("used.none",
        s"${neighbour(ready).value} had no warband to kill.", covers = false)))

  test("a site outside the actor's region is refused"):
    val t = use(staged(), SiegeEngines, source).toOption.get
    assert(answer(t, actor, SiegeEngines.decisionId, choose(faraway(staged())))
      .isLeft)

  test("it is unusable without a favor to place"):
    val ready = staged(favor = 0)
    assert(!usableNow(ready).exists(_.power.id == SiegeEngines.id))
    assert(use(ready, SiegeEngines, source).isLeft)
