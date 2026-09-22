package oathdigital.application

import oathdigital.gameplay.setup.{FirstGameSetupFixture, GameStartRules}
import oathdigital.model._

class GeneratedFirstGamePlanFactorySuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val config = FirstGameBootstrapConfig(FirstGameSetupFixture.participants,
    PlayerId("p2"))
  private val factory = new GeneratedFirstGamePlanFactory(catalog)

  test("a generated Chronicle feeds GameStartRules") {
    val plan = factory.build(config).toOption.get
    // The generated Chronicle stores every catalog site (between-game
    // storage for sites not currently in play); only the first 8 are the
    // in-play atlas GameStartRules deals from.
    assert(plan.chronicle.atlasBox.size >= 8)
    assertEquals(plan.chronicle.worldDeck.size, 60)
    val orders = ChronicleFirstGamePlan.dealOrder(plan.chronicle, plan.resolvedConfig)
    assert(GameStartRules.evolve(catalog, plan.chronicle, orders).isRight)
  }

  test("seating order and first player are shuffled, keeping every participant") {
    val plans = Vector.fill(30)(factory.build(config).toOption.get.resolvedConfig)
    plans.foreach { resolved =>
      assertEquals(resolved.participants.toSet, config.participants.toSet)
      assertEquals(resolved.firstPlayer, resolved.participants.head.playerId)
    }
    assert(plans.map(_.firstPlayer).distinct.size > 1,
      "the first player should vary across generated plans")
    assert(plans.map(_.participants).distinct.size > 1,
      "the seating order should vary across generated plans")
  }

  test("successive plans are randomized, not the fixed dev order") {
    val atlasBoxes = Vector.fill(5)(factory.build(config).toOption.get.chronicle.atlasBox)
    assert(atlasBoxes.distinct.size > 1, "site order should vary across generated plans")
  }
}
