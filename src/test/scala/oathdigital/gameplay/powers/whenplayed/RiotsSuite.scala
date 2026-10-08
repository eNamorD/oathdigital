package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.{p1, p2}

class RiotsSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Riots]
  private val riots = power.cardId
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val hallOfBandits = CatalogNames.edifice("Hall of Bandits")
  private val relicPatrols = CatalogNames.edifice("Relic Patrols")

  private def discarded(cards: Vector[CardId]): NoteText.Said =
    NoteText.Said("discarded",
      s"Discarded ${cards.map(_.value).mkString(", ")}.", covers = true)

  private def burned(n: Int): NoteText.Said = NoteText.Said("burned",
    s"Burned $n favor from the People's Favor.", covers = false)

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** Riots as p1's adviser, with four discardable cards in p1's region
    * (Homesteaders, Errand Boy, Wild Mounts and the ruined Hall of Bandits),
    * the intact Relic Patrols there and Land Warden in another region. */
  private def staged: Table = Table.start.adviser(p1, riots)
    .denizen(homesteaders, at = homeSite).denizen(errandBoy, at = homeSite)
    .denizen(wildMounts, at = nearSite)
    .edifice("Hall of Bandits", EdificeSide.Ruined, at = nearSite)
    .edifice("Relic Patrols", EdificeSide.Intact, at = nearSite)
    .denizen(landWarden, at = awaySite)

  private val targets: Vector[CardId] = inMapOrder(
    homeSite -> Vector(homesteaders, errandBoy),
    nearSite -> Vector(wildMounts, hallOfBandits))

  test("it discards every denizen and ruined edifice in the region, then " +
      "burns as many favor from a held People's Favor"):
    val ready = staged.peoplesFavor(Some(p2), 5).ready
    val done = Outcomes.finished(play(ready, power, riots))
    val after = Look(done.treeless)
    assertEquals(after.cardsAt(homeSite), Vector.empty)
    assertEquals(after.cardsAt(nearSite), Vector[CardId](relicPatrols))
    assertEquals(after.cardsAt(awaySite), Vector[CardId](landWarden))
    // 5 on the banner, less 1 per card discarded.
    assertEquals(onBanner(done.treeless), 5 - 4)
    assertEquals(done.treeless.game.current.banners.peoplesFavor.holder,
      Some(p2))
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(power, done.events),
      Vector(discarded(targets), burned(4)))

  test("played to a site it discards and counts itself"):
    val ready = Table.start.denizen(riots, at = homeSite)
      .denizen(homesteaders, at = homeSite).peoplesFavor(None, 5).ready
    val done = Outcomes.finished(fire(ready, power, hookAt(riots, homeSite)))
    assertEquals(Look(done.treeless).cardsAt(homeSite), Vector.empty)
    // Riots and Homesteaders are discarded: 5 - 2.
    assertEquals(onBanner(done.treeless), 5 - 2)
    assertEquals(said(power, done.events),
      Vector(discarded(Vector(riots, homesteaders)), burned(2)))

  test("the burn takes what the People's Favor holds"):
    val done = Outcomes.finished(
      play(staged.peoplesFavor(None, 1).ready, power, riots))
    assertEquals(onBanner(done.treeless), 0)
    assertEquals(said(power, done.events),
      Vector(discarded(targets), burned(1)))

  test("an empty People's Favor burns nothing, and the line says so"):
    val done = Outcomes.finished(
      play(staged.peoplesFavor(None, 0).ready, power, riots))
    assertEquals(said(power, done.events), Vector(discarded(targets),
      NoteText.Said("unburned", "The People's Favor had no favor to burn.",
        covers = false)))

  test("with nothing to discard it burns nothing"):
    val ready = Table.start.adviser(p1, riots)
      .denizen(landWarden, at = awaySite).peoplesFavor(None, 5).ready
    val done = Outcomes.finished(play(ready, power, riots))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(onBanner(done.treeless), 5)
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      "Nothing was discarded.", covers = true)))
