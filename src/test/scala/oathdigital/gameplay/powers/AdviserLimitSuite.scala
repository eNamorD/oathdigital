package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{PhasePowerHarness, Table}

class AdviserLimitSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val tongue = DenizenId("92")

  test("a faceup Silver Tongue lowers its holder's limit to two, and no one " +
      "else's"):
    val ready = Table.from(quietTable).adviser(actor, tongue).unchecked
    assertEquals(AdviserLimit.of(catalog, ready, actor), 2)
    others(quietTable).foreach(other =>
      assertEquals(AdviserLimit.of(catalog, ready, other), 3))

  test("a facedown Silver Tongue changes nothing"):
    val ready = Table.from(quietTable).adviser(actor, tongue, facedown = true)
      .unchecked
    assertEquals(AdviserLimit.of(catalog, ready, actor), 3)
