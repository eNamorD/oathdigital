package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PlayerFacts}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class BanditPrinceSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[BanditPrince.type]
  private val prince = power.cardId
  private val played = hook(prince)
  /** Bandit Prince is adviser-only: p1 holds it. */
  private val start = Table.start.adviser(p1, prince)
  /** Bandits hold p1's site (2) and a site in another region (1). */
  private val bandited = start.bandits(homeSite, 2).bandits(awaySite, 1)
  private val mine = PlayerFacts.forceKind(start.ready, p1).toOption.get
  /** The two bandit sites, in map order. */
  private val sites: Vector[SiteId] =
    start.ready.game.current.map.inPlay.filter(Set(homeSite, awaySite))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def choose(chosen: Vector[SiteId]): DecisionAnswer =
    DecisionAnswer.ChooseManyAnswer(chosen.map(DecisionOptionRef.Site(_)))

  /** `table` with p1's warband bank holding only `left`. */
  private def banking(table: Table, left: Int): Table =
    val ready = table.ready
    table.warbands(p1, ready.banks.warbandSupply(mine) - left)

  private def replacedAt(site: SiteId): NoteText.Said =
    val n = if site == homeSite then 2 else 1
    NoteText.Said("replaced", s"Replaced $n " +
      s"${if n == 1 then "bandit" else "bandits"} at ${site.value} with " +
      s"${p1.value}'s warbands.", covers = true)

  test("it offers every bandit site, and replaces the bandits at the sites " +
      "chosen"):
    val ready = bandited.ready
    val first = parked(play(ready, power, prince))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, BanditPrince.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseMany(0, 2, sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))), decide.query.heading))
    val done = finished(resume(ready, power, played, first.tree,
      BanditPrince.decisionId, choose(sites)))
    val after = Look(done.treeless)
    assertEquals(after.forces(homeSite), SiteForces.Occupied(mine, 2))
    assertEquals(after.forces(awaySite), SiteForces.Occupied(mine, 1))
    assertEquals(after.warbands(p1), 3)
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), sites.map(replacedAt))

  test("declining replaces nothing and writes nothing"):
    val ready = bandited.ready
    val first = parked(play(ready, power, prince))
    val done = finished(resume(ready, power, played, first.tree,
      BanditPrince.decisionId, choose(Vector.empty)))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector.empty)

  test("with no bandit site nothing is asked, and the line says so"):
    val done = finished(play(start.ready, power, prince))
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No site was ruled by bandits.", covers = false)))

  test("sites holding more bandits than the bank holds warbands are refused"):
    val ready = banking(bandited, left = 2).ready
    val first = parked(play(ready, power, prince))
    val offered = question(ready, power, played, first.tree).query
      .asInstanceOf[DecisionQuery.ChooseMany].options
    assertEquals(offered.size, 2)
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(sites)).isLeft)
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(Vector(homeSite))).isRight)

  test("a site the bandits do not rule is refused"):
    val ready = bandited.ready
    val first = parked(play(ready, power, prince))
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(Vector(nearSite))).isLeft)

  test("with an empty bank no site can be replaced, so nothing is asked"):
    val ready = banking(bandited, left = 0).ready
    val done = finished(play(ready, power, prince))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector.empty)
