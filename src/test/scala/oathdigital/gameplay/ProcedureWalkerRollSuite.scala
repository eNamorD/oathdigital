package oathdigital.gameplay

import oathdigital.gameplay.walker.{ProcedureWalker, RollPayload,
  WalkerOutcome, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.Outcomes.{parked, recorded}
import oathdigital.testkit.ProcedureWalkerFixture._

/** ProcedureWalker's Roll nodes: a Roll parks, and `roll()` takes the faces,
  * counts from state and walks on.
  */
class ProcedureWalkerRollSuite extends munit.FunSuite:
  test("a Roll leaf parks too (faces ride a later command; Task 4 wires them)"):
    val roll = Roll(PoolKey("recover"), DiceSpec(DiceKind.Defense))
    val tree: Operation = Sequence(roll, spendOneSupply)

    ProcedureWalker.advance(ready, tree, None, noPowers) match
      case Right(WalkerOutcome.Parked(pending, events)) =>
        assertEquals(pending.at, Vector("0"))
        assertEquals(events, Vector.empty[OathEvent])
      case other => fail(s"expected a park at the Roll, got $other")

  /** Advances `tree` to its Roll park, then folds the auto-deltas recorded
    * before the park into a fresh state (mirroring the app layer), so the
    * pool count set by a preceding ModifyDicePool is visible to roll().
    */
  private def parkAtRoll(tree: Operation): ParkedAtRoll =
    val park = parked(ProcedureWalker.advance(ready, tree, None, noPowers))
    ParkedAtRoll(park.tree, applyEvents(ready, park.events))

  /** A Roll park and the state with the steps before it applied. */
  private final case class ParkedAtRoll(pending: PendingTree,
      state: ReadyGame)

  private def expectRollViolation(
      state: ReadyGame,
      tree: Operation,
      pending: PendingTree,
      faces: Vector[DieFace],
      detailContains: String
  ): Unit =
    ProcedureWalker.roll(state, tree, pending, faces, noPowers) match
      case Left(violation: OathViolation.InvalidEventOrder) =>
        assert(violation.detail.contains(detailContains),
          s"violation detail '${violation.detail}' should contain " +
            s"'$detailContains'")
      case other => fail(s"expected an InvalidEventOrder rejection, got $other")

  test("a Roll park reports the pool and required count after auto pool deltas"):
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)

    val ParkedAtRoll(pending, parkedState) = parkAtRoll(tree)
    assertEquals(pending.at, Vector("1"))
    assertEquals(pending.answered, Vector.empty[Answered])
    assertEquals(parkedState.game.current.rollPools,
      Map(recoverPool -> DicePoolState(2)))
    assertEquals(ProcedureWalker.parkedRoll(parkedState, tree, pending,
      noPowers), Some((recoverPool, 2)))

  test("malformed and overflowing Roll paths return typed failures"):
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val parkedState = parkAtRoll(tree).state
    val paths = Vector(Vector("not-a-node"), Vector("999999999999999999999"))

    paths.foreach { path =>
      val pending = PendingTree(path, Vector.empty)
      assertEquals(ProcedureWalker.parkedRoll(parkedState, tree, pending,
        noPowers), None)
      assert(ProcedureWalker.roll(parkedState, tree, pending,
        Vector(DefenseDieFace.Blank, DefenseDieFace.Blank), noPowers)
        .left.toOption
        .exists(_.isInstanceOf[OathViolation.InvalidEventOrder]))
    }

  test("roll() writes the RollOutcome and records one RollPayload event, then finishes"):
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      DefenseDieFace.Doubler)
    val ParkedAtRoll(pending, parkedState) = parkAtRoll(tree)

    ProcedureWalker.roll(parkedState, tree, pending, faces, noPowers) match
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        val expectedScore = DefenseDieFace.score(faces.collect {
          case face: DefenseDieFace => face
        })
        assertEquals(finalState.game.current.rollOutcomes(recoverPool),
          RollOutcome(recoverPool, 2, faces, skulls = 0, score = expectedScore))
        assertEquals(events.size, 1)
        val step = events.head match
          case recorded: WalkerStepRecorded => recorded
          case other => fail(s"expected a WalkerStepRecorded, got $other")
        assertEquals(step.payload, RollPayload(recoverPool, faces))
        assertEquals(step.ops, Vector.empty[CoreOperation])
        assert(finalState.game.current.walkerPending.isEmpty)
        assertEquals(finalState.game.current.rollPools,
          Map.empty[PoolKey, DicePoolState])
      case other => fail(s"expected the roll to finish the tree, got $other")

  test("roll() rejects a face count that differs from the pool count"):
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val ParkedAtRoll(pending, parkedState) = parkAtRoll(tree)

    expectRollViolation(parkedState, tree, pending,
      Vector.fill(3)(DefenseDieFace.Blank),
      s"rolled 3 dice for pool $recoverPool but pool count is 2")

  test("roll() rejects a non-DefenseDieFace mixed into a defense roll"):
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      AttackDieFace.HollowSword)
    val ParkedAtRoll(pending, parkedState) = parkAtRoll(tree)

    expectRollViolation(parkedState, tree, pending, faces, "non-defense")

  test("roll() accepts an Attack roll and derives skulls and score from its faces"):
    val attackPool = PoolKey("campaign.attack")
    val tree: Operation = Sequence(ModifyDicePool(attackPool, 4),
      Roll(attackPool, DiceSpec(DiceKind.Attack)))
    val faces: Vector[DieFace] = Vector(AttackDieFace.TwoSwordsSkull,
      AttackDieFace.OneSword, AttackDieFace.HollowSword, AttackDieFace.HollowSword)
    val ParkedAtRoll(pending, parkedState) = parkAtRoll(tree)
    ProcedureWalker.roll(parkedState, tree, pending, faces, noPowers) match
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(finalState.game.current.rollOutcomes(attackPool),
          RollOutcome(attackPool, 4, faces, skulls = 1, score = 4))
        assertEquals(recorded(events.head).payload,
          RollPayload(attackPool, faces))
      case other => fail(s"expected the attack roll to finish the tree, got $other")

  test("roll() rejects a defense face mixed into an attack roll"):
    val attackPool = PoolKey("campaign.attack")
    val tree: Operation = Sequence(ModifyDicePool(attackPool, 2),
      Roll(attackPool, DiceSpec(DiceKind.Attack)))
    val ParkedAtRoll(pending, parkedState) = parkAtRoll(tree)
    expectRollViolation(parkedState, tree, pending,
      Vector[DieFace](AttackDieFace.HollowSword, DefenseDieFace.Blank),
      "non-attack")

  test("roll() continues auto-walking deltas after the Roll and records both events"):
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll,
      spendOneSupply)
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      DefenseDieFace.OneShield)
    val ParkedAtRoll(pending, parkedState) = parkAtRoll(tree)

    ProcedureWalker.roll(parkedState, tree, pending, faces, noPowers) match
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
        assertEquals(events.size, 2)
        assertEquals(recorded(events.head).payload,
          RollPayload(recoverPool, faces))
        val delta = recorded(events(1))
        assertEquals(delta.ops, Vector[CoreOperation](spendOneSupply))
        assertEquals(delta.nodeId, "2")
        assertEquals(finalState.game.current.rollOutcomes(recoverPool).score,
          DefenseDieFace.score(Vector(DefenseDieFace.OneShield,
            DefenseDieFace.OneShield)))
      case other => fail(s"expected the roll to finish the tree, got $other")

  test("roll() on a Decide park is rejected, not silently re-parked"):
    val tree: Operation = Sequence(decide, spendOneSupply)
    val pending = parkAtRoll(tree).pending
    assertEquals(pending.at, Vector("0"))

    ProcedureWalker.roll(ready, tree, pending, Vector.empty[DieFace],
        noPowers) match
      case Left(violation: OathViolation.InvalidEventOrder) =>
        assert(violation.detail.contains("expected a Roll"),
          s"violation detail '${violation.detail}' should mention the Roll " +
            "expectation")
      case other => fail(s"expected a Left on a non-Roll park, got $other")
