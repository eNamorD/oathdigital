package oathdigital.gameplay.operations

import oathdigital.model._

class ModifyRollOutcomeSuite extends munit.FunSuite {
  private val pool = PoolKey("campaign.attack")
  private val faces: Vector[DieFace] = Vector(AttackDieFace.TwoSwordsSkull,
    AttackDieFace.OneSword)
  private val rolled = TestGameFixtures.ready.updateCurrent(current =>
    current.copy(rollOutcomes = Map(pool -> RollOutcome(pool, 2, faces,
      skulls = 1, score = 3))))

  private def run(ready: ReadyGame, operation: CoreOperation) =
    new OperationExecutor().executeAll(ready, Vector(operation))
      .toOption.get.game.current.rollOutcomes

  test("modifying an outcome rewrites only the fields it names") {
    assertEquals(run(rolled, ModifyRollOutcome(pool, Some(0), None))(pool),
      RollOutcome(pool, 2, faces, skulls = 0, score = 3))
    assertEquals(run(rolled, ModifyRollOutcome(pool, None, Some(1)))(pool),
      RollOutcome(pool, 2, faces, skulls = 1, score = 1))
    assertEquals(run(rolled, ModifyRollOutcome(pool, Some(0), Some(0)))(pool),
      RollOutcome(pool, 2, faces, skulls = 0, score = 0))
  }

  test("modifying a pool that never rolled creates an empty outcome with the fields") {
    val outcomes = run(TestGameFixtures.ready,
      ModifyRollOutcome(pool, Some(0), Some(5)))
    assertEquals(outcomes(pool),
      RollOutcome(pool, 0, Vector.empty, skulls = 0, score = 5))
  }

  test("modifying one pool leaves every other pool alone") {
    val other = PoolKey("campaign.defense")
    val both = rolled.updateCurrent(current => current.copy(rollOutcomes =
      current.rollOutcomes.updated(other, RollOutcome(other, 1,
        Vector(DefenseDieFace.OneShield), 0, 1))))
    val outcomes = run(both, ModifyRollOutcome(pool, None, Some(9)))
    assertEquals(outcomes(other).score, 1)
    assertEquals(outcomes(pool).score, 9)
  }
}
