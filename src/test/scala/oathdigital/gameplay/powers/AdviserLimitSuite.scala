package oathdigital.gameplay.powers

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class AdviserLimitSuite extends munit.FunSuite {
  import PowerFixture._
  import TargetsFixture._

  private val tongue = DenizenId("92")

  test("the limit is three by default") {
    assertEquals(AdviserLimit.of(catalog, base, actor), AdviserLimit.Default)
    assertEquals(AdviserLimit.Default, 3)
  }

  test("a faceup Silver Tongue lowers its holder's limit to two, and no one " +
      "else's") {
    val ready = giveAdviser(base, actor, tongue, Orientation.FaceUp)
    assertEquals(AdviserLimit.of(catalog, ready, actor), 2)
    others(base).foreach(other =>
      assertEquals(AdviserLimit.of(catalog, ready, other), 3))
  }

  test("a facedown Silver Tongue changes nothing") {
    val ready = giveAdviser(base, actor, tongue, Orientation.FaceDown)
    assertEquals(AdviserLimit.of(catalog, ready, actor), 3)
  }
}
