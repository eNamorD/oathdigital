package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}

class AnimalHostSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[AnimalHost]
  private val host = power.cardId

  private def gained(n: Int): NoteText.Said = NoteText.Said("gained",
    s"${p1.value} gained $n ${if n == 1 then "warband" else "warbands"}.",
    covers = false)

  test("as an adviser it counts the Beast cards at every site, whoever " +
      "rules them"):
    // Four Beast cards count: Errand Boy at p1's site, the Ruined School at a
    // bandit site, and the Oaken Fortress and Fae Merchant at p2's site.
    // Homesteaders (Hearth) and p2's Beast adviser Second Chance do not.
    val ready = Table.start.adviser(p1, host)
      .denizen("Errand Boy", at = homeSite)
      .denizen("Homesteaders", at = homeSite)
      .bandits(nearSite, 2)
      .edifice("Ruined School", EdificeSide.Ruined, at = nearSite)
      .edifice("Oaken Fortress", EdificeSide.Intact, at = awaySite)
      .denizen("Fae Merchant", at = awaySite).warbandsAt(awaySite, p2, 1)
      .adviser(p2, "Second Chance").ready
    val done = Outcomes.finished(play(ready, power, host))
    assertEquals(Look(done.treeless).boardWarbands(p1), 3 + 4)
    assertJournalRebuilds(ready, done.events, done.treeless)
    assertEquals(said(power, done.events), Vector(gained(4)))

  test("played to a site it counts itself"):
    // Animal Host itself and Errand Boy.
    val ready = Table.start.denizen(host, at = homeSite)
      .denizen("Errand Boy", at = nearSite).ready
    val done = Outcomes.finished(fire(ready, power, hookAt(host, homeSite)))
    assertEquals(Look(done.treeless).boardWarbands(p1), 3 + 2)
    assertEquals(said(power, done.events), Vector(gained(2)))

  test("the gain takes what the bank holds"):
    // Two Beast cards, but p1's bank holds 1 warband.
    val ready = Table.start.adviser(p1, host)
      .denizen("Errand Boy", at = homeSite)
      .denizen("Fae Merchant", at = nearSite)
      .warbandsInBank(p1, 1).ready
    val done = Outcomes.finished(play(ready, power, host))
    assertEquals(said(power, done.events), Vector(gained(1)))

  test("with no Beast card at a site it gains none, and the line says so"):
    // Errand Boy is p1's adviser, at no site.
    val ready = Table.start.adviser(p1, host).adviser(p1, "Errand Boy").ready
    val done = Outcomes.finished(play(ready, power, host))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      s"${p1.value} gained no warbands.", covers = false)))
