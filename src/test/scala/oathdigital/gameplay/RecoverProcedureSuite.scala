package oathdigital.gameplay

import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  RollPayload, WalkerOutcome, WalkerPowers, WalkerStepPayload,
  WalkerStepRecorded}
import oathdigital.gameplay.walker.DeltaMeaning.{RelicAcquired, SupplySpent}
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.gameplay.OathState.Ready
import oathdigital.model._

/** Task 5: the declared Recover tree reproduces the legacy Recover flow on
  * `ProcedureWalker.advance`/`roll`/`resolve` — success (single- and
  * multi-roll), stop-without-success, insufficient-supply rejection, and
  * wrong-relic rejection — with recorded ops per node and the relic moving
  * facedown into the actor's play area.
  */
class RecoverProcedureSuite extends munit.FunSuite
    with WalkerRecordedOpsReducer {
  private val setup = new FirstGameSetupRules(catalog)

  /** Recover declares no window yet (Task 4), so every walk here states an
    * empty power source explicitly -- `advance`/`roll`/`resolve` have no
    * default, so a powered production walk can never be shadowed by a
    * silently unpowered suite.
    */
  private val noPowers: WalkerPowers = WalkerPowers.empty

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
    * already holds), and every other step re-applies its recorded ops. The
    * shared reducer lives in `WalkerRecordedOpsReducer`, which
    * `WalkerReplayDriftSuite`'s "live" state track also folds through.
    */
  private def applyRecorded(state: ReadyGame,
      events: Vector[OathEvent]): ReadyGame =
    foldRecordedOps(state, events, "replay of recorded ops failed")

  private val lowRoll: Vector[DieFace] =
    Vector(DefenseDieFace.Blank, DefenseDieFace.Blank)
  private val highRoll: Vector[DieFace] =
    Vector(DefenseDieFace.TwoShields, DefenseDieFace.TwoShields)

  test("Recover eligibility ignores player role and altered Foundations") {
    val (base, actor, _, _, _) = recoverable
    val campaign = base.game.campaign
    val lineage = campaign.lineages(actor.lineage)
    val altered = FoundationState(FoundationFace.Altered,
      Set(LegacyId("legacy:recover-unrelated")))
    val ready = base.copy(game = base.game.copy(campaign = campaign.copy(
      lineages = campaign.lineages.updated(actor.lineage,
        lineage.copy(role = Role.Chancellor)),
      foundations = campaign.foundations.map { case (number, _) =>
        number -> altered
      })))

    assert(RecoverProcedure.build(catalog, ready, actor.player).isRight)
  }

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
      ProcedureWalker.advance(ready, tree, None, noPowers),
      Vector("1", "0", "1"))
    assertEquals(rollPark.answered, Vector.empty[Answered])
    // The head ModifyDicePool carries `RecoverBeforeFirstRoll` (Task 4): a
    // windowed leaf's folded vector is walked as its own children (ruling G),
    // so an unchanged fold still records the leaf one level deeper than an
    // unwindowed leaf would sit -- "0.0", not "0".
    assertEquals(setupEvents.map(_.asInstanceOf[WalkerStepRecorded].nodeId),
      Vector("0.0", "1.0.0"))
    val stateAtRoll = applyRecorded(ready, setupEvents)

    // 2. A two-shield roll reaches any difficulty <= 4: park at the
    //    success-only relic decision; the roll + its 1-supply payment are
    //    recorded on the way.
    val (relicPark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, highRoll, noPowers),
      Vector("2", "0"))
    assertEquals(relicPark.answered, Vector.empty[Answered])
    val stateAtRelic = applyRecorded(stateAtRoll, rollEvents)
    assertEquals(supplyOf(stateAtRelic, actor.player), SupplyTrack.Maximum - 1)

    // 3. Take the facedown site relic: tree ends; the relic is moved facedown
    //    into the play area.
    val answer = Answered(RecoverProcedure.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(relic.id)), actor.player)
    val (finalState, resolveSteps) =
      ProcedureWalker.resolve(stateAtRelic, tree, relicPark, answer,
      noPowers) match {
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

    // Recorded step shape across the whole walk, in order. The two windowed
    // leaves (the head ModifyDicePool at RecoverBeforeFirstRoll, the relic
    // BuildOps at RecoverAfterRelic) each record one level deeper than their
    // unwindowed siblings ("0.0" and "2.1.0", not "0"/"2.1") for the same
    // reason as the park assertion above; nothing else in the tree shifts.
    val steps = (setupEvents ++ rollEvents ++ resolveSteps)
      .map(_.asInstanceOf[WalkerStepRecorded])
    assertEquals(steps.size, 5)
    assertEquals(steps.map(_.nodeId),
      Vector("0.0", "1.0.0", "1.0.1", "2.0", "2.1.0"))
    assertEquals(steps(0).ops,
      Vector[CoreOperation](ModifyDicePool(pool, 2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll))))
    assertEquals(steps(1).payload, WalkerStepPayload.DeltaRecorded(
      SupplySpent(actor.player, 1)))
    assertEquals(steps(1).ops,
      Vector[CoreOperation](AdjustSupply(actor.player, -1)))
    assert(steps(2).payload.isInstanceOf[RollPayload])
    assertEquals(steps(2).ops, Vector.empty[CoreOperation])
    assertEquals(steps(3).payload,
      ChoicePayload(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(relic.id)), actor.player))
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
      ProcedureWalker.advance(ready, tree, None, noPowers),
      Vector("1", "0", "1"))
    val stateAtRoll1 = applyRecorded(ready, setupEvents)

    // First roll fails (score 0 < difficulty): parks the choice decision.
    val (choicePark, firstRollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll1, tree, rollPark1, lowRoll, noPowers),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(stateAtRoll1, firstRollEvents)
    assertEquals(supplyOf(stateAtChoice, actor.player), SupplyTrack.Maximum - 1)

    // Continue -> fresh pass parks at the second Roll.
    val continue = Answered(RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor.player)
    val (rollPark2, continueEvents) = expectParked(
      ProcedureWalker.resolve(stateAtChoice, tree, choicePark, continue,
        noPowers),
      Vector("1", "0", "1"))
    assertEquals(continueEvents.size, 2)
    assertEquals(continueEvents.head.asInstanceOf[WalkerStepRecorded].payload,
      ChoicePayload(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor.player))
    assertEquals(continueEvents(1).asInstanceOf[WalkerStepRecorded].ops,
      Vector[CoreOperation](AdjustSupply(actor.player, -1)))
    val stateAtRoll2 = applyRecorded(stateAtChoice, continueEvents)

    // Second roll succeeds cumulatively: parks the relic decision.
    val (relicPark, secondRollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll2, tree, rollPark2, highRoll, noPowers),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(stateAtRoll2, secondRollEvents)
    assertEquals(supplyOf(stateAtRelic, actor.player), SupplyTrack.Maximum - 2)

    val answer = Answered(RecoverProcedure.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(relic.id)), actor.player)
    val finalState = ProcedureWalker.resolve(stateAtRelic, tree, relicPark,
      answer, noPowers) match {
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
      ProcedureWalker.advance(ready, tree, None, noPowers),
      Vector("1", "0", "1"))
    val stateAtRoll = applyRecorded(ready, setupEvents)
    val (choicePark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, lowRoll, noPowers),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(stateAtRoll, rollEvents)

    val stop = Answered(RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("stop")), actor.player)
    val (finalState, stopEvents) = ProcedureWalker.resolve(stateAtChoice, tree,
      choicePark, stop, noPowers) match {
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
        ChooseOneAnswer(DecisionOptionRef.Button("stop")), actor.player))
    assertEquals(stopEvents.head.asInstanceOf[WalkerStepRecorded].ops,
      Vector.empty[CoreOperation])
  }

  test("OperationPipeline rejects an unpaid next roll and a supply-zero " +
      "initial roll") {
    val (ready, actor, siteId, _, _) = recoverable
    val poor = withSupply(ready, actor.player, 1)
    val tree = RecoverProcedure.build(catalog, poor, actor.player).toOption.get

    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(poor, tree, None, noPowers),
      Vector("1", "0", "1"))
    val stateAtRoll = applyRecorded(poor, setupEvents)
    val (choicePark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, lowRoll, noPowers),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(stateAtRoll, rollEvents)
    assertEquals(supplyOf(stateAtChoice, actor.player), 0)

    // Continue re-enters the loop, whose payment now precedes its Roll.
    // OperationPipeline rejects that payment before another roll is recorded.
    val continue = Answered(RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor.player)
    ProcedureWalker.resolve(stateAtChoice, tree, choicePark, continue,
      noPowers) match {
      case Left(violation: OathViolation.CoreOperationRejected) =>
        assertEquals(violation.code, "insufficient-supply")
        assert(violation.detail.contains("1 exceeds the 0 available"))
      case other => fail(s"expected an InsufficientSupply rejection, got $other")
    }

    // Build owns only Recover semantics, so zero supply still builds. The
    // generic operation pipeline rejects the first payment before Roll.
    val broke = withSupply(ready, actor.player, 0)
    val brokeTree = RecoverProcedure.build(catalog, broke, actor.player)
      .toOption.get
    assert(ProcedureWalker.advance(broke, brokeTree, None, noPowers)
      .left.toOption.get.isInstanceOf[OathViolation.CoreOperationRejected])
    assert(RecoverProcedure.rebuild(catalog, broke, actor.player).isRight,
      "resume derivation must preserve the legal Stop exit at zero supply")
  }

  test("resolving the relic decision with a relic not facedown at the site is " +
      "rejected") {
    val (ready, actor, _, _, _) = recoverable
    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get

    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers),
      Vector("1", "0", "1"))
    val stateAtRoll = applyRecorded(ready, setupEvents)
    val (relicPark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, highRoll, noPowers),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(stateAtRoll, rollEvents)

    val wrong = Answered(RecoverProcedure.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(RelicId("no-such-relic"))),
      actor.player)
    // The relic is rejected because the rebuilt query, built from the live
    // site, never offered it -- not because a Recover-specific closure
    // checked the site a second time.
    ProcedureWalker.resolve(stateAtRelic, tree, relicPark, wrong,
      noPowers) match {
      case Left(violation: OathViolation.InvalidEventOrder) =>
        assert(violation.detail.contains(
          s"decision ${RecoverProcedure.relicDecisionId} does not offer the " +
            "selected option"),
          s"violation detail '${violation.detail}' should name the decision " +
            "and the undeclared option")
      case other => fail(s"expected a wrong-relic rejection, got $other")
    }
  }

  test("a successful Recover with no facedown relic finishes without a " +
      "relic decision") {
    val (ready, actor, siteId, _, _) = recoverable
    val reliclessSite =
      ready.game.current.map.sites(siteId).copy(relics = Vector.empty)
    val relicless = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(siteId, reliclessSite)))))

    val tree = RecoverProcedure.build(catalog, relicless, actor.player)
      .toOption.get
    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(relicless, tree, None, noPowers),
      Vector("1", "0", "1"))
    val stateAtRoll = applyRecorded(relicless, setupEvents)

    val (finalState, finalEvents) =
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, highRoll, noPowers) match {
        case Right(WalkerOutcome.Finished(treeless, events)) =>
          (treeless, events)
        case other => fail(s"expected relic-less Recover to finish, got $other")
      }

    assertEquals(finalEvents.collect {
      case step: WalkerStepRecorded if step.payload.isInstanceOf[ChoicePayload] =>
        step
    }, Vector.empty)
    assertEquals(RecoverProcedure.actorFacedownRelics(finalState, actor.player),
      Vector.empty)
  }

  /** Every node reachable from `node`, including `node` itself: a `Branch`
    * is resolved by evaluating `select(state, ...)` against `state` (the
    * same way a live walk and the restriction traversal resolve it -- ruling
    * H) rather than reading its statically-empty `children`, so a node
    * gated behind a Branch's selection is only found under the `state` that
    * makes the branch select it.
    */
  private def allNodes(node: Operation, state: ReadyGame,
      actor: PlayerId): Vector[Operation] = {
    val nested = node match {
      case _: PrimitiveOperation => Vector.empty
      case branch: Branch =>
        branch.select(state, PendingTree(Vector.empty, Vector.empty))
          .flatMap(allNodes(_, state, actor))
      case _ => node.children.flatMap(allNodes(_, state, actor))
    }
    node +: nested
  }

  // -------------------------------------------------------------------------
  // The two declared queries: what the client is offered IS what the walker
  // accepts, because they are the same expression.
  // -------------------------------------------------------------------------

  test("the continue/stop decision declares exactly two labelled buttons") {
    val (ready, actor, _, _, _) = recoverable
    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get

    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers),
      Vector("1", "0", "1"))
    val stateAtRoll = applyRecorded(ready, setupEvents)
    val (choicePark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, lowRoll, noPowers),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(stateAtRoll, rollEvents)

    val decide = ProcedureWalker.parkedDecide(stateAtChoice, tree, choicePark,
      noPowers).getOrElse(fail("expected the park to resolve to a Decide"))
    assertEquals(decide.decisionId, RecoverProcedure.choiceDecisionId)
    assertEquals(decide.owner, actor.player)
    assertEquals(decide.query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("continue"), "Continue"),
      DecisionOption.Button(DecisionOptionRef.Button("stop"), "Stop")),
      heading = Some("Recover")))
  }

  test("the relic decision declares one option per live facedown relic, and " +
      "nothing else at the site") {
    val (base, actor, siteId, relic, _) = recoverable
    // A second facedown relic, plus a faceup one and a relic at another site,
    // so the query has something to exclude rather than trivially matching.
    val second = RelicState(base.game.current.commonCards.relicDeck.head,
      Orientation.FaceDown, Tokens.empty)
    val faceUp = RelicState(base.game.current.commonCards.relicDeck(1),
      Orientation.FaceUp, Tokens.empty)
    val site = base.game.current.map.sites(siteId)
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        commonCards = base.game.current.commonCards.copy(
          relicDeck = base.game.current.commonCards.relicDeck.drop(2)),
        map = base.game.current.map.copy(sites =
          base.game.current.map.sites.updated(siteId,
            site.copy(relics = Vector(relic, second, faceUp)))))))

    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get
    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers),
      Vector("1", "0", "1"))
    val stateAtRoll = applyRecorded(ready, setupEvents)
    val (relicPark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, highRoll, noPowers),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(stateAtRoll, rollEvents)

    val decide = ProcedureWalker.parkedDecide(stateAtRelic, tree, relicPark,
      noPowers).getOrElse(fail("expected the park to resolve to a Decide"))
    assertEquals(decide.query, DecisionQuery.ChooseOne(Vector(relic.id,
      second.id).map(id =>
      DecisionOption.Relic(DecisionOptionRef.Relic(id))),
      heading = Some("Take a relic")))

    // And the walker accepts exactly those two, the faceup relic included in
    // neither direction.
    Vector(relic.id, second.id).foreach { id =>
      assert(ProcedureWalker.resolve(stateAtRelic, tree, relicPark,
        Answered(RecoverProcedure.relicDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Relic(id)), actor.player),
        noPowers).isRight,
        s"$id is offered and must be accepted")
    }
    assert(ProcedureWalker.resolve(stateAtRelic, tree, relicPark,
      Answered(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(faceUp.id)), actor.player),
      noPowers).isLeft,
      "a faceup relic is not offered and must not be accepted")
  }

  test("a continue answer submitted after the roll already succeeded is " +
      "rejected because the rebuilt tree no longer declares that node") {
    // This is what replaced Recover's deleted `validateChoice` closure: the
    // `Branch` omits the continue/stop node once the recovery has succeeded,
    // so a stale answer finds no matching `Decide` to resume at and is
    // rejected before any query is consulted.
    val (ready, actor, _, _, _) = recoverable
    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get

    val (rollPark, setupEvents) = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers),
      Vector("1", "0", "1"))
    val stateAtRoll = applyRecorded(ready, setupEvents)

    // The roll succeeds, so the walk parks at the RELIC decision instead.
    val (relicPark, rollEvents) = expectParked(
      ProcedureWalker.roll(stateAtRoll, tree, rollPark, highRoll, noPowers),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(stateAtRoll, rollEvents)

    val stale = Answered(RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor.player)
    ProcedureWalker.resolve(stateAtRelic, tree, relicPark, stale,
      noPowers) match {
      case Left(violation: OathViolation.InvalidEventOrder) =>
        assert(violation.detail.contains(
          RecoverProcedure.choiceDecisionId) &&
          violation.detail.contains(RecoverProcedure.relicDecisionId),
          s"violation detail '${violation.detail}' should name both the " +
            "submitted decision and the one actually parked")
      case other => fail(s"expected a stale-choice rejection, got $other")
    }

    // Resuming at the choice node's own former path fails too: the rebuilt
    // tree selects no child there at all.
    assert(ProcedureWalker.resolve(stateAtRelic, tree,
      relicPark.copy(at = Vector("1", "0", "2", "0")), stale, noPowers).isLeft)
  }

  test("the tree windows exactly its root, its pool-opening node, and its " +
      "relic-moving node; every other node carries none") {
    val (ready, actor, _, _, difficulty) = recoverable
    val tree = RecoverProcedure.build(catalog, ready, actor.player).toOption.get
    val pool = RecoverProcedure.recoverPool

    // `ready` (fresh, no rolls yet) reaches the choice Branch's Decide but
    // not the success-only relic Branch's contents; a state whose pool
    // already scored at/above the site's difficulty reaches the relic
    // Branch's Decide and the relic-moving BuildOps instead. Together the two
    // traversals cover every node the declared tree can ever produce.
    val succeededState = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(rollOutcomes = Map(pool -> RollOutcome(pool, 2,
        Vector(DefenseDieFace.TwoShields, DefenseDieFace.TwoShields), 0,
        difficulty)))))

    val observed = allNodes(tree, ready, actor.player) ++
      allNodes(tree, succeededState, actor.player)
    val windowed = observed.filter(_.window.isDefined).distinct

    assertEquals(windowed.map(_.window).toSet, Set[Option[PowerWindow]](
      Some(PowerWindow.RecoverActionEligibility),
      Some(PowerWindow.RecoverBeforeFirstRoll),
      Some(PowerWindow.RecoverAfterRelic)))
    assertEquals(windowed.size, 3,
      s"expected exactly 3 windowed nodes, found ${windowed.size}: $windowed")
    assert(windowed.contains(tree),
      "the tree root must carry RecoverActionEligibility")
    assert(windowed.exists(_.isInstanceOf[ModifyDicePool]),
      "the head ModifyDicePool must carry RecoverBeforeFirstRoll")
    assert(windowed.count(_.isInstanceOf[BuildOps]) == 1,
      "exactly the relic-moving BuildOps must carry RecoverAfterRelic")
  }
}
