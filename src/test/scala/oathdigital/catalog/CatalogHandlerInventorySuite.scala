package oathdigital.catalog

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog

/** The fingerprint is a deliberate tripwire: a catalog edit that changes the
  * handler vocabulary must be audited, like the corpus counts in
  * `CatalogLoaderSuite`.
  */
class CatalogHandlerInventorySuite extends munit.FunSuite:
  test("central handler inventory equals the audited catalog vocabulary"):
    val expected = (catalog.denizens.flatMap(_.handlers) ++
      catalog.relics.flatMap(_.handlers) ++ catalog.legacies.flatMap(_.handlers) ++
      catalog.sites.flatMap(_.handlers) ++ catalog.edifices.flatMap(e =>
        e.intact.handlers ++ e.ruined.handlers)).distinct.sorted
    assertEquals(CatalogHandlerInventory.handlerIds(catalog), expected)
    assertEquals(CatalogHandlerInventory.fingerprint(catalog),
      "7e333f6b4bdd033e2c1e76c3b4f8889c7d44cb5325f8d7da32ba514291b154e2")
