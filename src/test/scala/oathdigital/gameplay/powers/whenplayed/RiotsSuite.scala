package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class RiotsSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Riots]
  private val riots = power.cardId
  private val hearth = SearchFixture.denizensOf(Suit.Hearth)
  private val beast = SearchFixture.denizensOf(Suit.Beast)
  private val nomad = SearchFixture.denizensOf(Suit.Nomad)
  private val orderEdifices = edificesOf(Suit.Order)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def discarded(cards: Vector[CardId]): NoteText.Said =
    NoteText.Said("discarded",
      s"Discarded ${cards.map(_.value).mkString(", ")}.", covers = true)

  private def burned(n: Int): NoteText.Said = NoteText.Said("burned",
    s"Burned $n favor from the People's Favor.", covers = false)

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** Riots as p1's adviser, with four discardable cards in p1's region, an
    * intact edifice there and a denizen in another region. */
  private def staged: Table = Table.start.adviser(p1, riots)
    .denizen(hearth(0), at = homeSite).denizen(beast(0), at = homeSite)
    .denizen(nomad(0), at = nearSite)
    .edifice(orderEdifices(0), EdificeSide.Ruined, at = nearSite)
    .edifice(orderEdifices(1), EdificeSide.Intact, at = nearSite)
    .denizen(hearth(1), at = awaySite)

  private val targets: Vector[CardId] = inMapOrder(
    homeSite -> Vector(hearth(0), beast(0)),
    nearSite -> Vector(nomad(0), orderEdifices(0)))

  test("it discards every denizen and ruined edifice in the region, then " +
      "burns as many favor from a held People's Favor"):
    val ready = staged.peoplesFavor(Some(p2), 5).ready
    val done = finished(play(ready, power, riots))
    val after = Look(done.treeless)
    assertEquals(after.denizens(homeSite), Vector.empty)
    assertEquals(after.denizens(nearSite), Vector[CardId](orderEdifices(1)))
    assertEquals(after.denizens(awaySite), Vector[CardId](hearth(1)))
    assertEquals(onBanner(done.treeless), 1)
    assertEquals(done.treeless.game.current.banners.peoplesFavor.holder,
      Some(p2))
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(discarded(targets), burned(4)))

  test("played to a site it discards and counts itself"):
    val ready = Table.start.denizen(riots, at = homeSite)
      .denizen(hearth(0), at = homeSite).peoplesFavor(None, 5).ready
    val done = finished(fire(ready, power, hookAt(riots, homeSite)))
    assertEquals(Look(done.treeless).denizens(homeSite), Vector.empty)
    assertEquals(onBanner(done.treeless), 3)
    assertEquals(said(done.events),
      Vector(discarded(Vector(riots, hearth(0))), burned(2)))

  test("the burn takes what the People's Favor holds"):
    val done = finished(play(staged.peoplesFavor(None, 1).ready, power, riots))
    assertEquals(onBanner(done.treeless), 0)
    assertEquals(said(done.events), Vector(discarded(targets), burned(1)))

  test("an empty People's Favor burns nothing, and the line says so"):
    val done = finished(play(staged.peoplesFavor(None, 0).ready, power, riots))
    assertEquals(said(done.events), Vector(discarded(targets),
      NoteText.Said("unburned", "The People's Favor had no favor to burn.",
        covers = false)))

  test("with nothing to discard it burns nothing"):
    val ready = Table.start.adviser(p1, riots)
      .denizen(hearth(1), at = awaySite).peoplesFavor(None, 5).ready
    val done = finished(play(ready, power, riots))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(onBanner(done.treeless), 5)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "Nothing was discarded.", covers = true)))
