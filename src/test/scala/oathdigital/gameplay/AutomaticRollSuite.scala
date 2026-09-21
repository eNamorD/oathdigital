package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, RollPayload, WalkerDice,
  WalkerOutcome, WalkerPowers, WalkerSimulation, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** Automatic rolls: a `Roll` that takes its faces from the walker's dice
  * source and keeps walking, and the rules around it.
  */
class AutomaticRollSuite extends munit.FunSuite {
  private val noPowers = WalkerPowers.empty
  private val pool = PoolKey("campaign.attack")
  private val faces: Vector[DieFace] = Vector(AttackDieFace.TwoSwordsSkull,
    AttackDieFace.OneSword, AttackDieFace.HollowSword)
  private val fixed: WalkerDice = (kind, count) => kind match {
    case DiceKind.Attack => Right(faces.take(count))
    case DiceKind.Defense => Right(Vector.fill(count)(DefenseDieFace.OneShield))
  }
  private val automatic = Roll(pool, DiceSpec(DiceKind.Attack), RollMode.Automatic)
  private val afterPool = PoolKey("after")

  private def tree(steps: Operation*): Operation = Sequence(steps.toVector)

  test("an automatic roll rolls from the source and walks on without parking") {
    val walk = tree(ModifyDicePool(pool, 3), automatic,
      ModifyDicePool(afterPool, 1))
    ProcedureWalker.advance(ready, walk, None, noPowers, fixed) match {
      case Right(WalkerOutcome.Finished(done, events)) =>
        assertEquals(done.game.current.rollOutcomes(pool),
          RollOutcome(pool, 3, faces, skulls = 1, score = 3))
        val payloads = events.collect { case step: WalkerStepRecorded => step.payload }
        assertEquals(payloads.count(_.isInstanceOf[RollPayload]), 1)
        assert(payloads.contains(RollPayload(pool, faces, automatic = true)))
        assertEquals(events.size, 3)
      case other => fail(s"an automatic roll must not park, got $other")
    }
  }

  test("a pool of zero dice is skipped and records nothing") {
    ProcedureWalker.advance(ready, tree(automatic), None, noPowers, fixed) match {
      case Right(WalkerOutcome.Finished(done, events)) =>
        assertEquals(events, Vector.empty[OathEvent])
        assertEquals(done.game.current.rollOutcomes, Map.empty[PoolKey, RollOutcome])
      case other => fail(s"expected the empty roll to finish, got $other")
    }
  }

  test("without a dice source an automatic roll fails with a typed violation") {
    val walk = tree(ModifyDicePool(pool, 3), automatic)
    assertEquals(ProcedureWalker.advance(ready, walk, None, noPowers),
      Left(OathViolation.InvalidEventOrder(
        "walker has no dice source for an automatic roll")))
  }

  test("a source that returns the wrong number of faces is rejected") {
    val short: WalkerDice = (_, _) => Right(faces.take(1))
    val walk = tree(ModifyDicePool(pool, 3), automatic)
    assertEquals(ProcedureWalker.advance(ready, walk, None, noPowers, short),
      Left(OathViolation.InvalidEventOrder(
        s"rolled 1 dice for pool $pool but pool count is 3")))
  }

  test("a roll left in its default mode still parks") {
    val walk = tree(ModifyDicePool(pool, 2), Roll(pool, DiceSpec(DiceKind.Defense)))
    ProcedureWalker.advance(ready, walk, None, noPowers, fixed) match {
      case Right(WalkerOutcome.Parked(pending, _)) =>
        assertEquals(pending.at, Vector("1"))
      case other => fail(s"a default roll must park, got $other")
    }
  }

  test("parkedRoll reports a parked roll and never an automatic one") {
    val parkedTree = tree(ModifyDicePool(pool, 2), Roll(pool, DiceSpec(DiceKind.Defense)))
    val autoTree = tree(ModifyDicePool(pool, 2), automatic)
    val state = ready.updateCurrent(_.copy(rollPools = Map(pool -> DicePoolState(2))))
    val at = PendingTree(Vector("1"), Vector.empty)
    assertEquals(ProcedureWalker.parkedRoll(state, parkedTree, at, noPowers),
      Some((pool, 2)))
    assertEquals(ProcedureWalker.parkedRoll(state, autoTree, at, noPowers), None)
  }

  test("an automatic roll replays from its recorded payload and rejects a tampered one") {
    val walk = tree(ModifyDicePool(pool, 3), automatic)
    val Right(WalkerOutcome.Finished(done, events)) =
      ProcedureWalker.advance(ready, walk, None, noPowers, fixed): @unchecked
    val recorded = events.collect { case step: WalkerStepRecorded => step }
    def replay(steps: Vector[WalkerStepRecorded]) =
      steps.foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready))) {
        case (Right(state), step) => ProcedureWalker.applyRecorded(state, step)
        case (failure, _) => failure
      }
    val Right(OathState.Ready(replayed)) = replay(recorded): @unchecked
    assertEquals(replayed.game.current.rollOutcomes,
      done.game.current.rollOutcomes)
    val tampered = recorded.init :+ recorded.last.copy(payload =
      RollPayload(pool, faces.take(2), automatic = true))
    assertEquals(replay(tampered), Left(OathViolation.InvalidEventOrder(
      "recorded roll has 2 faces but pool count is 3")))
  }

  test("a simulated tree rolls placeholder faces instead of failing") {
    val walk = tree(ModifyDicePool(pool, 2), automatic)
    assert(WalkerSimulation.run(walk, ready, noPowers).isRight)
  }
}
