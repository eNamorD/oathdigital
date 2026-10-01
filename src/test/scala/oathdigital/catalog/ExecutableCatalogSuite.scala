package oathdigital.catalog

import oathdigital.gameplay.cards.NewFoundations
import oathdigital.model.{DenizenId, EdificeId, LegacyId, PowerId, RelicId,
  SiteId, Suit}
import oathdigital.testkit.TestCards

class ExecutableCatalogSuite extends munit.FunSuite:
  private val catalog = NewFoundations.catalog

  test("each card kind is found by its typed ID"):
    assertEquals(catalog.denizen(DenizenId("9")).map(_.name), Some("Alchemist"))
    assertEquals(catalog.relic(RelicId("R01")).map(_.name), Some("Sticky Fire"))
    assertEquals(catalog.edifice(EdificeId("E01")).map(_.suit),
      Some(Suit.Discord))
    assertEquals(catalog.legacy(LegacyId("L01")).map(_.name), Some("Iron Hand"))
    assertEquals(catalog.site(SiteId("site:deep-woods")).map(_.name),
      Some("Deep Woods"))

  test("an ID the catalog does not hold finds nothing"):
    assertEquals(catalog.denizen(DenizenId("999")), None)
    assertEquals(catalog.relic(RelicId("R99")), None)
    assertEquals(catalog.edifice(EdificeId("E99")), None)
    assertEquals(catalog.legacy(LegacyId("L99")), None)
    assertEquals(catalog.site(SiteId("site:nowhere")), None)

  test("the card printing a power is found from the power's ID"):
    assertEquals(
      catalog.denizenWithPower(PowerId("denizen.alchemist")).map(_.id.value),
      Some("9"))
    assertEquals(
      catalog.relicWithPower(PowerId("relic.sticky-fire")).map(_.id.value),
      Some("R01"))
    assertEquals(
      catalog.site(SiteId("site:deep-woods")).map(_.id),
      catalog.siteWithHandler(PowerId("site.deep-woods.homeland-beast"))
        .map(_.id))
    assertEquals(catalog.denizenWithPower(PowerId("relic.sticky-fire")), None)

  test("an edifice is found from a power on either face"):
    assertEquals(
      catalog.edificeWithPower(PowerId("edifice.e01.intact")).map(_.id.value),
      Some("E01"))
    assertEquals(
      catalog.edificeWithPower(PowerId("edifice.e01.ruined")).map(_.id.value),
      Some("E01"))

  test("a printed power is found on any rendered card, never on a site"):
    assertEquals(
      catalog.printedPower(PowerId("legacy.iron-hand")).map(_.id.value),
      Some("legacy.iron-hand"))
    assertEquals(
      catalog.printedPower(PowerId("edifice.e01.ruined")).map(_.id.value),
      Some("edifice.e01.ruined"))
    assertEquals(
      catalog.printedPower(PowerId("site.deep-woods.homeland-beast")), None)

  test("suitOf reads denizens and edifices only"):
    assertEquals(catalog.suitOf(DenizenId("9")), Some(Suit.Arcane))
    assertEquals(catalog.suitOf(EdificeId("E01")), Some(Suit.Discord))
    assertEquals(catalog.suitOf(RelicId("R01")), None)

  test("a copied catalog indexes its own components"):
    val alchemist = catalog.denizen(DenizenId("9")).get
    val renamed = catalog.copy(denizens = catalog.denizens.map(d =>
      if d.id == alchemist.id then TestCards.denizenLike(d)(name = "Renamed")
      else d))
    assertEquals(renamed.denizen(DenizenId("9")).map(_.name), Some("Renamed"))
    assertEquals(catalog.denizen(DenizenId("9")).map(_.name), Some("Alchemist"))

  test("the first of two components sharing an ID wins, as a scan would"):
    val alchemist = catalog.denizen(DenizenId("9")).get
    val shadowed = catalog.copy(denizens =
      alchemist +: (TestCards.denizenLike(alchemist)(name = "Shadow") +: catalog.denizens))
    assertEquals(shadowed.denizen(DenizenId("9")).map(_.name), Some("Alchemist"))
