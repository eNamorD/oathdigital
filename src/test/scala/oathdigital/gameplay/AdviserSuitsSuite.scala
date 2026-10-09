package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.AdviserSuits
import oathdigital.gameplay.powers.RuledCards
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** `AdviserSuits`: a player's advisers counted by suit, a faceup Marriage
  * counting as two (catalog batch 5 rulings, "Counts as applies
  * everywhere"). */
class AdviserSuitsSuite extends munit.FunSuite:
  test("a faceup Marriage counts as two Hearth advisers, and facedown as " +
      "none"):
    val faceup = Table.start.adviser(p1, "Marriage")
      .adviser(p1, "Homesteaders").ready
    assertEquals(AdviserSuits.count(catalog, faceup, p1, Suit.Hearth), 3)
    assertEquals(AdviserSuits.count(catalog, faceup, p1, Suit.Beast), 0)
    val facedown = Table.start.adviser(p1, "Marriage", facedown = true)
      .adviser(p1, "Homesteaders").ready
    assertEquals(AdviserSuits.count(catalog, facedown, p1, Suit.Hearth), 1)

  test("a ruler's count weighs Marriage, while its list names it once"):
    val ready = Table.start.adviser(p1, "Marriage").ready
    val ruler = SiteRuler.Player(p1)
    val listed = RuledCards.of(catalog, ready, ruler, Suit.Hearth)
    assertEquals(listed.count(_ == CatalogNames.denizen("Marriage")), 1)
    assertEquals(RuledCards.count(catalog, ready, ruler, Suit.Hearth),
      listed.size + 1)
