package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, PowerWindow, Restriction, Transform}
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
import oathdigital.gameplay.walker.{ChoicePayload, OwnerQuery, ProcedureWalker,
  RollPayload, WalkerCtx, WalkerOutcome, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

object ProcedureWalkerSuite {
  /** Test-only open payload (D2: powers define their own payloads later). */
  final case class TestDecisionPayload(decision: String) extends DecisionPayload
  final case class TestOwner(actor: PlayerId) extends OwnerQuery {
    def owner(ctx: WalkerCtx): Option[PlayerId] = Some(actor)
  }

  /** Test-only composite that hooks a `PowerWindow` on an arbitrary children
    * vector. `Operation` is deliberately unsealed (unlike `CoreOperation`/
    * `PrimitiveOperation`, which Scala 2.13 pins to `CoreOperations.scala`)
    * precisely so a windowed node can be authored outside that file -- Task 4
    * wires real windows onto Recover's own tree; this suite proves the
    * walker's generic wiring with a tree it builds itself.
    */
  final case class WindowedNode(hook: PowerWindow,
      override val children: Vector[Operation]) extends Operation {
    override def window: Option[PowerWindow] = Some(hook)
  }

  /** A minimal `ContributingPower` contributing exactly one `Transform` at
    * one window -- enough to prove the walker's gather/fold wiring without
    * pulling in the real power catalog.
    */
  final case class TestTransformPower(id: PowerId, hook: PowerWindow,
      fn: (PowerCtx, Vector[Operation]) => Vector[Operation])
      extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Transform(fn)))
  }

  /** A minimal `ContributingPower` contributing exactly one `Restriction` at
    * one window.
    */
  final case class TestRestrictionPower(id: PowerId, hook: PowerWindow,
      fn: (PowerCtx, Operation) => Option[OathViolation])
      extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Restriction(fn)))
  }
}

/** Task 3 spec: ProcedureWalker auto-walks delta leaves (recording one
  * `WalkerStepRecorded` per executed leaf), parks at Decide/Roll, honors
  * Repeat(guard, body) with pure command-time guards, and never re-executes or
  * re-records a delta that already ran.
  */
class ProcedureWalkerSuite extends munit.FunSuite {
  private val actor: PlayerId = playerId

  private val decide: Decide = Decide(
    payload = ProcedureWalkerSuite.TestDecisionPayload("continue-or-stop"),
    owner = ProcedureWalkerSuite.TestOwner(actor),
    decisionId = "recover.choice")

  /** Legal ready state: supply full, pawn on S1; S3 cleared so a pawn Move
    * there is unambiguously legal (mirrors the OperationExecutorSuite fixture).
    */
  private val ready: ReadyGame = {
    val base = ReadyGame(
      game,
      Map(actor -> PlayerColor("red")),
      FirstGameSupportState(FirstGameFoundationProfile.FixedUnaltered, actor),
      MaterialBankState(
        Suit.all.map(_ -> 5).toMap,
        Map(ForceKind.Exile(lineageId) -> 14, ForceKind.Bandit -> 24)))
    val destination = base.game.current.map.sites(sites(2)).copy(
      forces = SiteForces.Empty)
    base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites =
        base.game.current.map.sites.updated(sites(2), destination)))))
  }

  private val move: Move = Move(Piece.Pawn(actor),
    PositionedLocation(Location.Site(sites.head)),
    PositionedLocation(Location.Site(sites(2))))
  private val adjust: AdjustSupply = AdjustSupply(actor, -1)

  private def pawnSiteOf(state: ReadyGame): Option[SiteId] =
    state.game.current.players.find(_.player == actor).get.pawnSite

  private def supplyOf(state: ReadyGame): Int =
    state.game.current.players.find(_.player == actor).get.board.supply.supply

  /** Applies a recorded step's ops from `state` (the caller's replay of the
    * journal, mirroring how the app layer rebuilds state after a park).
    */
  private def applyEvents(state: ReadyGame, events: Vector[OathEvent]): ReadyGame =
    events.foldLeft(state) { (current, event) =>
      val recorded = event match {
        case step: WalkerStepRecorded => step
        case other => fail(s"expected a WalkerStepRecorded, got $other")
      }
      OperationPipeline.run(current, recorded.ops,
        OperationPolicy.Permissive)(Right(_)).toOption.get
    }

  test("fresh walk of a legal delta pair finishes and records one event per leaf") {
    val tree: Operation = Sequence(move, adjust)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished walk, got $other")
    }

    assertEquals(pawnSiteOf(finalState), Some(sites(2)))
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
    assertEquals(events.size, 2)
    val recorded = events.map {
      case step: WalkerStepRecorded => step
      case other => fail(s"expected a WalkerStepRecorded, got $other")
    }
    assertEquals(recorded.map(_.actor), Vector(actor, actor))
    assertEquals(recorded.map(_.nodeId), Vector("0", "1"))
    assertEquals(recorded.map(_.ops), Vector(
      Vector[CoreOperation](move), Vector[CoreOperation](adjust)))
    recorded.foreach(step => assert(step.ops.nonEmpty))
    // Re-applying each recorded ops batch reproduces the walked state.
    assertEquals(applyEvents(ready, events), finalState)
  }

  test("a Decide at the head parks with no events, then the answered resume finishes") {
    val tree: Operation = Sequence(decide, adjust)

    val parked = ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        assertEquals(pending.at, Vector("0"))
        assertEquals(pending.answered, Vector.empty[Answered])
        assertEquals(pending.actor, actor)
        assertEquals(events, Vector.empty[OathEvent])
        pending
      case other => fail(s"expected a park at the Decide, got $other")
    }

    val answer = Answered(decide.decisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))
    ProcedureWalker.resolve(ready, tree, parked, answer) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 2)
        assertEquals(events.last.asInstanceOf[WalkerStepRecorded].ops,
          Vector[CoreOperation](adjust))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
        assert(finalState.game.current.walkerPending.isEmpty)
      case other => fail(s"expected the answered resume to finish, got $other")
    }
  }

  test("a Roll leaf parks too (faces ride a later command; Task 4 wires them)") {
    val roll = Roll(PoolKey("recover"), DiceSpec(DiceKind.Defense))
    val tree: Operation = Sequence(roll, adjust)

    ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        assertEquals(pending.at, Vector("0"))
        assertEquals(events, Vector.empty[OathEvent])
      case other => fail(s"expected a park at the Roll, got $other")
    }
  }

  test("auto-deltas executed before a park are recorded in the Parked events") {    val tree: Operation = Sequence(adjust, decide)

    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park after the auto-delta, got $other")
    }
    assertEquals(parked.at, Vector("1"))
    assertEquals(parked.answered, Vector.empty[Answered])
    assertEquals(parkEvents.size, 1)
    assertEquals(parkEvents.head.asInstanceOf[WalkerStepRecorded].ops,
      Vector[CoreOperation](adjust))

    // The caller folds the Parked events to obtain the state at the park.
    val parkedState = applyEvents(ready, parkEvents)
    val answer = Answered(decide.decisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))
    ProcedureWalker.resolve(parkedState, tree, parked, answer) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 1)
        assert(events.head.asInstanceOf[WalkerStepRecorded]
          .payload.isInstanceOf[ChoicePayload])
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
      case other => fail(s"expected the answered resume to finish, got $other")
    }
  }

  test("Repeat re-runs its body while the pure guard holds and records every pass") {
    // Pure state-based guard: full supply 7 loops while >= 5, so three body
    // passes run (7 -> 6 -> 5 -> 4), then the guard fails and the walk
    // continues past the Repeat into the trailing AdjustSupply.
    val guard: (ReadyGame, PendingTree) => Boolean =
      (state, _) => supplyOf(state) >= SupplyTrack.Maximum - 2
    val tree: Operation = Sequence(Repeat(guard, adjust), adjust)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished repeat walk, got $other")
    }
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 4)
    assertEquals(events.size, 4)
    assertEquals(events.collect {
      case step: WalkerStepRecorded => step.ops
    }, Vector.fill(4)(Vector[CoreOperation](adjust)))
  }

  test("Repeat whose guard is false from the start walks nothing") {
    val tree: Operation = Repeat((_: ReadyGame, _: PendingTree) => false, adjust)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished skipped repeat, got $other")
    }
    assertEquals(events, Vector.empty[OathEvent])
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum)
  }

  test("a park inside a Repeat body resumes at the same body point") {
    // Guard holds only at full supply; the body Decide parks before the delta
    // runs, so answering the decision and resuming completes the pass, drops
    // supply below the guard, and exits the Repeat — the delta executes once.
    val guard: (ReadyGame, PendingTree) => Boolean =
      (state, _) => supplyOf(state) >= SupplyTrack.Maximum
    val tree: Operation = Sequence(Repeat(guard, Sequence(decide, adjust)))

    val parked = ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        // Repeat (child 0) > body (child 0) > Decide (child 0).
        assertEquals(pending.at, Vector("0", "0", "0"))
        assertEquals(events, Vector.empty[OathEvent])
        pending
      case other => fail(s"expected a park inside the Repeat body, got $other")
    }

    val answer = Answered(decide.decisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))
    ProcedureWalker.resolve(ready, tree, parked, answer) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 2)
        assertEquals(events.last.asInstanceOf[WalkerStepRecorded].ops,
          Vector[CoreOperation](adjust))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
      case other => fail(s"expected the resumed repeat to finish, got $other")
    }
  }

  test("plain advance re-parks a repeated Decide even when an older pass " +
      "answered the same decision ID") {
    val olderAnswer = Answered(decide.decisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))
    val pending = PendingTree(Vector("0", "0", "0"), Vector(olderAnswer), actor)
    val tree: Operation = Sequence(Repeat(
      (_: ReadyGame, _: PendingTree) => true,
      Sequence(decide, adjust)))

    ProcedureWalker.advance(ready, tree, Some(pending)) match {
      case Right(WalkerOutcome.Parked(reparked, events)) =>
        assertEquals(reparked.at, pending.at)
        assertEquals(reparked.answered, Vector(olderAnswer))
        assertEquals(events, Vector.empty[OathEvent])
      case other => fail(s"expected a re-park at the repeated Decide, got $other")
    }
  }

  test("Finished clears stored pending trees and dice pools from the resulting state") {
    val dirty = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(
        walkerPending = Some(PendingTree(Vector("0"), Vector.empty, actor)),
        rollPools = Map(PoolKey("recover") -> DicePoolState(3)))))

    val (finalState, events) = ProcedureWalker.advance(dirty, adjust, None) match {
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished clear walk, got $other")
    }
    assertEquals(events.size, 1)
    assert(finalState.game.current.walkerPending.isEmpty)
    assertEquals(finalState.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
  }

  // -------------------------------------------------------------------------
  // Task 4: Roll node flow — faces ride the roll() command, count from state.
  // -------------------------------------------------------------------------

  private val recoverPool: PoolKey = PoolKey("recover")
  private val defenseRoll: Roll = Roll(recoverPool, DiceSpec(DiceKind.Defense))

  /** Advances `tree` to its Roll park, then folds the auto-deltas recorded
    * before the park into a fresh state (mirroring the app layer), so the
    * pool count set by a preceding ModifyDicePool is visible to roll().
    */
  private def parkAtRoll(tree: Operation): (PendingTree, ReadyGame) =
    ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        (pending, applyEvents(ready, events))
      case other => fail(s"expected a park at the Roll, got $other")
    }

  private def expectRollViolation(
      state: ReadyGame,
      tree: Operation,
      pending: PendingTree,
      faces: Vector[DieFace],
      detailContains: String
  ): Unit =
    ProcedureWalker.roll(state, tree, pending, faces) match {
      case Left(violation: OathViolation.InvalidEventOrder) =>
        assert(violation.detail.contains(detailContains),
          s"violation detail '${violation.detail}' should contain " +
            s"'$detailContains'")
      case other => fail(s"expected an InvalidEventOrder rejection, got $other")
    }

  test("a Roll park reports the pool and required count after auto pool deltas") {
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)

    val (pending, parkedState) = parkAtRoll(tree)
    assertEquals(pending.at, Vector("1"))
    assertEquals(pending.answered, Vector.empty[Answered])
    assertEquals(parkedState.game.current.rollPools,
      Map(recoverPool -> DicePoolState(2)))
    assertEquals(ProcedureWalker.parkedRoll(parkedState, tree, pending),
      Some((recoverPool, 2)))
  }

  test("malformed and overflowing Roll paths return typed failures") {
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val (_, parkedState) = parkAtRoll(tree)
    val paths = Vector(Vector("not-a-node"), Vector("999999999999999999999"))

    paths.foreach { path =>
      val pending = PendingTree(path, Vector.empty, actor)
      assertEquals(ProcedureWalker.parkedRoll(parkedState, tree, pending), None)
      assert(ProcedureWalker.roll(parkedState, tree, pending,
        Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)).left.toOption
        .exists(_.isInstanceOf[OathViolation.InvalidEventOrder]))
    }
  }

  test("roll() writes the RollOutcome and records one RollPayload event, then finishes") {
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      DefenseDieFace.Doubler)
    val (pending, parkedState) = parkAtRoll(tree)

    ProcedureWalker.roll(parkedState, tree, pending, faces) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        val expectedScore = DefenseDieFace.score(faces.collect {
          case face: DefenseDieFace => face
        })
        assertEquals(finalState.game.current.rollOutcomes(recoverPool),
          RollOutcome(recoverPool, 2, faces, skulls = 0, score = expectedScore))
        assertEquals(events.size, 1)
        val step = events.head match {
          case recorded: WalkerStepRecorded => recorded
          case other => fail(s"expected a WalkerStepRecorded, got $other")
        }
        assertEquals(step.payload, RollPayload(recoverPool, faces))
        assertEquals(step.ops, Vector.empty[CoreOperation])
        assert(finalState.game.current.walkerPending.isEmpty)
        assertEquals(finalState.game.current.rollPools,
          Map.empty[PoolKey, DicePoolState])
      case other => fail(s"expected the roll to finish the tree, got $other")
    }
  }

  test("roll() rejects a face count that differs from the pool count") {
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val (pending, parkedState) = parkAtRoll(tree)

    expectRollViolation(parkedState, tree, pending,
      Vector.fill(3)(DefenseDieFace.Blank),
      s"rolled 3 dice for pool $recoverPool but pool count is 2")
  }

  test("roll() rejects a non-DefenseDieFace mixed into a defense roll") {
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      AttackDieFace.HollowSword)
    val (pending, parkedState) = parkAtRoll(tree)

    expectRollViolation(parkedState, tree, pending, faces, "non-defense")
  }

  test("roll() rejects an Attack-kind roll in this defense-only slice") {
    val attackRoll = Roll(recoverPool, DiceSpec(DiceKind.Attack))
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 1), attackRoll)
    val (pending, parkedState) = parkAtRoll(tree)

    expectRollViolation(parkedState, tree, pending,
      Vector[DieFace](AttackDieFace.HollowSword),
      "attack dice not supported in this slice")
  }

  test("roll() continues auto-walking deltas after the Roll and records both events") {
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll,
      adjust)
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      DefenseDieFace.OneShield)
    val (pending, parkedState) = parkAtRoll(tree)

    ProcedureWalker.roll(parkedState, tree, pending, faces) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
        assertEquals(events.size, 2)
        assertEquals(events.head.asInstanceOf[WalkerStepRecorded].payload,
          RollPayload(recoverPool, faces))
        val delta = events(1).asInstanceOf[WalkerStepRecorded]
        assertEquals(delta.ops, Vector[CoreOperation](adjust))
        assertEquals(delta.nodeId, "2")
        assertEquals(finalState.game.current.rollOutcomes(recoverPool).score,
          DefenseDieFace.score(Vector(DefenseDieFace.OneShield,
            DefenseDieFace.OneShield)))
      case other => fail(s"expected the roll to finish the tree, got $other")
    }
  }

  test("roll() on a Decide park is rejected, not silently re-parked") {
    val tree: Operation = Sequence(decide, adjust)
    val (pending, _) = parkAtRoll(tree)
    assertEquals(pending.at, Vector("0"))

    ProcedureWalker.roll(ready, tree, pending, Vector.empty[DieFace]) match {
      case Left(violation: OathViolation.InvalidEventOrder) =>
        assert(violation.detail.contains("expected a Roll"),
          s"violation detail '${violation.detail}' should mention the Roll " +
            "expectation")
      case other => fail(s"expected a Left on a non-Roll park, got $other")
    }
  }

  // -------------------------------------------------------------------------
  // Task 3: power contributions -- gather/fold at a windowed node, restriction
  // rejection at command entry, and the replay-never-consults-contributions
  // guarantee. Recover's own tree carries no window yet (Task 4), so these
  // trees are built ad hoc with `ProcedureWalkerSuite.WindowedNode`.
  // -------------------------------------------------------------------------

  private val testWindow: PowerWindow = PowerWindow.RecoverModifierSelection

  test("a windowed node's Transform-inserted op is recorded in one step, " +
      "with the power id in contributions") {
    val powerId = PowerId("test.prepend-move")
    val power = ProcedureWalkerSuite.TestTransformPower(powerId, testWindow,
      (_, ops) => move +: ops)
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow, Vector(adjust))
    val tree: Operation = Sequence(windowed)

    ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 1)
        val step = events.head.asInstanceOf[WalkerStepRecorded]
        assertEquals(step.ops, Vector[CoreOperation](move, adjust))
        assertEquals(step.contributions, Vector(powerId))
        assertEquals(step.nodeId, "0")
        assertEquals(pawnSiteOf(finalState), Some(sites(2)))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
      case other => fail(s"expected a Finished walk, got $other")
    }
  }

  test("a node with no window records contributions as Vector.empty") {
    val tree: Operation = Sequence(adjust)

    ProcedureWalker.advance(ready, tree, None) match {
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.size, 1)
        assertEquals(events.head.asInstanceOf[WalkerStepRecorded].contributions,
          Vector.empty[PowerId])
      case other => fail(s"expected a Finished walk, got $other")
    }
  }

  test("a Restriction violation rejects the command with no events appended") {
    val violation: OathViolation = OathViolation.InvalidEventOrder(
      "test restriction forbids this action")
    val power = ProcedureWalkerSuite.TestRestrictionPower(
      PowerId("test.forbid"), testWindow, (_, _) => Some(violation))
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow, Vector(adjust))
    val tree: Operation = Sequence(windowed)
    val powers = WalkerPowers(Vector(power))

    val violations = ProcedureWalker.restrictionViolations(tree, powers,
      ready, actor)
    assertEquals(violations, Vector(violation))

    // Mirrors OathRules' command-entry check (Task 3 wiring rule): the first
    // violation rejects the command outright, before any node runs -- no
    // events, no walk.
    val command: Either[OathViolation, WalkerOutcome] =
      violations.headOption.toLeft(()).flatMap(_ =>
        ProcedureWalker.advance(ready, tree, None, powers))
    assertEquals(command, Left(violation))
  }

  test("replay of contributions-carrying events reaches the same state as " +
      "the live walk when no powers are present at replay") {
    val powerId = PowerId("test.prepend-move")
    val power = ProcedureWalkerSuite.TestTransformPower(powerId, testWindow,
      (_, ops) => move +: ops)
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow, Vector(adjust))
    val tree: Operation = Sequence(windowed)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None,
        WalkerPowers(Vector(power))) match {
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished walk, got $other")
    }
    val step = events.head.asInstanceOf[WalkerStepRecorded]
    assertEquals(step.contributions, Vector(powerId))

    // `applyRecorded` (replay) takes no `WalkerPowers` at all -- it applies
    // `ops` only and never re-gathers or re-transforms (spec decision 5).
    // Folding the SAME events through it, starting fresh from `ready`, must
    // reach the SAME state even though no power is available to replay:
    // `contributions` is an audit fact, not a replay input.
    val replayed = events.foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready))) {
      case (Right(state), event: WalkerEvent) =>
        ProcedureWalker.applyRecorded(state, event)
      case (Right(_), other) => fail(s"expected a WalkerEvent, got $other")
      case (left, _) => left
    }
    assertEquals(replayed, Right(OathState.Ready(finalState)))
  }
}
