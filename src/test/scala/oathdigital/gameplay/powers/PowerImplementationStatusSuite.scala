package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.PowerId

/** The single predicate `ReviewedPowerCatalog`'s registry and the
  * presentation layer's unimplemented-card marker both read. Dazzle,
  * Revelation, Catacombs and Silver Tongue are real production power ids
  * covering one case each, kept in sync with
  * `GamePresentationProjectorImplementedSuite`'s card-level assertions.
  */
class PowerImplementationStatusSuite extends munit.FunSuite {
  private val implemented = PowerImplementationStatus.implemented(catalog)

  test("a power marked implemented in ReviewedPowerCatalog is implemented") {
    assert(implemented(PowerId("denizen.dazzle")))
  }

  test("a power declared but not yet built anywhere is not implemented") {
    assert(!implemented(PowerId("denizen.revelation")))
  }

  test("a power that runs only through the walker catalog is implemented") {
    assert(implemented(PowerId("denizen.catacombs")))
  }

  test("a power declared unimplemented in ReviewedPowerCatalog but covered by " +
      "the walker or phase catalog is implemented") {
    assert(implemented(PowerId("denizen.silver-tongue")))
  }

  test("an id nothing declares at all is not implemented") {
    assert(!implemented(PowerId("denizen.not-a-real-power")))
  }
}
