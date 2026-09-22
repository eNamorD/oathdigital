package oathdigital.application

import oathdigital.gameplay.setup.{FirstGameSetupFixture, FirstGameSetupRules}
import oathdigital.model._

class GeneratedFirstGamePlanFactorySuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val config = FirstGameBootstrapConfig(FirstGameSetupFixture.participants,
    PlayerId("p2"))
  private val factory = new GeneratedFirstGamePlanFactory(catalog)

  test("a generated plan feeds the unchanged setup machine") {
    val plan = factory.build(config).toOption.get
    assertEquals(plan.orderedSites.size, 8)
    assertEquals(plan.denizenOrder.size, 60)
    assert(new FirstGameSetupRules(catalog)
      .handle(OathState.NoGame, FirstGameSetupCommand.Begin(plan)).isRight)
  }

  test("successive plans are randomized, not the fixed dev order") {
    val orders = Vector.fill(5)(factory.build(config).toOption.get.orderedSites)
    assert(orders.distinct.size > 1, "site order should vary across generated plans")
  }
}
