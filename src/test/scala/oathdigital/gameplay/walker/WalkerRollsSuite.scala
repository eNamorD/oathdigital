package oathdigital.gameplay.walker

import oathdigital.model._

/** Roll outcome mechanics: what a face vector scores, what a recorded roll may
  * claim, and how repeated rolls of one pool accumulate.
  */
class WalkerRollsSuite extends munit.FunSuite {
  private val attackPool = PoolKey("campaign.attack")
  private val defensePool = PoolKey("campaign.defense")
  private val attackRoll = Roll(attackPool, DiceSpec(DiceKind.Attack))
  private val defenseRoll = Roll(defensePool, DiceSpec(DiceKind.Defense))

  private def withPools(pools: (PoolKey, Int)*): ReadyGame =
    TestGameFixtures.ready.updateCurrent(current => current.copy(rollPools =
      pools.map { case (pool, count) => pool -> DicePoolState(count) }.toMap))

  private val attackFaces: Vector[DieFace] = Vector(AttackDieFace.TwoSwordsSkull,
    AttackDieFace.OneSword, AttackDieFace.HollowSword, AttackDieFace.HollowSword)

  test("an attack roll counts skulls and scores swords") {
    val state = withPools(attackPool -> 4)
    assertEquals(WalkerRolls.outcomeFor(attackRoll, state, attackFaces),
      Right(RollOutcome(attackPool, 4, attackFaces, skulls = 1, score = 4)))
  }

  test("a defense roll scores shields and has no skulls") {
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      DefenseDieFace.Doubler)
    val state = withPools(defensePool -> 2)
    assertEquals(WalkerRolls.outcomeFor(defenseRoll, state, faces),
      Right(RollOutcome(defensePool, 2, faces, skulls = 0,
        score = DefenseDieFace.score(Vector(DefenseDieFace.OneShield,
          DefenseDieFace.Doubler)))))
  }

  test("a roll must match its pool count and its die kind") {
    val state = withPools(attackPool -> 4, defensePool -> 2)
    assertEquals(WalkerRolls.outcomeFor(attackRoll, state, attackFaces.init),
      Left(OathViolation.InvalidEventOrder(
        s"rolled 3 dice for pool $attackPool but pool count is 4")))
    assertEquals(WalkerRolls.outcomeFor(attackRoll, state,
      Vector.fill(4)(DefenseDieFace.Blank)),
      Left(OathViolation.InvalidEventOrder(
        s"attack roll for pool $attackPool received a non-attack die face")))
    assertEquals(WalkerRolls.outcomeFor(defenseRoll, state,
      Vector(AttackDieFace.OneSword, AttackDieFace.OneSword)),
      Left(OathViolation.InvalidEventOrder(
        s"defense roll for pool $defensePool received a non-defense die face")))
  }

  test("a recorded roll needs its pool, its count and one family of faces") {
    val state = withPools(attackPool -> 4)
    assertEquals(WalkerRolls.outcomeForRecorded(state, attackPool, attackFaces),
      Right(RollOutcome(attackPool, 4, attackFaces, skulls = 1, score = 4)))
    assertEquals(WalkerRolls.outcomeForRecorded(state, defensePool,
      Vector(DefenseDieFace.Blank)),
      Left(OathViolation.InvalidEventOrder(
        s"recorded roll references missing pool ${defensePool.value}")))
    assertEquals(WalkerRolls.outcomeForRecorded(state, attackPool,
      attackFaces.init), Left(OathViolation.InvalidEventOrder(
        "recorded roll has 3 faces but pool count is 4")))
    assertEquals(WalkerRolls.outcomeForRecorded(state, attackPool,
      Vector[DieFace](AttackDieFace.OneSword, AttackDieFace.OneSword,
        DefenseDieFace.Blank, DefenseDieFace.Blank)),
      Left(OathViolation.InvalidEventOrder(
        "recorded roll mixes attack and defense faces")))
  }

  test("two attack rolls of one pool accumulate faces, skulls and a re-scored total") {
    val first: Vector[DieFace] = Vector(AttackDieFace.HollowSword,
      AttackDieFace.TwoSwordsSkull)
    val second: Vector[DieFace] = Vector(AttackDieFace.HollowSword,
      AttackDieFace.OneSword)
    val one = WalkerRolls.write(withPools(attackPool -> 2), RollOutcome(
      attackPool, 2, first, skulls = 1, score = 2))
    val two = WalkerRolls.write(one, RollOutcome(attackPool, 2, second,
      skulls = 0, score = 1))
    assertEquals(two.game.current.rollOutcomes(attackPool), RollOutcome(
      attackPool, 4, first ++ second, skulls = 1,
      score = AttackDieFace.score((first ++ second).collect {
        case face: AttackDieFace => face })))
  }
}
