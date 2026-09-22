package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model._

class ChronicleFirstGamePlanSuite extends munit.FunSuite {
  private val config = FirstGameBootstrapConfig(
    FirstGameSetupFixture.participants, PlayerId("p2"))
  private val chronicle = FirstGameSetupFixture.chronicle

  test("dealing the fixture's equivalent Chronicle reproduces its exact deal order") {
    val orders = ChronicleFirstGamePlan.dealOrder(chronicle, config)
    assertEquals(orders, FirstGameSetupFixture.orders)
  }

  test("the dealt orders still satisfy GameStartRules") {
    val orders = ChronicleFirstGamePlan.dealOrder(chronicle, config)
    assert(oathdigital.gameplay.setup.GameStartRules
      .evolve(FirstGameSetupFixture.catalog, chronicle, orders).isRight)
  }

  test("dealOrder is total even when the Chronicle is too small for GameStartRules") {
    val short = chronicle.copy(atlasBox = chronicle.atlasBox.take(7))
    val orders = ChronicleFirstGamePlan.dealOrder(short, config)
    assertEquals(orders.participants, config.participants)
    assert(oathdigital.gameplay.setup.GameStartRules
      .evolve(FirstGameSetupFixture.catalog, short, orders).isLeft)
  }
}
