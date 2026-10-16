package oathdigital.gameplay.powers

import oathdigital.gameplay.operations.HallOfMinisters
import oathdigital.gameplay.powerresolver.Power
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.PowerId

/** The single predicate `ReviewedPowerCatalog`'s registry and the
  * presentation layer's unimplemented-card marker both read. Each case reads
  * the registries the predicate reads, so a card that ships moves from one
  * case to another instead of breaking a test that named it; the
  * card-level assertions live in `GamePresentationProjectorImplementedSuite`.
  */
class PowerImplementationStatusSuite extends munit.FunSuite:
  private val implemented = PowerImplementationStatus.implemented(catalog)
  private val reviewed = ReviewedPowerCatalog.powers
  private val covered: Set[PowerId] =
    (WalkerPowerCatalog.default(catalog).powers.map(_.id) ++
      PhasePowerCatalog.default(catalog).powers.map(_.id)).toSet
  private def declaresUnimplemented(power: Power): Boolean =
    power.handlers.exists(!_.implemented)

  test("a power whose every handler ReviewedPowerCatalog marks implemented " +
      "is implemented"):
    reviewed.filterNot(declaresUnimplemented).foreach(power =>
      assert(implemented(power.id), power.id.value))

  test("a power declared unimplemented in ReviewedPowerCatalog and covered " +
      "by no walker or phase power is not implemented"):
    reviewed.filter(power => declaresUnimplemented(power) &&
      !covered(power.id)).foreach(power =>
      assert(!implemented(power.id), power.id.value))

  test("a power the walker or phase catalog covers is implemented, even " +
      "when ReviewedPowerCatalog still declares it unimplemented"):
    covered.foreach(id => assert(implemented(id), id.value))

  test("the Hall of Ministers' intact power, which an operation " +
      "restriction implements with no walker power, is implemented"):
    assert(implemented(HallOfMinisters.id))

  test("an id nothing declares at all is not implemented"):
    assert(!implemented(PowerId("denizen.not-a-real-power")))
