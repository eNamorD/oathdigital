package oathdigital.catalog

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog

class CatalogHandlerInventorySuite extends munit.FunSuite:
  test("the handler inventory lists every printed power and site handler once"):
    val expected = (catalog.denizens.flatMap(_.powers.map(_.id.value)) ++
      catalog.relics.flatMap(_.powers.map(_.id.value)) ++
      catalog.legacies.flatMap(_.powers.map(_.id.value)) ++
      catalog.sites.flatMap(_.handlers.map(_.value)) ++ catalog.edifices.flatMap(e =>
        (e.intact.powers ++ e.ruined.powers).map(_.id.value))).distinct.sorted
    assertEquals(CatalogHandlerInventory.handlerIds(catalog), expected)
