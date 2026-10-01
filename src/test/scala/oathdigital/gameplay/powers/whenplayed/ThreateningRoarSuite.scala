package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class ThreateningRoarSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[ThreateningRoar]
  private val roar = power.cardId
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
  private val beastEdifices = edificesOf(Suit.Beast)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def discarded(cards: Vector[CardId]): NoteText.Said =
    NoteText.Said("discarded",
      s"Discarded ${cards.map(_.value).mkString(", ")}.", covers = true)

  test("as an adviser it discards the Beast and Nomad cards at sites in " +
      "the pawn's region"):
    val ruined = beastEdifices(0)
    val intact = beastEdifices(1)
    val ready = Table.start.adviser(p1, roar)
      .denizen(beast(0), at = homeSite).denizen(hearth(0), at = homeSite)
      .denizen(nomad(0), at = nearSite)
      .edifice(ruined, EdificeSide.Ruined, at = nearSite)
      .edifice(intact, EdificeSide.Intact, at = nearSite)
      .denizen(beast(1), at = awaySite).ready
    val done = finished(play(ready, power, roar))
    val after = Look(done.treeless)
    assertEquals(after.denizens(homeSite), Vector[CardId](hearth(0)))
    assertEquals(after.denizens(nearSite), Vector[CardId](intact))
    assertEquals(after.denizens(awaySite), Vector[CardId](beast(1)))
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(discarded(inMapOrder(
      homeSite -> Vector(beast(0)), nearSite -> Vector(nomad(0), ruined)))))

  test("played to a site it discards itself with the rest"):
    val ready = Table.start.denizen(roar, at = homeSite)
      .denizen(nomad(0), at = homeSite).ready
    val done = finished(fire(ready, power, hookAt(roar, homeSite)))
    assertEquals(Look(done.treeless).denizens(homeSite), Vector.empty)
    assertEquals(said(done.events), Vector(discarded(Vector(roar, nomad(0)))))

  test("with nothing to discard it says so"):
    val ready = Table.start.adviser(p1, roar)
      .denizen(hearth(0), at = homeSite).ready
    val done = finished(play(ready, power, roar))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "Nothing was discarded.", covers = true)))
