package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, PowerResolution, PowerWindow, Restriction, Transform}
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  RollPayload, WalkerOutcome, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.TestGameFixtures._

object ProcedureWalkerSuite {
  val continueOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("continue")
  val stopOption: DecisionOptionRef.Button = DecisionOptionRef.Button("stop")

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
      fn: (PowerCtx, Vector[Operation]) => Vector[Operation],
      override val resolution: PowerResolution = PowerResolution.Automatic)
      extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Transform(fn)))
  }

  /** A `ContributingPower` with an arbitrary contribution map, for the cases
    * that need one power speaking at two windows.
    */
  final case class TestPower(id: PowerId,
      contributions: Map[PowerWindow, Vector[Contribution]])
      extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
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

  /** Every walk entry point states its power source explicitly -- there is
    * no default, so a suite can never silently walk unpowered while
    * production walks with powers.
    */
  private val noPowers: WalkerPowers = WalkerPowers.empty

  import ProcedureWalkerSuite.{continueOption, stopOption}

  private val decide: Decide = Decide(
    decisionId = "recover.choice",
    owner = actor,
    query = DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(continueOption, "Continue"),
      DecisionOption.Button(stopOption, "Stop"))))

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
  private val adjust: SpendSupply = SpendSupply(actor, 1)

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
        OperationPolicy.Permissive)(Right(_)).toOption.get.state
    }

  test("fresh walk of a legal delta pair finishes and records one event per leaf") {
    val tree: Operation = Sequence(move, adjust)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None,
        noPowers) match {
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
    assertEquals(recorded.map(_.nodeId), Vector("0", "1"))
    assertEquals(recorded.map(_.ops), Vector(
      Vector[CoreOperation](move), Vector[CoreOperation](adjust)))
    recorded.foreach(step => assert(step.ops.nonEmpty))
    // Re-applying each recorded ops batch reproduces the walked state.
    assertEquals(applyEvents(ready, events), finalState)
  }

  test("all-skipped BuildOps records no delta step") {
    val empty = ready.copy(banks = ready.banks.copy(favor =
      ready.banks.favor.updated(Suit.Order, 0)))
    val tree = BuildOps((_, _) =>
      Right(Vector(Gain.Favor(actor, Suit.Order, 1))))
    ProcedureWalker.advance(empty, tree, None, noPowers) match {
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, empty)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")
    }
  }

  test("BuildOps records actual reduced count") {
    val six = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map { player =>
        player.copy(board = player.board.copy(supply = SupplyTrack(6)))
      })))
    val tree = BuildOps((_, _) => Right(Vector(GainSupply(actor, 3))))
    ProcedureWalker.advance(six, tree, None, noPowers) match {
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.collect { case step: WalkerStepRecorded => step.ops },
          Vector(Vector[CoreOperation](GainSupply(actor, 1))))
      case other => fail(s"expected a Finished walk, got $other")
    }
  }

  test("BuildOps filters immune discard and replays the legal discard") {
    val first = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, 1, 0, actor)
    val second = Discard.Denizen(worldDenizen,
      PositionedLocation(Location.Site(sites(1))), Region.Cradle,
      Suit.Order, 0, 0, actor)
    val current = ready.game.current
    val source = ready.copy(game = ready.game.copy(current = current.copy(
      commonCards = current.commonCards.copy(worldDeck = Vector.empty),
      map = current.map.copy(sites = current.map.sites.updated(sites(1),
        current.map.sites(sites(1)).copy(denizens = Vector(
          DenizenState(worldDenizen, Orientation.FaceUp, Tokens.empty))))))))
    val immunity = new OperationRestriction {
      override def reason(state: ReadyGame, operation: CoreOperation) =
        Option.when(operation == first)(OperationReason("immune",
          "first card cannot be discarded", OperationReasonKind.Impossible))
    }
    val tree = Sequence(Vector(BuildOps((_, _) => Right(Vector(first, second)),
      restrictions = (_, _) => Vector(immunity))))
    ProcedureWalker.advance(source, tree, None, noPowers) match {
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(events.collect { case step: WalkerStepRecorded => step.ops },
          Vector(Vector[CoreOperation](second)))
        val replay = events.foldLeft[Either[OathViolation, OathState]](
          Right(OathState.Ready(source))) { (result, event) =>
          result.flatMap(ProcedureWalker.applyRecorded(_, event
            .asInstanceOf[WalkerStepRecorded]))
        }
        assertEquals(replay, Right(OathState.Ready(state)))
      case other => fail(s"expected a Finished walk, got $other")
    }
  }

  test("optional immune-only discard records nothing; required discard rejects") {
    val discard = Discard.Denizen(siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)), Region.Cradle,
      Suit.Order, 1, 0, actor)
    val immunity = new OperationRestriction {
      override def reason(state: ReadyGame, operation: CoreOperation) =
        Some(OperationReason("immune", "site card cannot be discarded",
          OperationReasonKind.Impossible))
    }
    def tree(operation: CoreOperation): Operation = Sequence(Vector(
      BuildOps((_, _) => Right(Vector(operation)),
        restrictions = (_, _) => Vector(immunity))))
    ProcedureWalker.advance(ready, tree(discard), None, noPowers) match {
      case Right(WalkerOutcome.Finished(state, events)) =>
        assertEquals(state, ready)
        assertEquals(events, Vector.empty)
      case other => fail(s"expected a Finished walk, got $other")
    }
    assert(ProcedureWalker.advance(ready,
      tree(discard.copy(required = true)), None, noPowers).isLeft)
  }

  test("a Decide at the head parks with no events, then the answered resume finishes") {
    val tree: Operation = Sequence(decide, adjust)

    val parked = ProcedureWalker.advance(ready, tree, None, noPowers) match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        assertEquals(pending.at, Vector("0"))
        assertEquals(pending.answered, Vector.empty[Answered])
        assertEquals(events, Vector.empty[OathEvent])
        pending
      case other => fail(s"expected a park at the Decide, got $other")
    }

    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(ready, tree, parked, answer, noPowers) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 2)
        assertEquals(events.last.asInstanceOf[WalkerStepRecorded].ops,
          Vector[CoreOperation](adjust))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
        assert(finalState.game.current.walkerPending.isEmpty)
      case other => fail(s"expected the answered resume to finish, got $other")
    }
  }

  /** Walks `tree` to its first Decide park and hands back the park. */
  private def parkAtDecide(tree: Operation)
      : (PendingTree, Vector[OathEvent]) =
    ProcedureWalker.advance(ready, tree, None, noPowers) match {
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the Decide, got $other")
    }

  // -------------------------------------------------------------------------
  // Generic decision resolution: the walker accepts exactly what a node's
  // declared query states, and nothing about the action that declared it.
  // -------------------------------------------------------------------------

  test("a Decide accepts exactly its declared options and rejects an " +
      "undeclared reference") {
    val tree: Operation = Sequence(decide, adjust)
    val (pending, _) = parkAtDecide(tree)

    Vector(continueOption, stopOption).foreach { option =>
      assert(ProcedureWalker.resolve(ready, tree, pending,
        Answered(decide.decisionId, ChooseOneAnswer(option), actor),
        noPowers).isRight, s"$option is declared and must be accepted")
    }

    val undeclared = Answered(decide.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("teleport")), actor)
    assertEquals(ProcedureWalker.resolve(ready, tree, pending, undeclared,
      noPowers),
      Left(OathViolation.InvalidEventOrder(
        "decision recover.choice does not offer the selected option")):
        Either[OathViolation, WalkerOutcome])
  }

  test("a Decide rejects an answer of the wrong shape for its query") {
    val tree: Operation = Sequence(decide, adjust)
    val (pending, _) = parkAtDecide(tree)

    assertEquals(ProcedureWalker.resolve(ready, tree, pending,
      Answered(decide.decisionId,
        DecisionAnswer.PartitionAnswer(Vector.empty), actor),
      noPowers),
      Left(OathViolation.InvalidEventOrder(
        "decision recover.choice expects a single-choice answer")):
        Either[OathViolation, WalkerOutcome])
  }

  test("a malformed query is rejected as a contract failure before the " +
      "submitted answer is even looked at") {
    // An empty option set, and a partition whose single section takes every
    // option: both are queries no answer could make meaningful, so the
    // rejection names the query rather than the submission.
    val empty = decide.copy(
      query = DecisionQuery.ChooseOne(Vector.empty))
    val forced = decide.copy(query = DecisionQuery.Partition(
      Vector(DecisionSection("all", "All", 1)),
      Vector(DecisionOption.Button(continueOption, "Continue"))))

    Vector(
      empty -> "declares no options",
      forced -> "declares fewer than two sections"
    ).foreach { case (node, detail) =>
      val tree: Operation = Sequence(node, adjust)
      val (pending, _) = parkAtDecide(tree)
      assertEquals(ProcedureWalker.resolve(ready, tree, pending,
        Answered(node.decisionId, ChooseOneAnswer(continueOption), actor),
        noPowers),
        Left(OathViolation.InvalidEventOrder(
          s"decision recover.choice $detail")):
          Either[OathViolation, WalkerOutcome])
    }
  }

  test("a Decide answered by anyone but its owner is rejected as " +
      "the wrong player") {
    val tree: Operation = Sequence(decide, adjust)
    val (pending, _) = parkAtDecide(tree)
    val intruder = PlayerId("intruder")

    assertEquals(ProcedureWalker.resolve(ready, tree,
      pending,
      Answered(decide.decisionId, ChooseOneAnswer(continueOption), intruder),
      noPowers),
      Left(OathViolation.WrongPlayer(actor, intruder)):
        Either[OathViolation, WalkerOutcome])
  }

  test("a partition Decide accepts a complete legal placement and rejects " +
      "one that starves a section") {
    val options = Vector("a", "b", "c").map(key =>
      DecisionOption.Button(DecisionOptionRef.Button(key), key.toUpperCase))
    val node = Decide("split", actor, DecisionQuery.Partition(
      Vector(DecisionSection("left", "Left", 2),
        DecisionSection("right", "Right", 1)),
      options))
    val tree: Operation = Sequence(node, adjust)
    val (pending, _) = parkAtDecide(tree)

    def answer(sections: Vector[String]): Answered = Answered("split",
      DecisionAnswer.PartitionAnswer(options.zip(sections).map {
        case (option, section) => DecisionPlacement(option.ref, section)
      }), actor)

    assert(ProcedureWalker.resolve(ready, tree, pending,
      answer(Vector("left", "left", "right")), noPowers).isRight)
    assertEquals(ProcedureWalker.resolve(ready, tree, pending,
      answer(Vector("left", "right", "right")), noPowers),
      Left(OathViolation.InvalidEventOrder(
        "decision split leaves section 'left' below its minimum of 2")):
        Either[OathViolation, WalkerOutcome])
  }

  test("a Roll leaf parks too (faces ride a later command; Task 4 wires them)") {
    val roll = Roll(PoolKey("recover"), DiceSpec(DiceKind.Defense))
    val tree: Operation = Sequence(roll, adjust)

    ProcedureWalker.advance(ready, tree, None, noPowers) match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        assertEquals(pending.at, Vector("0"))
        assertEquals(events, Vector.empty[OathEvent])
      case other => fail(s"expected a park at the Roll, got $other")
    }
  }

  test("auto-deltas executed before a park are recorded in the Parked events") {    val tree: Operation = Sequence(adjust, decide)

    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        noPowers) match {
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
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(parkedState, tree, parked, answer,
        noPowers) match {
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
    // continues past the Repeat into the trailing SpendSupply.
    val guard: (ReadyGame, PendingTree) => Boolean =
      (state, _) => supplyOf(state) >= SupplyTrack.Maximum - 2
    val tree: Operation = Sequence(Repeat(guard, adjust), adjust)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None,
        noPowers) match {
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

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None,
        noPowers) match {
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

    val parked = ProcedureWalker.advance(ready, tree, None, noPowers) match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        // Repeat (child 0) > body (child 0) > Decide (child 0).
        assertEquals(pending.at, Vector("0", "0", "0"))
        assertEquals(events, Vector.empty[OathEvent])
        pending
      case other => fail(s"expected a park inside the Repeat body, got $other")
    }

    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(ready, tree, parked, answer, noPowers) match {
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
      ChooseOneAnswer(continueOption), actor)
    val pending = PendingTree(Vector("0", "0", "0"), Vector(olderAnswer))
    val tree: Operation = Sequence(Repeat(
      (_: ReadyGame, _: PendingTree) => true,
      Sequence(decide, adjust)))

    ProcedureWalker.advance(ready, tree, Some(pending), noPowers) match {
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
        walkerPending = Some(PendingTree(Vector("0"), Vector.empty)),
        rollPools = Map(PoolKey("recover") -> DicePoolState(3)))))

    val (finalState, events) = ProcedureWalker.advance(dirty, adjust, None,
        noPowers) match {
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
    ProcedureWalker.advance(ready, tree, None, noPowers) match {
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
    ProcedureWalker.roll(state, tree, pending, faces, noPowers) match {
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
    assertEquals(ProcedureWalker.parkedRoll(parkedState, tree, pending,
      noPowers), Some((recoverPool, 2)))
  }

  test("malformed and overflowing Roll paths return typed failures") {
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val (_, parkedState) = parkAtRoll(tree)
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
  }

  test("roll() writes the RollOutcome and records one RollPayload event, then finishes") {
    val tree: Operation = Sequence(ModifyDicePool(recoverPool, 2), defenseRoll)
    val faces: Vector[DieFace] = Vector(DefenseDieFace.OneShield,
      DefenseDieFace.Doubler)
    val (pending, parkedState) = parkAtRoll(tree)

    ProcedureWalker.roll(parkedState, tree, pending, faces, noPowers) match {
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

    ProcedureWalker.roll(parkedState, tree, pending, faces, noPowers) match {
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

    ProcedureWalker.roll(ready, tree, pending, Vector.empty[DieFace],
        noPowers) match {
      case Left(violation: OathViolation.InvalidEventOrder) =>
        assert(violation.detail.contains("expected a Roll"),
          s"violation detail '${violation.detail}' should mention the Roll " +
            "expectation")
      case other => fail(s"expected a Left on a non-Roll park, got $other")
    }
  }

  // -------------------------------------------------------------------------
  // Task 3: power contributions -- gather/fold at a windowed node, inherited
  // attribution, restriction rejection at command entry, and the
  // replay-never-consults-contributions guarantee. Recover's own tree carries
  // no window yet (Task 4), so these trees are built ad hoc with
  // `ProcedureWalkerSuite.WindowedNode` (composites) and a `Decide` carrying a
  // `window` (the one leaf kind that can declare one today).
  // -------------------------------------------------------------------------

  private val testWindow: PowerWindow = PowerWindow.RecoverModifierSelection
  private val otherWindow: PowerWindow = PowerWindow.RecoverBeforeFirstRoll

  test("CardPlayed visits existing played-card window without recording hook") {
    val hook = CardPlayed(adviser.id,
      RuleSourceRef.Adviser(actor, adviser.id))
    assertEquals(hook.children, Vector.empty[Operation])
    assertEquals(hook.window, Some(PowerWindow.ActionCardPlayed))
    val power = transformPower("test.played", PowerWindow.ActionCardPlayed)(
      (_, ops) => SpendSupply(actor, 1) +: ops)
    val (state, steps) = finishedSteps(ProcedureWalker.advance(ready,
      Sequence(hook), None, WalkerPowers(Vector(power))))
    assertEquals(supplyOf(state), SupplyTrack.Maximum - 1)
    assertEquals(steps.map(_.ops), Vector(Vector[CoreOperation](
      SpendSupply(actor, 1))))
  }

  private def transformPower(id: String, hook: PowerWindow)(
      fn: (PowerCtx, Vector[Operation]) => Vector[Operation])
      : ProcedureWalkerSuite.TestTransformPower =
    ProcedureWalkerSuite.TestTransformPower(PowerId(id), hook, fn)

  private def recordedStep(event: OathEvent): WalkerStepRecorded = event match {
    case step: WalkerStepRecorded => step
    case other => fail(s"expected a WalkerStepRecorded, got $other")
  }

  private def finishedSteps(outcome: Either[OathViolation, WalkerOutcome])
      : (ReadyGame, Vector[WalkerStepRecorded]) = outcome match {
    case Right(WalkerOutcome.Finished(state, events)) =>
      (state, events.map(recordedStep))
    case other => fail(s"expected a Finished walk, got $other")
  }

  test("a windowed composite records one event per folded child at the " +
      "child's own node id, whether or not a transform fired") {
    val powerId = PowerId("test.prepend-move")
    val power = transformPower(powerId.value, testWindow)((_, ops) => move +: ops)
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow, Vector(adjust))
    val tree: Operation = Sequence(windowed)

    // No power fires: one event per declared child, at the child's node id.
    val (bare, bareSteps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, noPowers))
    assertEquals(bareSteps.map(_.nodeId), Vector("0.0"))
    assertEquals(bareSteps.map(_.ops), Vector(Vector[CoreOperation](adjust)))
    assertEquals(bareSteps.map(_.contributions), Vector(Vector.empty[PowerId]))
    assertEquals(supplyOf(bare), SupplyTrack.Maximum - 1)

    // A transform fires: SAME shape, one event per folded child at its own
    // node id -- the composite never collapses its children into one event
    // and never emits an event of its own.
    val (finalState, steps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))))
    assertEquals(steps.map(_.nodeId), Vector("0.0", "0.1"))
    assertEquals(steps.map(_.ops), Vector(
      Vector[CoreOperation](move), Vector[CoreOperation](adjust)))
    assertEquals(steps.map(_.contributions), Vector.fill(2)(Vector(powerId)))
    assertEquals(pawnSiteOf(finalState), Some(sites(2)))
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
  }

  test("a Repeat of plain deltas inside a windowed composite still loops and " +
      "still evaluates its guard every pass") {
    // Every leaf under the fold is a plain delta, so a batching collapse that
    // classified the folded vector by flattening it would run the body ONCE
    // with the guard never evaluated -- the loop silently gone.
    var guardCalls = 0
    val guard: (ReadyGame, PendingTree) => Boolean = (state, _) => {
      guardCalls += 1
      supplyOf(state) >= SupplyTrack.Maximum - 2
    }
    val power = transformPower("test.prepend-move", testWindow)(
      (_, ops) => move +: ops)
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow,
      Vector(Repeat(guard, adjust)))
    val tree: Operation = Sequence(windowed)

    val (finalState, steps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))))
    // Supply 7 -> 6 -> 5 -> 4: three passes, four guard evaluations.
    assertEquals(guardCalls, 4)
    assertEquals(steps.map(_.ops), Vector[Vector[CoreOperation]](
      Vector(move), Vector(adjust), Vector(adjust), Vector(adjust)))
    assertEquals(steps.map(_.nodeId),
      Vector("0.0", "0.1.0", "0.1.0", "0.1.0"))
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 3)
    assertEquals(pawnSiteOf(finalState), Some(sites(2)))
  }

  test("a Branch inside a windowed composite still has select called and its " +
      "selection walked") {
    // `Branch.children` is statically empty, so a batching collapse that
    // flattened the folded vector would drop the branch entirely.
    var selects = 0
    val branch = Branch { (_: ReadyGame, _: PendingTree) =>
      selects += 1
      Vector[Operation](adjust)
    }
    val power = transformPower("test.prepend-move", testWindow)(
      (_, ops) => move +: ops)
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow, Vector(branch))
    val tree: Operation = Sequence(windowed)

    val (finalState, steps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))))
    assertEquals(selects, 1)
    assertEquals(steps.map(_.ops), Vector(
      Vector[CoreOperation](move), Vector[CoreOperation](adjust)))
    assertEquals(steps.map(_.nodeId), Vector("0.0", "0.1.0"))
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
    assertEquals(pawnSiteOf(finalState), Some(sites(2)))
  }

  test("a windowed leaf whose transform yields a Decide parks at it, and the " +
      "resume applies the same fold") {
    val powerId = PowerId("test.pay-before-decide")
    val power = transformPower(powerId.value, testWindow)(
      (_, ops) => adjust +: ops)
    val hooked = decide.copy(window = Some(testWindow))
    val tree: Operation = Sequence(hooked)
    val powers = WalkerPowers(Vector(power))

    // The fold turns the leaf into [SpendSupply, Decide]: the delta runs and
    // the walk parks on the Decide the transform left in place (this raised a
    // contract violation before the folded vector was walked as children).
    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        powers) match {
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the folded Decide, got $other")
    }
    assertEquals(parked.at, Vector("0", "1"))
    assertEquals(parkEvents.size, 1)
    val paid = recordedStep(parkEvents.head)
    assertEquals(paid.nodeId, "0.0")
    assertEquals(paid.ops, Vector[CoreOperation](adjust))
    assertEquals(paid.contributions, Vector(powerId))

    // Resuming re-derives the tree and re-applies the SAME fold, so the park
    // position still addresses the Decide.
    val parkedState = applyEvents(ready, parkEvents)
    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(parkedState, tree, parked, answer, powers) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 1)
        val step = recordedStep(events.head)
        assertEquals(step.nodeId, "0.1")
        assert(step.payload.isInstanceOf[ChoicePayload])
        assertEquals(step.contributions, Vector(powerId))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
      case other => fail(s"expected the answered resume to finish, got $other")
    }
  }

  test("a leaf records every enclosing window's contributions, its own " +
      "window's included, de-duplicated") {
    val outerId = PowerId("test.outer")
    val innerId = PowerId("test.inner")
    val keep: (PowerCtx, Vector[Operation]) => Vector[Operation] =
      (_, ops) => ops
    // `outer` speaks at BOTH windows, `inner` only at the leaf's.
    val outer = ProcedureWalkerSuite.TestPower(outerId, Map(
      testWindow -> Vector(Transform(keep)),
      otherWindow -> Vector(Transform(keep))))
    val inner = ProcedureWalkerSuite.TestPower(innerId, Map(
      otherWindow -> Vector(Transform(keep))))
    val powers = WalkerPowers(Vector(outer, inner))
    val hooked = decide.copy(window = Some(otherWindow))
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow,
      Vector(adjust, hooked))
    val tree: Operation = Sequence(windowed)

    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        powers) match {
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the hooked Decide, got $other")
    }
    // A leaf with no window of its own records the enclosing composite's
    // gather order.
    assertEquals(recordedStep(parkEvents.head).contributions, Vector(outerId))
    assertEquals(parked.at, Vector("0", "1", "0"))

    val parkedState = applyEvents(ready, parkEvents)
    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(parkedState, tree, parked, answer, powers) match {
      case Right(WalkerOutcome.Finished(_, events)) =>
        // The leaf's own window gathers `outer` a second time and `inner` for
        // the first: inherited order first, own order after, first occurrence
        // winning.
        assertEquals(recordedStep(events.head).contributions,
          Vector(outerId, innerId))
      case other => fail(s"expected the answered resume to finish, got $other")
    }
  }

  test("a node with no window records contributions as Vector.empty") {
    val tree: Operation = Sequence(adjust)

    ProcedureWalker.advance(ready, tree, None, noPowers) match {
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.size, 1)
        assertEquals(recordedStep(events.head).contributions,
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
    // events, no walk. `OathRulesWalkerPowerSuite` drives the real command.
    val command: Either[OathViolation, WalkerOutcome] =
      violations.headOption.toLeft(()).flatMap(_ =>
        ProcedureWalker.advance(ready, tree, None, powers))
    assertEquals(command, Left(violation))
  }

  test("a Restriction declared inside a Branch's selected children is " +
      "collected") {
    // `Branch.children` is statically empty, so a traversal that read it
    // would never see this window at all.
    val violation: OathViolation = OathViolation.InvalidEventOrder(
      "test restriction forbids this branch")
    val power = ProcedureWalkerSuite.TestRestrictionPower(
      PowerId("test.forbid"), testWindow, (_, _) => Some(violation))
    val branch = Branch((_: ReadyGame, _: PendingTree) => Vector[Operation](
      ProcedureWalkerSuite.WindowedNode(testWindow, Vector(adjust))))
    val tree: Operation = Sequence(branch)

    assertEquals(ProcedureWalker.restrictionViolations(tree,
      WalkerPowers(Vector(power)), ready, actor), Vector(violation))
  }

  test("replay of contributions-carrying events reaches the same state as " +
      "the live walk when no powers are present at replay") {
    val powerId = PowerId("test.prepend-move")
    val power = transformPower(powerId.value, testWindow)((_, ops) => move +: ops)
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow, Vector(adjust))
    val tree: Operation = Sequence(windowed)

    val (finalState, steps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))))
    assertEquals(steps.map(_.contributions), Vector.fill(2)(Vector(powerId)))

    // `applyRecorded` (replay) takes no `WalkerPowers` at all -- it applies
    // `ops` only and never re-gathers or re-transforms (spec decision 5).
    // Folding the SAME events through it, starting fresh from `ready`, must
    // reach the SAME state even though no power is available to replay:
    // `contributions` is an audit fact, not a replay input.
    val replayed = steps.foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready))) {
      case (Right(state), event) => ProcedureWalker.applyRecorded(state, event)
      case (left, _) => left
    }
    assertEquals(replayed, Right(OathState.Ready(finalState)))
  }

  // -------------------------------------------------------------------------
  // Fix-round ruling J: `leafAt`/`resolveAt` (behind `parkedRoll`/
  // `parkedDecide`) must resolve a park position against the FOLDED tree, not
  // the declared one -- an inserting `Transform` at a windowed composite
  // shifts a later sibling's index in the fold without moving it in the
  // composite's own declared `children`. `OathRules.parkedContinue` (and,
  // through it, the client-facing continuation prompt) is the only caller;
  // these tests drive `parkedRoll`/`parkedDecide` directly, synthetically,
  // exactly like Task 3's own `WindowedNode`/`TestTransformPower` doubles --
  // no dependency on Recover or the catalog.
  // -------------------------------------------------------------------------

  test("parkedDecide resolves an inserting transform's shifted index, not " +
      "the windowed composite's declared (unfolded) children") {
    val powerId = PowerId("test.insert-before-decide-park")
    val power = transformPower(powerId.value, testWindow)((_, ops) => adjust +: ops)
    // Declared children of `windowed` are `Vector(decide)` -- one element, at
    // declared index 0.
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow, Vector(decide))
    val tree: Operation = Sequence(windowed)
    val powers = WalkerPowers(Vector(power))

    // The live walk folds `adjust` in FIRST, so the real park is at folded
    // index 1 ("0.1"), one deeper than the declared "0.0" -- the exact shape
    // Task 5's first inserting power (Catacombs, at the root window) produces.
    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        powers) match {
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the folded Decide, got $other")
    }
    assertEquals(parked.at, Vector("0", "1"))
    assertEquals(parkEvents.size, 1)

    val parkedState = applyEvents(ready, parkEvents)

    // Before ruling J's fix, `leafAt` indexed `windowed.children` (the
    // DECLARED, unfolded `Vector(decide)`) directly: index 1 was out of
    // range there, so this returned `None` even though the walk is
    // legitimately parked on a real Decide -- `OathRules.parkedContinue`
    // would have rejected a live client's resume with "parked walker
    // position is neither a Roll nor a Decide".
    assertEquals(ProcedureWalker.parkedDecide(parkedState, tree, parked,
      powers), Some(decide))
    assertEquals(ProcedureWalker.parkedRoll(parkedState, tree, parked,
      powers), None)
  }

  test("parkedRoll resolves an inserting transform's shifted index on a " +
      "windowed composite whose folded Roll sits one index deeper") {
    val powerId = PowerId("test.insert-before-roll-park")
    val power = transformPower(powerId.value, testWindow)((_, ops) => adjust +: ops)
    // Declared children are `Vector(ModifyDicePool(...), defenseRoll)` -- the
    // Roll at declared index 1.
    val windowed = ProcedureWalkerSuite.WindowedNode(testWindow,
      Vector(ModifyDicePool(recoverPool, 2), defenseRoll))
    val tree: Operation = Sequence(windowed)
    val powers = WalkerPowers(Vector(power))

    // Folded: [adjust, ModifyDicePool, Roll] -- the Roll now sits at folded
    // index 2, not its declared index 1.
    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        powers) match {
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the folded Roll, got $other")
    }
    assertEquals(parked.at, Vector("0", "2"))
    assertEquals(parkEvents.size, 2)

    val parkedState = applyEvents(ready, parkEvents)

    // Before ruling J's fix, `leafAt` indexed the declared children directly:
    // index 2 there is out of range (only 2 declared children), so this
    // returned `None` even though the walk legitimately parked on the Roll.
    assertEquals(ProcedureWalker.parkedRoll(parkedState, tree, parked,
      powers), Some((recoverPool, 2)))
    assertEquals(ProcedureWalker.parkedDecide(parkedState, tree, parked,
      powers), None)
  }
}
