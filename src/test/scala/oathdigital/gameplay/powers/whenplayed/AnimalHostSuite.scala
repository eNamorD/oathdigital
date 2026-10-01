package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, PlayerFacts, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class AnimalHostSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[AnimalHost]
  private val host = power.cardId
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
  private val beastEdifices = edificesOf(Suit.Beast)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def gained(n: Int): NoteText.Said = NoteText.Said("gained",
    s"${p1.value} gained $n ${if n == 1 then "warband" else "warbands"}.",
    covers = false)

  /** `table` with p1's warband bank holding only `left`. */
  private def banking(table: Table, left: Int): Table =
    val ready = table.ready
    val kind = PlayerFacts.forceKind(ready, p1).toOption.get
    table.warbands(p1, ready.banks.warbandSupply(kind) - left)

  test("as an adviser it counts the Beast cards at every site, whoever " +
      "rules them"):
    // Four count: a denizen at p1's site, a ruined edifice at a bandit site,
    // and an intact edifice and a denizen at p2's site. A Hearth card and
    // p2's Beast adviser do not.
    val ready = Table.start.adviser(p1, host)
      .denizen(beast(0), at = homeSite).denizen(hearth(0), at = homeSite)
      .bandits(nearSite, 2)
      .edifice(beastEdifices(0), EdificeSide.Ruined, at = nearSite)
      .edifice(beastEdifices(1), EdificeSide.Intact, at = awaySite)
      .denizen(beast(1), at = awaySite).warbandsAt(awaySite, p2, 1)
      .adviser(p2, beast(2)).ready
    val done = finished(play(ready, power, host))
    assertEquals(Look(done.treeless).warbands(p1), 3 + 4)
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(gained(4)))

  test("played to a site it counts itself"):
    val ready = Table.start.denizen(host, at = homeSite)
      .denizen(beast(0), at = nearSite).ready
    val done = finished(fire(ready, power, hookAt(host, homeSite)))
    assertEquals(Look(done.treeless).warbands(p1), 3 + 2)
    assertEquals(said(done.events), Vector(gained(2)))

  test("the gain takes what the bank holds"):
    val ready = banking(Table.start.adviser(p1, host)
      .denizen(beast(0), at = homeSite).denizen(beast(1), at = nearSite),
      left = 1).ready
    val done = finished(play(ready, power, host))
    assertEquals(said(done.events), Vector(gained(1)))

  test("with no Beast card at a site it gains none, and the line says so"):
    val ready = Table.start.adviser(p1, host).adviser(p1, beast(0)).ready
    val done = finished(play(ready, power, host))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      s"${p1.value} gained no warbands.", covers = false)))
