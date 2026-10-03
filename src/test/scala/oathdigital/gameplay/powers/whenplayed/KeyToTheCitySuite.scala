package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}

class KeyToTheCitySuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[KeyToTheCity.type]
  private val key = power.cardId
  private val played = hookAt(key, homeSite)
  /** Key to the City is site-only: it stands at p1's site. */
  private val start = Table.start.denizen(key, at = homeSite)
  private val mine = Look(start.ready).forceKind(p1)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private val killedTwo = NoteText.Said("killed",
    s"Killed 2 ${p2.value} warbands at ${homeSite.value}.", covers = false)
  private val placed = NoteText.Said("placed",
    s"${p1.value} placed 1 warband at ${homeSite.value}.", covers = true)

  test("an absent ruler's warbands are killed, and the actor gains and " +
      "places one"):
    val ready = start.warbandsAt(homeSite, p2, 2).ready
    val done = Outcomes.finished(fire(ready, power, played))
    val after = Look(done.treeless)
    assertEquals(after.forces(homeSite), SiteForces.Occupied(mine, 1))
    assertEquals(after.boardWarbands(p1), 3)
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(killedTwo, placed))

  test("bandits are killed too"):
    val done = Outcomes.finished(
      fire(start.bandits(homeSite, 3).ready, power, played))
    assertEquals(Look(done.treeless).forces(homeSite),
      SiteForces.Occupied(mine, 1))
    assertEquals(said(done.events), Vector(NoteText.Said("bandits",
      s"Killed 3 bandit warbands at ${homeSite.value}.", covers = false),
      placed))

  test("an empty site only receives the warband"):
    val done = Outcomes.finished(fire(start.ready, power, played))
    assertEquals(Look(done.treeless).forces(homeSite),
      SiteForces.Occupied(mine, 1))
    assertEquals(said(done.events), Vector(placed))

  test("the actor rules the site and stands there, so nothing happens"):
    val ready = start.warbandsAt(homeSite, p1, 2).ready
    val done = Outcomes.finished(fire(ready, power, played))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("guarded",
      s"${p1.value} was at ${homeSite.value}.", covers = false)))

  test("a ruler whose pawn stands at the site keeps it"):
    val ready = start.warbandsAt(homeSite, p2, 2).pawn(p2, homeSite).ready
    val theirs = Look(ready).forceKind(p2)
    val done = Outcomes.finished(fire(ready, power, played))
    assertEquals(Look(done.treeless).forces(homeSite),
      SiteForces.Occupied(theirs, 2))
    assertEquals(said(done.events), Vector(NoteText.Said("guarded",
      s"${p2.value} was at ${homeSite.value}.", covers = false)))

  test("with no warband on the board or in the bank nothing is placed"):
    // Every one of p1's warbands stands at a site in another region.
    val supply = start.ready.banks.warbandSupply(mine)
    val ready = start.warbandsAt(homeSite, p2, 2).boardWarbands(p1, 0)
      .warbandsAt(awaySite, p1, supply).ready
    val done = Outcomes.finished(fire(ready, power, played))
    assertEquals(Look(done.treeless).forces(homeSite), SiteForces.Empty)
    assertEquals(said(done.events), Vector(killedTwo,
      NoteText.Said("unplaced", s"${p1.value} had no warband to place.",
        covers = true)))
