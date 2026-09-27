package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.PowerId

/** The single predicate `ReviewedPowerCatalog`'s registry and the
  * presentation layer's unimplemented-card marker both read. Dazzle,
  * Revelation, Catacombs and Silver Tongue are real production power ids
  * covering one case each, kept in sync with
  * `GamePresentationProjectorImplementedSuite`'s card-level assertions.
  */
class PowerImplementationStatusSuite extends munit.FunSuite:
  private val implemented = PowerImplementationStatus.implemented(catalog)

  test("a power marked implemented in ReviewedPowerCatalog is implemented"):
    assert(implemented(PowerId("denizen.dazzle")))

  test("a power declared but not yet built anywhere is not implemented"):
    assert(!implemented(PowerId("denizen.revelation")))

  test("a power that runs only through the walker catalog is implemented"):
    assert(implemented(PowerId("denizen.catacombs")))

  test("a power declared unimplemented in ReviewedPowerCatalog but covered by " +
      "the walker or phase catalog is implemented"):
    assert(implemented(PowerId("denizen.silver-tongue")))

  test("an id nothing declares at all is not implemented"):
    assert(!implemented(PowerId("denizen.not-a-real-power")))

  test("the Vision-play cards are implemented"):
    Vector("denizen.vow-of-obedience", "denizen.secret-police",
      "denizen.book-binders", "edifice.e08.intact").foreach(id =>
      assert(implemented(PowerId(id)), id))

  test("catalog batch 2's modifiers and restrictions are implemented"):
    Vector("denizen.animal-playmates", "denizen.birdsong",
      "denizen.royal-stables", "denizen.forgotten-vault").foreach(id =>
      assert(implemented(PowerId(id)), id))

  test("catalog batch 2's battle plans are implemented"):
    Vector("denizen.fire-talkers", "denizen.nature-worship",
      "denizen.cracked-sage", "denizen.horse-archers", "denizen.storm-caller",
      "denizen.longbows", "relic.black-sword", "relic.bag-of-siegeworks",
      "denizen.hospital").foreach(id => assert(implemented(PowerId(id)), id))
