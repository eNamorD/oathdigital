package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.p1

class ThreateningRoarSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[ThreateningRoar]
  private val roar = power.cardId
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val faeMerchant = CatalogNames.denizen("Fae Merchant")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val homesteaders = CatalogNames.denizen("Homesteaders")

  private def discarded(cards: Vector[CardId]): NoteText.Said =
    NoteText.Said("discarded",
      s"Discarded ${cards.map(_.value).mkString(", ")}.", covers = true)

  test("as an adviser it discards the Beast and Nomad cards at sites in " +
      "the pawn's region"):
    // Beast and Nomad cards in p1's region go, the ruined Beast edifice with
    // them. Homesteaders (Hearth), the intact Oaken Fortress and Fae Merchant
    // in another region stay.
    val ruined = CatalogNames.edifice("Ruined School")
    val intact = CatalogNames.edifice("Oaken Fortress")
    val ready = Table.start.adviser(p1, roar)
      .denizen(errandBoy, at = homeSite).denizen(homesteaders, at = homeSite)
      .denizen(wildMounts, at = nearSite)
      .edifice("Ruined School", EdificeSide.Ruined, at = nearSite)
      .edifice("Oaken Fortress", EdificeSide.Intact, at = nearSite)
      .denizen(faeMerchant, at = awaySite).ready
    val done = Outcomes.finished(play(ready, power, roar))
    val after = Look(done.treeless)
    assertEquals(after.cardsAt(homeSite), Vector[CardId](homesteaders))
    assertEquals(after.cardsAt(nearSite), Vector[CardId](intact))
    assertEquals(after.cardsAt(awaySite), Vector[CardId](faeMerchant))
    assertJournalRebuilds(ready, done.events, done.treeless)
    assertEquals(said(power, done.events), Vector(discarded(inMapOrder(
      homeSite -> Vector(errandBoy), nearSite -> Vector(wildMounts, ruined)))))

  test("played to a site it discards itself with the rest"):
    val ready = Table.start.denizen(roar, at = homeSite)
      .denizen(wildMounts, at = homeSite).ready
    val done = Outcomes.finished(fire(ready, power, hookAt(roar, homeSite)))
    assertEquals(Look(done.treeless).cardsAt(homeSite), Vector.empty)
    assertEquals(said(power, done.events),
      Vector(discarded(Vector(roar, wildMounts))))

  test("with nothing to discard it says so"):
    val ready = Table.start.adviser(p1, roar)
      .denizen(homesteaders, at = homeSite).ready
    val done = Outcomes.finished(play(ready, power, roar))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      "Nothing was discarded.", covers = true)))
