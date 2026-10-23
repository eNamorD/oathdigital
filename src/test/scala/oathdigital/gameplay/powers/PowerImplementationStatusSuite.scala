package oathdigital.gameplay.powers

import oathdigital.gameplay.operations.{AncientBloodline, GrandScepter,
  HallOfMinisters, OperationRestrictions}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.PowerId

/** The single predicate the presentation layer's unimplemented-card marker
  * reads. Each case reads the registries the predicate reads, so a card
  * that ships moves from one case to another instead of breaking a test
  * that named it; the card-level assertions live in
  * `GamePresentationProjectorImplementedSuite`, and the exact unimplemented
  * set in `UnimplementedPowersSuite`.
  */
class PowerImplementationStatusSuite extends munit.FunSuite:
  private val implemented = PowerImplementationStatus.implemented(catalog)

  test("a power the walker or phase catalog covers is implemented"):
    val covered = WalkerPowerCatalog.default(catalog).powers.map(_.id) ++
      PhasePowerCatalog.default(catalog).powers.map(_.id)
    covered.foreach(id => assert(implemented(id), id.value))

  test("the printed rules are Ancient Bloodline, the Hall of Ministers and " +
      "the Grand Scepter's restriction"):
    assertEquals(OperationRestrictions.printedRules.map(_.id),
      Vector(AncientBloodline.id, HallOfMinisters.id, GrandScepter.id))

  test("a power a printed operation restriction enforces, with no walker " +
      "power, is implemented"):
    OperationRestrictions.printedRules.foreach(rule =>
      assert(implemented(rule.id), rule.id.value))

  test("an id nothing declares at all is not implemented"):
    assert(!implemented(PowerId("denizen.not-a-real-power")))
