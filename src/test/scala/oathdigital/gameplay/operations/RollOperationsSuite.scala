package oathdigital.gameplay.operations

import oathdigital.model._
import oathdigital.testkit.Table

/** `Reroll`: rerolled faces replace the old ones in place, and the skulls
  * and score are worked out again (catalog batch 7, slice 1). */
class RollOperationsSuite extends munit.FunSuite:
  private val pool = PoolKey("campaign.attack")
  import AttackDieFace.{HollowSword, OneSword, TwoSwordsSkull}

  /** The quiet table with `faces` recorded for the attack pool, which
    * holds as many dice. */
  private def rolled(faces: Vector[DieFace]): ReadyGame =
    val (skulls, score) = RollOutcome.derive(faces)
    Table.start.ready.updateCurrent(current => current.copy(
      rollPools = Map(pool -> DicePoolState(faces.size)),
      rollOutcomes = Map(pool ->
        RollOutcome(pool, faces.size, faces, skulls, score))))

  test("rerolled faces replace the chosen ones and rescore the roll"):
    val before = rolled(Vector(HollowSword, OneSword, HollowSword))
    val after = RollOperations.reroll(before, pool, Vector(0, 2),
      Some(Vector(TwoSwordsSkull, OneSword))).toOption.get
    val outcome = after.game.current.rollOutcomes(pool)
    assertEquals(outcome.faces,
      Vector[DieFace](TwoSwordsSkull, OneSword, OneSword))
    assertEquals(outcome.skulls, 1)
    assertEquals(outcome.score, 4)
    assertEquals(outcome.count, 3)

  test("a reroll records which dice it rerolled"):
    val before = rolled(Vector(HollowSword, OneSword, HollowSword))
    val once = RollOperations.reroll(before, pool, Vector(2),
      Some(Vector(OneSword))).toOption.get
    val twice = RollOperations.reroll(once, pool, Vector(0, 2),
      Some(Vector(OneSword, OneSword))).toOption.get
    assertEquals(twice.game.current.rollOutcomes(pool).rerolled,
      Vector(0, 2))

  test("a reroll keeps skulls another step already changed"):
    // A power lowered the skulls to 0 after the roll; rerolling a sword
    // into a skull adds one to what stands.
    val before = rolled(Vector(TwoSwordsSkull, OneSword))
    val lowered = before.updateCurrent(current => current.copy(
      rollOutcomes = current.rollOutcomes.updated(pool,
        current.rollOutcomes(pool).copy(skulls = 0))))
    val after = RollOperations.reroll(lowered, pool, Vector(1),
      Some(Vector(TwoSwordsSkull))).toOption.get
    assertEquals(after.game.current.rollOutcomes(pool).skulls, 1)
    // Rerolling the skull itself away leaves none, not fewer.
    val cleared = RollOperations.reroll(lowered, pool, Vector(0),
      Some(Vector(OneSword))).toOption.get
    assertEquals(cleared.game.current.rollOutcomes(pool).skulls, 0)

  test("a reroll without faces, of a missing pool, or out of range fails"):
    val before = rolled(Vector(HollowSword, OneSword))
    assert(RollOperations.reroll(before, pool, Vector(0), None).isLeft)
    assert(RollOperations.reroll(before, PoolKey("recover"), Vector(0),
      Some(Vector(OneSword))).isLeft)
    assert(RollOperations.reroll(before, pool, Vector(2),
      Some(Vector(OneSword))).isLeft)
    assert(RollOperations.reroll(before, pool, Vector(0, 0),
      Some(Vector(OneSword, OneSword))).isLeft)
    assert(RollOperations.reroll(before, pool, Vector(0),
      Some(Vector(OneSword, OneSword))).isLeft)
    assert(RollOperations.reroll(before, pool, Vector(0),
      Some(Vector(DefenseDieFace.Blank))).isLeft)
