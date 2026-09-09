package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.setup.{FirstGameSetupFixture, FirstGameSetupRules}
import oathdigital.model.PowerId

/** Task 1: the contribution vocabulary and the `ContributingPower` shape.
  * Vocabulary only -- nothing here gathers, chains, or applies a
  * contribution; that is Task 2's collector.
  */
class ContributingPowerSuite extends munit.FunSuite {

  /** A minimal power: only `id`, `source` and `contributions` declared,
    * exercising every default on the trait.
    */
  private def minimalPower(idValue: String, sourceKey: String): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId(idValue)
      def source: RuleSourceRef = RuleSourceRef.GameRule(sourceKey)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    }

  test("sortKey orders equal-priority powers by source.stableKey, then by id.value") {
    // Deliberately interleaved: the power with the largest id ("power.z")
    // carries the smallest stableKey, so a naive id-only sort would put it
    // last while the spec's (priority, stableKey, id) key puts it first.
    val bravoB = minimalPower("power.b", "bravo")
    val bravoA = minimalPower("power.a", "bravo")
    val alphaZ = minimalPower("power.z", "alpha")

    val sorted = Vector(bravoB, alphaZ, bravoA)
      .sortBy(ContributingPower.sortKey)

    assertEquals(sorted, Vector(alphaZ, bravoA, bravoB))
    assertEquals(sorted.map(ContributingPower.sortKey), Vector(
      (0, "game:alpha", "power.z"),
      (0, "game:bravo", "power.a"),
      (0, "game:bravo", "power.b")
    ))
  }

  test("a power declaring only id/source/contributions gets the trait defaults") {
    val power = minimalPower("power.trivial", "trivial")
    val ready = FirstGameSetupFixture
      .execute(new FirstGameSetupRules(FirstGameSetupFixture.catalog))._1 match {
      case OathState.Ready(state) => state
      case other => fail(s"expected Ready state, got $other")
    }
    val ctx = PowerCtx(
      state = ready,
      actor = ready.game.current.turn.activePlayer,
      source = power.source,
      window = PowerWindow.RecoverEligibility,
      nodePath = Vector("root")
    )

    assertEquals(power.priority, 0)
    assertEquals(power.applicable(ctx), true)
    assertEquals(power.shouldIgnore(power), false)
  }
}
