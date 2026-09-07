package oathdigital.gameplay

import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  RollPayload, WalkerOutcome, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.gameplay.walker.DeltaMeaning.{RelicAcquired, SupplySpent}
import oathdigital.model.DecisionPayload.{RecoverChoice,
  RecoverChoicePayload, RecoverRelicPayload}
import oathdigital.gameplay.OathState.Ready
import oathdigital.model._

/** Task 5: the declared Recover tree reproduces the legacy Recover flow on
  * `ProcedureWalker.advance`/`roll`/`resolve` — success (single- and
  * multi-roll), stop-without-success, insufficient-supply rejection, and
  * wrong-relic rejection — with recorded ops per node and the relic moving
  * facedown into the actor's play area.
  */
class RecoverProcedureSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)

  private def supplyOf(state: ReadyGame, player: PlayerId): Int =
    state.game.current.players.find(_.player == player).get.board.supply.supply

  private def withSupply(state: ReadyGame, player: PlayerId,
      amount: Int): ReadyGame =
    state.copy(game = state.game.copy(current = state.game.current.copy(
      players = state.game.current.players.map(p =>
        if (p.player == player)
          p.copy(board = p.board.copy(supply = SupplyTrack(amount)))
        else p))))

  /** Full setup finished; the active player's pawn sits on an in-play Recover
    * site whose difficulty a single two-dice roll can reach (<= 4), with
    * exactly one facedown relic there (taken from the relic deck head, which
    * is removed to conserve card identity). Supply is full.
    */
  private def recoverable: (ReadyGame, PlayerState, SiteId, RelicState, Int) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.players.find(
      _.player == base.game.current.turn.activePlayer).get
    val candidates = base.game.current.map.inPlay.filter(siteId =>
      RecoverRules.difficulty(catalog, siteId).exists(d => d > 0 && d <= 4))
    assert(candidates.nonEmpty,
      "fixture needs an in-play Recover site with difficulty <= 4")
    val siteId = candidates.minBy(site =>
      RecoverRules.difficulty(catalog, site).get)
    val difficulty = RecoverRules.difficulty(catalog, siteId).get
    val relic = RelicState(base.game.current.commonCards.relicDeck.head,
      Orientation.FaceDown, Tokens.empty)
    val site = base.game.current.map.sites(siteId).copy(relics = Vector(relic))
    val moved = active.copy(pawnSite = Some(siteId))
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        turn = base.game.current.turn.copy(phase = Phase.Act),
        commonCards = base.game.current.commonCards.copy(
          relicDeck = base.game.current.commonCards.relicDeck.tail),
        players = base.game.current.players.map(p =>
          if (p.player == active.player) moved else p),
        map = base.game.current.map.copy(sites =
          base.game.current.map.sites.updated(siteId, site)))))
    (ready, moved, siteId, relic, difficulty)
  }

  /** Folds a Parked command's recorded events into the caller's state,
    * mirroring journal replay: RollPayload events re-derive and merge the
    * pool's accumulated roll outcome (state write, no ops), ChoicePayload
    * events change no state here (answered rides the pending tree the caller
    * already holds), and every other step re-applies its recorded ops.
    */
  private def applyRecorded(state: ReadyGame,
      events: Vector[OathEvent]): ReadyGame =
    events.foldLeft(state) { (current, event) =>
      val step = event match {
        case recorded: WalkerStepRecorded => recorded
        case other => fail(s"expected a WalkerStepRecorded, got $other")
      }
      step.payload match {
        case RollPayload(pool, faces) =>
          val outcome = RollOutcome(pool, faces.size, faces, skulls = 0,
            score = DefenseDieFace.score(faces.collect {
              case face: DefenseDieFace => face
            }))
          val accumulated =
            current.game.current.rollOutcomes.get(pool).fold(outcome) {
              previous => RollOutcome(pool,
                previous.count + outcome.count,
                previous.faces ++ outcome.faces,
                previous.skulls + outcome.skulls,
                previous.score + outcome.score)
            }
          current.copy(game = current.game.copy(current =
            current.game.current.copy(rollOutcomes =
              current.game.current.rollOutcomes.updated(pool, accumulated))))
        case _ if step.ops.isEmpty => current
        case _ =>
          OperationPipeline.run(current, step.ops,
            OperationPolicy.Permissive)(Right(_)) match {
            case Right(updated) => updated
            case Left(violation) =>
              fail(s"replay of recorded ops failed: $violation")
          }
      }
    }

  private val lowRoll: Vector[DieFace] =
    Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)
  private val highRoll: Vector[DieFace] =
    Vector(DefenseDieFace.TwoShields, DefenseDieFace.TwoShields)

  private def expectParked(outcome: Either[OathViolation, WalkerOutcome],
      at: Vector[String]): (PendingTree, Vector[OathEvent]) =
    outcome match {
      case Right(WalkerOutcome.Parked(pending, events)) =>
        assertEquals(pending.at, at)
        (pending, events)
      case other => fail(s"expected a Parked outcome at $at, got $other")
    }

  test("single-roll success parks the relic decision, then moves a facedown " +
      "relic into the play area for one supply") {
    val (ready, actor, siteId, relic, _) = recoverable
    val pool = RecoverProcedure.recoverPool
    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get

    // 1. Fresh walk: auto pool setup, then park at the first Roll.
    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(ready, tree, None), Vector("1", "0", "0"))
    assertEquals(rollPark.answered, Vector.empty[Answered])
    assertEquals(setupEvents.map(_.asInstanceOf[WalkerStepRecorded].nodeId),
      Vector("0"))
    val stateAtRoll = applyRecorded(ready, setupEvents)

    // 2. A two-shield roll reaches any difficulty <= 4: park at the
    //    success-only relic decision; the roll + its 1-supply payment are
    //    recorded on the way.
    val (relicPark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, highRoll),
      Vector("2", "0"))
    assertEquals(relicPark.answered, Vector.empty[Answered])
    val stateAtRelic = applyRecorded(stateAtRoll, rollEvents)
    assertEquals(supplyOf(stateAtRelic, actor.player), SupplyTrack.Maximum - 1)

    // 3. Take the facedown site relic: tree ends; the relic is moved facedown
    //    into the play area.
    val answer = Answered(RecoverProcedure.relicDecisionId,
      RecoverRelicPayload(relic.id))
    val (finalState, resolveSteps) =
      ProcedureWalker.resolve(stateAtRelic, tree, relicPark, answer) match {
        case Right(WalkerOutcome.Finished(treeless, events)) => (treeless, events)
        case other => fail(s"expected a Finished resolve, got $other")
      }

    assertEquals(supplyOf(finalState, actor.player), SupplyTrack.Maximum - 1)
    assertEquals(finalState.game.current.map.sites(siteId).relics,
      Vector.empty)
    assertEquals(finalState.game.current.players.find(
      _.player == actor.player).get.relics.map(relic => relic.id -> relic.orientation),
      Vector(relic.id -> Orientation.FaceDown))
    assert(finalState.game.current.walkerPending.isEmpty)
    assertEquals(finalState.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
    assertEquals(finalState.game.current.rollOutcomes(pool).score,
      DefenseDieFace.score(Vector(DefenseDieFace.TwoShields,
        DefenseDieFace.TwoShields)))

    // Recorded step shape across the whole walk, in order:
    val steps = (setupEvents ++ rollEvents ++ resolveSteps)
      .map(_.asInstanceOf[WalkerStepRecorded])
    assertEquals(steps.size, 5)
    assertEquals(steps.map(_.nodeId),
      Vector("0", "1.0.0", "1.0.1", "2.0", "2.1"))
    assertEquals(steps(0).ops,
      Vector[CoreOperation](ModifyDicePool(pool, 2)))
    assert(steps(1).payload.isInstanceOf[RollPayload])
    assertEquals(steps(1).ops, Vector.empty[CoreOperation])
    assertEquals(steps(2).payload, WalkerStepPayload.DeltaRecorded(
      SupplySpent(actor.player, 1)))
    assertEquals(steps(2).ops,
      Vector[CoreOperation](AdjustSupply(actor.player, -1)))
    assertEquals(steps(3).payload,
      ChoicePayload(RecoverProcedure.relicDecisionId,
        RecoverRelicPayload(relic.id)))
    assertEquals(steps(3).ops, Vector.empty[CoreOperation])
    assertEquals(steps(4).payload, WalkerStepPayload.DeltaRecorded(
      RelicAcquired(actor.player, relic.id, siteId)))
    assertEquals(steps(4).ops, Vector[CoreOperation](Move(
      Piece.Card(relic.id),
      PositionedLocation(Location.Site(siteId)),
      PositionedLocation(Location.PlayArea(actor.player)),
      resultingOrientation = Some(Orientation.FaceDown))))
  }

  test("multi-roll success: a failed roll parks Continue/Stop; Continue rolls " +
      "again and the cumulative score reaches the difficulty") {
    val (ready, actor, siteId, relic, _) = recoverable
    val pool = RecoverProcedure.recoverPool
    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get

    val (rollPark1, setupEvents) = expectParked(
      ProcedureWalker.advance(ready, tree, None), Vector("1", "0", "0"))
    val stateAtRoll1 = applyRecorded(ready, setupEvents)

    // First roll fails (score 0 < difficulty): parks the choice decision.
    val (choicePark, firstRollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll1, tree, rollPark1, lowRoll),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(stateAtRoll1, firstRollEvents)
    assertEquals(supplyOf(stateAtChoice, actor.player), SupplyTrack.Maximum - 1)

    // Continue -> fresh pass parks at the second Roll.
    val continue = Answered(RecoverProcedure.choiceDecisionId,
      RecoverChoicePayload(RecoverChoice.Continue))
    val (rollPark2, continueEvents) = expectParked(
      ProcedureWalker.resolve(stateAtChoice, tree, choicePark, continue),
      Vector("1", "0", "0"))
    assertEquals(continueEvents.size, 1)
    assertEquals(continueEvents.head.asInstanceOf[WalkerStepRecorded].payload,
      ChoicePayload(RecoverProcedure.choiceDecisionId,
        RecoverChoicePayload(RecoverChoice.Continue)))
    val stateAtRoll2 = applyRecorded(stateAtChoice, continueEvents)

    // Second roll succeeds cumulatively: parks the relic decision.
    val (relicPark, secondRollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll2, tree, rollPark2, highRoll),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(stateAtRoll2, secondRollEvents)
    assertEquals(supplyOf(stateAtRelic, actor.player), SupplyTrack.Maximum - 2)

    val answer = Answered(RecoverProcedure.relicDecisionId,
      RecoverRelicPayload(relic.id))
    val finalState = ProcedureWalker.resolve(stateAtRelic, tree, relicPark,
      answer) match {
      case Right(WalkerOutcome.Finished(treeless, _)) => treeless
      case other => fail(s"expected a Finished resolve, got $other")
    }

    assertEquals(supplyOf(finalState, actor.player), SupplyTrack.Maximum - 2)
    assertEquals(finalState.game.current.rollOutcomes(pool).score,
      DefenseDieFace.score(Vector(DefenseDieFace.Blank,
        DefenseDieFace.Blank)) + DefenseDieFace.score(
        Vector(DefenseDieFace.TwoShields, DefenseDieFace.TwoShields)))
    assertEquals(finalState.game.current.players.find(
      _.player == actor.player).get.relics.map(_.id), Vector(relic.id))

    // Both rolls are recorded individually with their own faces.
    val rollPayloads = Vector(setupEvents, firstRollEvents, continueEvents,
      secondRollEvents).flatten.map(_.asInstanceOf[WalkerStepRecorded])
      .filter(_.payload.isInstanceOf[RollPayload])
      .map(_.payload.asInstanceOf[RollPayload])
    assertEquals(rollPayloads.map(_.faces), Vector(lowRoll, highRoll))
  }

  test("stop after a failed roll ends the walk with no relic and one supply " +
      "spent") {
    val (ready, actor, siteId, relic, _) = recoverable
    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get

    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(ready, tree, None), Vector("1", "0", "0"))
    val stateAtRoll = applyRecorded(ready, setupEvents)
    val (choicePark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, lowRoll),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(stateAtRoll, rollEvents)

    val stop = Answered(RecoverProcedure.choiceDecisionId,
      RecoverChoicePayload(RecoverChoice.Stop))
    val (finalState, stopEvents) = ProcedureWalker.resolve(stateAtChoice, tree,
      choicePark, stop) match {
      case Right(WalkerOutcome.Finished(treeless, events)) =>
        (treeless, events)
      case other => fail(s"expected a Finished stop, got $other")
    }

    assertEquals(supplyOf(finalState, actor.player), SupplyTrack.Maximum - 1)
    assertEquals(finalState.game.current.players.find(
      _.player == actor.player).get.relics, Vector.empty)
    // The facedown relic stays at the site — Stop never offers the relic
    // decision.
    assertEquals(finalState.game.current.map.sites(siteId).relics,
      Vector(relic))
    assert(finalState.game.current.walkerPending.isEmpty)
    assertEquals(finalState.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
    assertEquals(stopEvents.size, 1)
    assertEquals(stopEvents.head.asInstanceOf[WalkerStepRecorded].payload,
      ChoicePayload(RecoverProcedure.choiceDecisionId,
        RecoverChoicePayload(RecoverChoice.Stop)))
    assertEquals(stopEvents.head.asInstanceOf[WalkerStepRecorded].ops,
      Vector.empty[CoreOperation])
  }

  test("insufficient supply rejects Continue before another roll, and a " +
      "supply-zero start is rejected at build") {
    val (ready, actor, siteId, _, _) = recoverable
    val poor = withSupply(ready, actor.player, 1)
    val tree = RecoverProcedure.build(catalog, poor, actor.player).toOption.get

    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(poor, tree, None), Vector("1", "0", "0"))
    val stateAtRoll = applyRecorded(poor, setupEvents)
    val (choicePark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, lowRoll),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(stateAtRoll, rollEvents)
    assertEquals(supplyOf(stateAtChoice, actor.player), 0)

    // Continue would roll again but the actor cannot pay for another roll:
    // rejected before any roll is recorded.
    val continue = Answered(RecoverProcedure.choiceDecisionId,
      RecoverChoicePayload(RecoverChoice.Continue))
    ProcedureWalker.resolve(stateAtChoice, tree, choicePark, continue) match {
      case Left(violation: OathViolation.InsufficientSupply) =>
        assertEquals(violation.required, 1)
        assertEquals(violation.available, 0)
      case other => fail(s"expected an InsufficientSupply rejection, got $other")
    }

    // Starting the action with no supply at all is rejected up front, exactly
    // like legacy Recover's first-roll check.
    val broke = withSupply(ready, actor.player, 0)
    assert(RecoverProcedure.build(catalog, broke, actor.player)
      .left.toOption.get.isInstanceOf[OathViolation.InsufficientSupply])
    assert(RecoverProcedure.rebuild(catalog, broke, actor.player).isRight,
      "resume derivation must preserve the legal Stop exit at zero supply")
  }

  test("resolving the relic decision with a relic not facedown at the site is " +
      "rejected") {
    val (ready, actor, _, _, _) = recoverable
    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get

    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(ready, tree, None), Vector("1", "0", "0"))
    val stateAtRoll = applyRecorded(ready, setupEvents)
    val (relicPark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, highRoll),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(stateAtRoll, rollEvents)

    val wrong = Answered(RecoverProcedure.relicDecisionId,
      RecoverRelicPayload(RelicId("no-such-relic")))
    ProcedureWalker.resolve(stateAtRelic, tree, relicPark, wrong) match {
      case Left(violation: OathViolation.RecoverOutcomeMismatch) =>
        assert(violation.detail.contains("not a facedown relic at the site"),
          s"violation detail '${violation.detail}' should mention the site " +
            "relic requirement")
      case other => fail(s"expected a wrong-relic rejection, got $other")
    }
  }

  test("build rejects a site with no facedown relic instead of building a " +
      "tree that deadlocks after success") {
    val (ready, actor, siteId, _, _) = recoverable
    val reliclessSite =
      ready.game.current.map.sites(siteId).copy(relics = Vector.empty)
    val relicless = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(siteId, reliclessSite)))))

    RecoverProcedure.build(catalog, relicless, actor.player) match {
      case Left(violation: OathViolation.RecoverUnavailable) =>
        assert(violation.detail.contains("no facedown relic"),
          s"violation detail '${violation.detail}' should mention the " +
            "missing facedown relic")
      case other =>
        fail(s"expected a relic-less Recover start rejection, got $other")
    }
  }
}
