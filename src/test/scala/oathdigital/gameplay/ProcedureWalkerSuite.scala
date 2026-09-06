package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
import oathdigital.gameplay.walker.{DecisionPayload, OwnerQuery, ProcedureWalker,
  WalkerCtx, WalkerOutcome, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

object ProcedureWalkerSuite {
  /** Test-only open payload (D2: powers define their own payloads later). */
  final case class TestDecisionPayload(decision: String) extends DecisionPayload
  final case class TestOwner(actor: PlayerId) extends OwnerQuery {
    def owner(ctx: WalkerCtx): Option[PlayerId] = Some(actor)
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
        assertEquals(pending.answered, Vector.empty[String])
        assertEquals(pending.actor, actor)
        assertEquals(events, Vector.empty[OathEvent])
        pending
      case other => fail(s"expected a park at the Decide, got $other")
    }

    // The caller answers the parked decision (appending to `answered`) and
    // stores the pending tree in state for the resumed command.
    val answeredTree = parked.copy(
      answered = parked.answered :+ decide.decisionId)
    val storedState = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(walkerPending = Some(answeredTree))))

    ProcedureWalker.advance(storedState, tree, Some(answeredTree)) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 1)
        assertEquals(events.head.asInstanceOf[WalkerStepRecorded].ops,
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
    assertEquals(parked.answered, Vector.empty[String])
    assertEquals(parkEvents.size, 1)
    assertEquals(parkEvents.head.asInstanceOf[WalkerStepRecorded].ops,
      Vector[CoreOperation](adjust))

    // The caller folds the Parked events to obtain the state at the park.
    val parkedState = applyEvents(ready, parkEvents)
    val answeredTree = parked.copy(
      answered = parked.answered :+ decide.decisionId)
    ProcedureWalker.advance(parkedState, tree, Some(answeredTree)) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events, Vector.empty[OathEvent])
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

    val answeredTree = parked.copy(
      answered = parked.answered :+ decide.decisionId)
    ProcedureWalker.advance(ready, tree, Some(answeredTree)) match {
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 1)
        assertEquals(events.head.asInstanceOf[WalkerStepRecorded].ops,
          Vector[CoreOperation](adjust))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
      case other => fail(s"expected the resumed repeat to finish, got $other")
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
}
