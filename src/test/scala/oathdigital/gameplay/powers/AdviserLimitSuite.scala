package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class AdviserLimitSuite extends munit.FunSuite:
  private val tongue = CatalogNames.denizen("Silver Tongue")

  // The default limit is three advisers; a faceup Silver Tongue lowers its
  // holder's to two.
  test("a faceup Silver Tongue lowers its holder's limit to two, and no one " +
      "else's"):
    val ready = Table.start.adviser(p1, tongue).ready
    assertEquals(AdviserLimit.of(catalog, ready, p1), 2)
    Vector(p2, p3).foreach(other =>
      assertEquals(AdviserLimit.of(catalog, ready, other), 3))

  test("a facedown Silver Tongue changes nothing"):
    val ready = Table.start.adviser(p1, tongue, facedown = true).ready
    assertEquals(AdviserLimit.of(catalog, ready, p1), 3)
