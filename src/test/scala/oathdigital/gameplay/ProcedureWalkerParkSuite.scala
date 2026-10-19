package oathdigital.gameplay

import oathdigital.gameplay.walker.{ChoicePayload, DeltaPayload,
  ProcedureWalker, WalkerOutcome, WalkerStepRecorded}
import oathdigital.testkit.RecordedOps
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.Outcomes.{parked, recorded}
import oathdigital.testkit.Unwrap.accepted
import oathdigital.testkit.WalkerStubs._
import oathdigital.testkit.ProcedureWalkerFixture._

/** ProcedureWalker's parks: a Decide parks and resumes, accepts exactly its
  * declared answers from its owner, and a `Repeat(guard, body)` with a pure
  * command-time guard re-runs, re-parks and finishes.
  */
class ProcedureWalkerParkSuite extends munit.FunSuite:
  test("a Decide at the head parks with no events, then the answered resume finishes"):
    val tree: Operation = Sequence(decide, spendOneSupply)

    val parkedAt = ProcedureWalker.advance(ready, tree, None, noPowers) match
      case Right(WalkerOutcome.Parked(pending, events)) =>
        assertEquals(pending.at, Vector("0"))
        assertEquals(pending.answered, Vector.empty[Answered])
        assertEquals(events, Vector.empty[OathEvent])
        pending
      case other => fail(s"expected a park at the Decide, got $other")

    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(ready, tree, parkedAt, answer, noPowers) match
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 2)
        assertEquals(RecordedOps.of(recorded(events.last)),
          Vector[CoreOperation](spendOneSupply))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
        assertEquals(finalState.game.current.walkerPending, None)
      case other => fail(s"expected the answered resume to finish, got $other")

  /** Walks `tree` to its first Decide park and hands back the park. */
  private def parkAtDecide(tree: Operation): PendingTree =
    parked(ProcedureWalker.advance(ready, tree, None, noPowers)).tree

  // -------------------------------------------------------------------------
  // Generic decision resolution: the walker accepts exactly what a node's
  // declared query states, and nothing about the action that declared it.
  // -------------------------------------------------------------------------

  test("a Decide accepts exactly its declared options and rejects an " +
      "undeclared reference"):
    val tree: Operation = Sequence(decide, spendOneSupply)
    val pending = parkAtDecide(tree)

    Vector(continueOption, stopOption).foreach { option =>
      // Declared, so accepted.
      accepted(ProcedureWalker.resolve(ready, tree, pending,
        Answered(decide.decisionId, ChooseOneAnswer(option), actor),
        noPowers))
    }

    val undeclared = Answered(decide.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("teleport")), actor)
    assertEquals(ProcedureWalker.resolve(ready, tree, pending, undeclared,
      noPowers),
      Left(OathViolation.InvalidEventOrder(
        "decision recover.choice does not offer the selected option"))
        : Either[OathViolation, WalkerOutcome])

  test("a Decide rejects an answer of the wrong shape for its query"):
    val tree: Operation = Sequence(decide, spendOneSupply)
    val pending = parkAtDecide(tree)

    assertEquals(ProcedureWalker.resolve(ready, tree, pending,
      Answered(decide.decisionId,
        DecisionAnswer.PartitionAnswer(Vector.empty), actor),
      noPowers),
      Left(OathViolation.InvalidEventOrder(
        "decision recover.choice expects a single-choice answer"))
        : Either[OathViolation, WalkerOutcome])

  test("a malformed query is rejected as a contract failure before the " +
      "submitted answer is even looked at"):
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
      val tree: Operation = Sequence(node, spendOneSupply)
      val pending = parkAtDecide(tree)
      assertEquals(ProcedureWalker.resolve(ready, tree, pending,
        Answered(node.decisionId, ChooseOneAnswer(continueOption), actor),
        noPowers),
        Left(OathViolation.InvalidEventOrder(
          s"decision recover.choice $detail"))
          : Either[OathViolation, WalkerOutcome])
    }

  test("a Decide answered by anyone but its owner is rejected as " +
      "the wrong player"):
    val tree: Operation = Sequence(decide, spendOneSupply)
    val pending = parkAtDecide(tree)
    val intruder = PlayerId("intruder")

    assertEquals(ProcedureWalker.resolve(ready, tree,
      pending,
      Answered(decide.decisionId, ChooseOneAnswer(continueOption), intruder),
      noPowers),
      Left(OathViolation.WrongPlayer(actor, intruder))
        : Either[OathViolation, WalkerOutcome])

  test("a partition Decide accepts a complete legal placement and rejects " +
      "one that starves a section"):
    val options = Vector("a", "b", "c").map(key =>
      DecisionOption.Button(DecisionOptionRef.Button(key), key.toUpperCase))
    val node = Decide("split", actor, DecisionQuery.Partition(
      Vector(DecisionSection("left", "Left", 2),
        DecisionSection("right", "Right", 1)),
      options))
    val tree: Operation = Sequence(node, spendOneSupply)
    val pending = parkAtDecide(tree)

    def answer(sections: Vector[String]): Answered = Answered("split",
      DecisionAnswer.PartitionAnswer(options.zip(sections).map {
        case (option, section) => DecisionPlacement(option.ref, section)
      }), actor)

    accepted(ProcedureWalker.resolve(ready, tree, pending,
      answer(Vector("left", "left", "right")), noPowers))
    assertEquals(ProcedureWalker.resolve(ready, tree, pending,
      answer(Vector("left", "right", "right")), noPowers),
      Left(OathViolation.InvalidEventOrder(
        "decision split leaves section 'left' below its minimum of 2"))
        : Either[OathViolation, WalkerOutcome])

  test("auto-deltas executed before a park are recorded in the Parked " +
      "events"):
    val tree: Operation = Sequence(spendOneSupply, decide)

    val (parkedAt, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        noPowers) match
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park after the auto-delta, got $other")
    assertEquals(parkedAt.at, Vector("1"))
    assertEquals(parkedAt.answered, Vector.empty[Answered])
    assertEquals(parkEvents.size, 1)
    assertEquals(RecordedOps.of(recorded(parkEvents.head)),
      Vector[CoreOperation](spendOneSupply))

    // The caller folds the Parked events to obtain the state at the park.
    val parkedState = applyEvents(ready, parkEvents)
    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(parkedState, tree, parkedAt, answer,
        noPowers) match
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 1)
        assert(recorded(events.head)
          .payload.isInstanceOf[ChoicePayload])
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
      case other => fail(s"expected the answered resume to finish, got $other")

  test("Repeat re-runs its body while the pure guard holds and records every pass"):
    // Pure state-based guard: full supply 7 loops while >= 5, so three body
    // passes run (7 -> 6 -> 5 -> 4), then the guard fails and the walk
    // continues past the Repeat into the trailing SpendSupply.
    val guard: (ReadyGame, PendingTree) => Boolean =
      (state, _) => supplyOf(state) >= SupplyTrack.Maximum - 2
    val tree: Operation = Sequence(Repeat(guard, spendOneSupply),
      spendOneSupply)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None,
        noPowers) match
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished repeat walk, got $other")
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 4)
    assertEquals(events.size, 4)
    assertEquals(events.collect {
      case WalkerStepRecorded(_, DeltaPayload(ops), _) => ops
    }, Vector.fill(4)(Vector[CoreOperation](spendOneSupply)))

  test("Repeat whose guard is false from the start walks nothing"):
    val tree: Operation = Repeat((_: ReadyGame, _: PendingTree) => false,
      spendOneSupply)

    val (finalState, events) = ProcedureWalker.advance(ready, tree, None,
        noPowers) match
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished skipped repeat, got $other")
    assertEquals(events, Vector.empty[OathEvent])
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum)

  test("a park inside a Repeat body resumes at the same body point"):
    // Guard holds only at full supply; the body Decide parks before the delta
    // runs, so answering the decision and resuming completes the pass, drops
    // supply below the guard, and exits the Repeat — the delta executes once.
    val guard: (ReadyGame, PendingTree) => Boolean =
      (state, _) => supplyOf(state) >= SupplyTrack.Maximum
    val tree: Operation = Sequence(
      Repeat(guard, Sequence(decide, spendOneSupply)))

    val parkedAt = ProcedureWalker.advance(ready, tree, None, noPowers) match
      case Right(WalkerOutcome.Parked(pending, events)) =>
        // Repeat (child 0) > body (child 0) > Decide (child 0).
        assertEquals(pending.at, Vector("0", "0", "0"))
        assertEquals(events, Vector.empty[OathEvent])
        pending
      case other => fail(s"expected a park inside the Repeat body, got $other")

    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(ready, tree, parkedAt, answer, noPowers) match
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 2)
        assertEquals(RecordedOps.of(recorded(events.last)),
          Vector[CoreOperation](spendOneSupply))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
      case other => fail(s"expected the resumed repeat to finish, got $other")

  test("plain advance re-parks a repeated Decide even when an older pass " +
      "answered the same decision ID"):
    val olderAnswer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    val pending = PendingTree(Vector("0", "0", "0"), Vector(olderAnswer))
    val tree: Operation = Sequence(Repeat(
      (_: ReadyGame, _: PendingTree) => true,
      Sequence(decide, spendOneSupply)))

    ProcedureWalker.advance(ready, tree, Some(pending), noPowers) match
      case Right(WalkerOutcome.Parked(reparked, events)) =>
        assertEquals(reparked.at, pending.at)
        assertEquals(reparked.answered, Vector(olderAnswer))
        assertEquals(events, Vector.empty[OathEvent])
      case other => fail(s"expected a re-park at the repeated Decide, got $other")

  test("Finished clears stored pending trees and dice pools from the resulting state"):
    val dirty = ready.updateCurrent(_.copy(
        walkerPending = Some(PendingTree(Vector("0"), Vector.empty)),
        rollPools = Map(PoolKey("recover") -> DicePoolState(3))))

    val (finalState, events) = ProcedureWalker.advance(dirty, spendOneSupply,
        None, noPowers) match
      case Right(WalkerOutcome.Finished(state, recorded)) => (state, recorded)
      case other => fail(s"expected a Finished clear walk, got $other")
    assertEquals(events.size, 1)
    assertEquals(finalState.game.current.walkerPending, None)
    assertEquals(finalState.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
