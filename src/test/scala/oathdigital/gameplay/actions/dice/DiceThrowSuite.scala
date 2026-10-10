package oathdigital.gameplay.actions.dice

import oathdigital.model._
import oathdigital.testkit.Table

/** A throw: the faces its roll added, and every earlier face rerolled since,
  * numbered by the rolls of its pool (catalog batch 7, slice 2). */
class DiceThrowSuite extends munit.FunSuite:
  import DefenseDieFace.{Blank, OneShield, TwoShields}

  private val pool = PoolKey("recover")

  /** The quiet table after two Recover rolls of two dice that show `faces`,
    * with `rerolled` recorded on the outcome. */
  private def afterTwoRecoverRolls(faces: Vector[DieFace],
      rerolled: Vector[Int] = Vector.empty): ReadyGame =
    val (skulls, score) = RollOutcome.derive(faces)
    Table.start.ready.updateCurrent(current => current.copy(
      rollPools = Map(pool -> DicePoolState(2)),
      rollOutcomes = Map(pool -> RollOutcome(pool, faces.size, faces,
        skulls, score, throws = 2, rerolled = rerolled))))

  test("a throw is the faces the pool's last roll added"):
    val ready = afterTwoRecoverRolls(Vector(OneShield, Blank, Blank,
      TwoShields))
    assertEquals(DiceThrow.lastIndices(ready, pool), Vector(2, 3))

  test("an earlier face rerolled since the last roll joins its throw"):
    val ready = afterTwoRecoverRolls(Vector(OneShield, Blank, Blank,
      TwoShields), rerolled = Vector(0, 3))
    assertEquals(DiceThrow.lastIndices(ready, pool), Vector(0, 2, 3))

  test("a throw is numbered by the rolls of its pool, not by its dice"):
    // Two rolls of two dice, after which the pool holds three: four faces
    // divided by three dice would number this throw 1.
    val ready = afterTwoRecoverRolls(Vector(OneShield, Blank, Blank,
      TwoShields)).updateCurrent(current => current.copy(
        rollPools = Map(pool -> DicePoolState(3))))
    val choice = new RerollChoice(pool, DiceKind.Defense, Table.p1)
    assertEquals(choice.throwAt(ready).map(_.number), Some(2))
