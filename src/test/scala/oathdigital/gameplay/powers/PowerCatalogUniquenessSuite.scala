package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.PowerId

/** Every production power is registered once per catalog, so a card's power
  * cannot fire twice. The per-power suites prove presence by running each
  * power through the default catalogs; this suite is the one place that
  * catches a doubled entry.
  */
class PowerCatalogUniquenessSuite extends munit.FunSuite:
  private def repeated(ids: Vector[PowerId]): Vector[PowerId] =
    ids.groupBy(identity).collect {
      case (id, copies) if copies.size > 1 => id
    }.toVector

  test("no power id is registered twice in the walker catalog"):
    assertEquals(repeated(WalkerPowerCatalog.default(catalog).powers.map(_.id)),
      Vector.empty)

  test("no power id is registered twice in the phase catalog"):
    assertEquals(repeated(PhasePowerCatalog.default(catalog).powers.map(_.id)),
      Vector.empty)
