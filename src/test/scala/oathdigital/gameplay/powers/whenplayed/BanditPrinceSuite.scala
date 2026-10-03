package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.as

class BanditPrinceSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[BanditPrince.type]
  private val prince = power.cardId
  private val played = hook(prince)
  /** Bandit Prince is adviser-only: p1 holds it. */
  private val start = Table.start.adviser(p1, prince)
  /** Bandits hold p1's site (2) and a site in another region (1). */
  private val bandited = start.bandits(homeSite, 2).bandits(awaySite, 1)
  private val mine = Look(start.ready).forceKind(p1)
  /** The two bandit sites, in map order. */
  private val sites: Vector[SiteId] =
    start.ready.game.current.map.inPlay.filter(Set(homeSite, awaySite))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def choose(chosen: Vector[SiteId]): DecisionAnswer =
    DecisionAnswer.ChooseManyAnswer(chosen.map(DecisionOptionRef.Site(_)))

  private def replacedAt(site: SiteId): NoteText.Said =
    val n = if site == homeSite then 2 else 1
    NoteText.Said("replaced", s"Replaced $n " +
      s"${if n == 1 then "bandit" else "bandits"} at ${site.value} with " +
      s"${p1.value}'s warbands.", covers = true)

  test("it offers every bandit site, and replaces the bandits at the sites " +
      "chosen"):
    val ready = bandited.ready
    val first = Outcomes.parked(play(ready, power, prince))
    val decide = question(ready, power, played, first.tree)
    assertEquals(decide.decisionId, BanditPrince.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseMany(0, 2, sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))), decide.query.heading))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      BanditPrince.decisionId, choose(sites)))
    val after = Look(done.treeless)
    assertEquals(after.forces(homeSite), SiteForces.Occupied(mine, 2))
    assertEquals(after.forces(awaySite), SiteForces.Occupied(mine, 1))
    // The replacing warbands come from the bank, so p1's board keeps its 3.
    assertEquals(after.boardWarbands(p1), 3)
    assertEquals(replayed(ready, first.events ++ done.events), done.treeless)
    assertEquals(said(done.events), sites.map(replacedAt))

  test("declining replaces nothing and writes nothing"):
    val ready = bandited.ready
    val first = Outcomes.parked(play(ready, power, prince))
    val done = Outcomes.finished(resume(ready, power, played, first.tree,
      BanditPrince.decisionId, choose(Vector.empty)))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector.empty)

  test("with no bandit site nothing is asked, and the line says so"):
    val done = Outcomes.finished(play(start.ready, power, prince))
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No site was ruled by bandits.", covers = false)))

  test("sites holding more bandits than the bank holds warbands are refused"):
    val ready = bandited.warbandsInBank(p1, 2).ready
    val first = Outcomes.parked(play(ready, power, prince))
    val query = question(ready, power, played, first.tree).query
    val offered = as[DecisionQuery.ChooseMany](query).options
    assertEquals(offered.size, 2)
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(sites)).isLeft)
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(Vector(homeSite))).isRight)

  test("only the sites that fit the bank are offered, and the question is " +
      "still asked"):
    // With 1 left, the 2 bandits at p1's site no longer fit; the 1 away does.
    val tight = bandited.warbandsInBank(p1, 1).ready
    val asked = Outcomes.parked(play(tight, power, prince))
    val fitting = as[DecisionQuery.ChooseMany](
      question(tight, power, played, asked.tree).query).options
    assertEquals(fitting.map(_.ref), Vector[DecisionOptionRef](
      DecisionOptionRef.Site(awaySite)))

  test("a site the bandits do not rule is refused"):
    val ready = bandited.ready
    val first = Outcomes.parked(play(ready, power, prince))
    assert(resume(ready, power, played, first.tree, BanditPrince.decisionId,
      choose(Vector(nearSite))).isLeft)

  test("with an empty bank no site can be replaced, so nothing is asked"):
    val ready = bandited.warbandsInBank(p1, 0).ready
    val done = Outcomes.finished(play(ready, power, prince))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector.empty)
