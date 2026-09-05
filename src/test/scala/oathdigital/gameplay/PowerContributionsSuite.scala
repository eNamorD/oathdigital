package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.model.PowerId

class PowerContributionsSuite extends munit.FunSuite {
  private val none: Set[PowerId] = Set.empty

  test("suppression applies only when the dominant is active and the predicate holds") {
    val window = PowerWindow.TravelCost
    val dominant = PowerId("site.a.coast")
    val context = new WindowContext {
      override val activePowers: Vector[PowerId] =
        Vector(dominant, PowerId("site.b.island"))
    }
    SuppressionRegistry.register(
      window,
      dominant,
      Vector(PowerId("site.b.island"), PowerId("site.c.mountain"))
    )(_ => true)

    // Dominant active + predicate true -> its suppressed active ids drop.
    assertEquals(SuppressionRegistry.suppressed(window,
      Vector(dominant, PowerId("site.b.island"),
        PowerId("site.c.mountain")), context),
      Set(PowerId("site.b.island"), PowerId("site.c.mountain")))

    // Suppressed id not active is irrelevant.
    assertEquals(SuppressionRegistry.suppressed(window,
      Vector(dominant), context), none)

    // Dominant absent -> nothing suppressed.
    assertEquals(SuppressionRegistry.suppressed(window,
      Vector(PowerId("site.b.island"), PowerId("site.c.mountain")), context),
      none)
  }

  test("suppression predicate can veto by context") {
    val window = PowerWindow.TravelCost
    val dominant = PowerId("site.d.coast")
    val island = PowerId("site.e.island")
    SuppressionRegistry.register(
      window, dominant, Vector(island))(context =>
      context.activePowers.contains(dominant) &&
        context.activePowers.contains(PowerId("site.f.coast")))
    val context = new WindowContext {
      override val activePowers: Vector[PowerId] = Vector(dominant, island)
    }
    assertEquals(SuppressionRegistry.suppressed(window,
      Vector(dominant, island), context), none)
  }

  test("duplicate registration for the same window and dominant is rejected") {
    val window = PowerWindow.TravelCost
    val dominant = PowerId("site.dup.coast")
    SuppressionRegistry.register(
      window, dominant, Vector(PowerId("site.other.island")))(_ => true)
    intercept[IllegalArgumentException] {
      SuppressionRegistry.register(
        window, dominant, Vector(PowerId("site.other.island")))(_ => false)
    }
  }
}
