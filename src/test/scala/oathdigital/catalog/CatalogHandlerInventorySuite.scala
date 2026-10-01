package oathdigital.catalog

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog

class CatalogHandlerInventorySuite extends munit.FunSuite:
  test("the handler inventory lists every printed power and site handler once"):
    val expected = (catalog.denizens.flatMap(_.handlers) ++
      catalog.relics.flatMap(_.handlers) ++ catalog.legacies.flatMap(_.handlers) ++
      catalog.sites.flatMap(_.handlers) ++ catalog.edifices.flatMap(e =>
        e.intact.handlers ++ e.ruined.handlers)).distinct.sorted
    assertEquals(CatalogHandlerInventory.handlerIds(catalog), expected)
