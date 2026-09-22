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

  test("seating order and first player are shuffled, keeping every participant") {
    val plans = Vector.fill(30)(factory.build(config).toOption.get)
    plans.foreach { plan =>
      assertEquals(plan.participants.toSet, config.participants.toSet)
      assertEquals(plan.firstPlayer, plan.participants.head.playerId)
    }
    assert(plans.map(_.firstPlayer).distinct.size > 1,
      "the first player should vary across generated plans")
    assert(plans.map(_.participants).distinct.size > 1,
      "the seating order should vary across generated plans")
  }

  test("successive plans are randomized, not the fixed dev order") {
    val orders = Vector.fill(5)(factory.build(config).toOption.get.orderedSites)
    assert(orders.distinct.size > 1, "site order should vary across generated plans")
  }
}
