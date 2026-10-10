package oathdigital.gameplay

import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  RollPayload, WalkerOutcome, WalkerPowers, WalkerStepPayload,
  WalkerStepRecorded}
import oathdigital.gameplay.walker.DeltaMeaning.{RelicAcquired, SupplySpent}
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, as, present, rejected}

/** The declared Recover tree on `ProcedureWalker.advance`/`roll`/`resolve`:
  * success (single- and multi-roll), stop-without-success,
  * insufficient-supply rejection, and wrong-relic rejection, with recorded
  * ops per node and the relic moving facedown into the actor's play area.
  */
class RecoverProcedureSuite extends munit.FunSuite
    with WalkerRecordedOpsReducer:
  /** Every walk here states an empty power source explicitly --
    * `advance`/`roll`/`resolve` have no default, so a powered production
    * walk can never be shadowed by a silently unpowered suite.
    */
  private val noPowers: WalkerPowers = WalkerPowers.empty

  private val actor = p1
  // Fair Isle's Recover difficulty is 3: a two-shield roll of two dice
  // reaches it, and two blanks do not.
  private val recoverSite = CatalogNames.site("Fair Isle")
  private val difficulty = 3
  private val relic = CatalogNames.relic("Dowsing Sticks")
  private val siteRelic = RelicState(relic, Orientation.FaceDown, Tokens.empty)

  /** p1, in Act with full Supply, stands at Fair Isle, which holds Dowsing
    * Sticks facedown and nothing else. */
  private def recoverBoard: Table = Table.start.pawn(actor, at = recoverSite)
    .relicAt(relic, at = recoverSite)

  private def recordedSteps(events: Vector[OathEvent])
      : Vector[WalkerStepRecorded] = events.map(as[WalkerStepRecorded](_))

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

  test("Fair Isle's Recover difficulty is the one the tests assume"):
    assertEquals(RecoverRules.difficulty(catalog, recoverSite),
      Some(difficulty))

  test("Recover eligibility ignores player role and altered Foundations"):
    val base = recoverBoard.ready
    val campaign = base.game.campaign
    val lineage = campaign.lineages(Look(base).player(actor).lineage)
    val altered = FoundationState(FoundationFace.Altered,
      Set(LegacyId("legacy:recover-unrelated")))
    // No Table step reaches lineage roles or foundations.
    val ready = base.updateCampaign(_.copy(
      lineages = campaign.lineages.updated(lineage.id,
        lineage.copy(role = Role.Chancellor)),
      foundations = campaign.foundations.map { case (number, _) =>
        number -> altered
      }))

    assertEquals(accepted(RecoverProcedure.afterModifiers(catalog, ready, actor)).window,
      Some(PowerWindow.RecoverActionEligibility))

  /** A walk parked at `at`: where it parked and what it recorded. */
  private def expectParked(outcome: Either[OathViolation, WalkerOutcome],
      at: Vector[String]): WalkerOutcome.Parked =
    val parked = Outcomes.parked(outcome)
    assertEquals(parked.tree.at, at)
    parked

  test("single-roll success parks the relic decision, then moves a facedown " +
      "relic into the play area for one supply"):
    val ready = recoverBoard.ready
    val pool = RecoverProcedure.recoverPool
    val tree = accepted(RecoverProcedure.afterModifiers(catalog, ready, actor))

    // 1. Fresh walk: the pool, the supply that pays for it, and the roll
    //    that supply buys, with nothing asked along the way. A two-shield
    //    roll reaches Fair Isle's 3, so the walk carries on to the
    //    success-only relic decision and parks there.
    val relicWalk = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers, scripted(highRoll)),
      Vector("2", "0"))
    assertEquals(relicWalk.tree.answered, Vector.empty[Answered])
    // The head ModifyDicePool carries `RecoverBeforeFirstRoll`: a
    // windowed leaf's folded vector is walked as its own children (ruling G),
    // so an unchanged fold still records the leaf one level deeper than an
    // unwindowed leaf would sit -- "0.0", not "0". The payment sits in its
    // own `RecoverCost` window, one level deeper again, and the roll heads
    // its roll step, which follows it with the reroll choice. The roll
    // carries `RecoverRoll`, so it sits one level deeper still.
    assertEquals(recordedSteps(relicWalk.events).map(_.nodeId),
      Vector("0.0", "1.0.0.0", "1.0.1.0.0"))
    val stateAtRelic = applyRecorded(ready, relicWalk.events)
    assertEquals(Look(stateAtRelic).supply(actor), SupplyTrack.Maximum - 1)

    // 2. Take the facedown site relic: tree ends; the relic is moved facedown
    //    into the play area.
    val answer = Answered(RecoverProcedure.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(relic)), actor)
    val resolved = Outcomes.finished(ProcedureWalker.resolve(stateAtRelic, tree,
      relicWalk.tree, answer, noPowers))
    val finalState = resolved.treeless

    assertEquals(Look(finalState).supply(actor), SupplyTrack.Maximum - 1)
    assertEquals(finalState.game.current.map.sites(recoverSite).relics,
      Vector.empty)
    assertEquals(Look(finalState).player(actor).relics
      .map(held => held.id -> held.orientation),
      Vector(relic -> Orientation.FaceDown))
    assertEquals(finalState.game.current.walkerPending, None)
    assertEquals(finalState.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
    assertEquals(finalState.game.current.rollOutcomes(pool).score,
      DefenseDieFace.score(Vector(DefenseDieFace.TwoShields,
        DefenseDieFace.TwoShields)))

    // Recorded step shape across the whole walk, in order. The two windowed
    // leaves (the head ModifyDicePool at RecoverBeforeFirstRoll, the relic
    // BuildOps at RecoverAfterRelic) each record one level deeper than their
    // unwindowed siblings ("0.0" and "2.1.0", not "0"/"2.1") for the same
    // reason as the park assertion above, and so does the payment in its
    // `RecoverCost` window ("1.0.0.0"), and the roll heading its roll step
    // ("1.0.1.0.0", one level deeper again in its `RecoverRoll` window);
    // nothing else in the tree shifts.
    val steps = recordedSteps(relicWalk.events ++ resolved.events)
    assertEquals(steps.size, 5)
    assertEquals(steps.map(_.nodeId),
      Vector("0.0", "1.0.0.0", "1.0.1.0.0", "2.0", "2.1.0"))
    assertEquals(steps(0).ops,
      Vector[CoreOperation](ModifyDicePool(pool, 2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll))))
    assertEquals(steps(1).payload, WalkerStepPayload.DeltaRecorded(
      SupplySpent(actor, 1)))
    assertEquals(steps(1).ops,
      Vector[CoreOperation](SpendSupply(actor, 1)))
    assertEquals(steps(2).payload, RollPayload(pool, highRoll, automatic = true))
    assertEquals(steps(2).ops, Vector.empty[CoreOperation])
    assertEquals(steps(3).payload,
      ChoicePayload(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(relic)), actor))
    assertEquals(steps(3).ops, Vector.empty[CoreOperation])
    assertEquals(steps(4).payload, WalkerStepPayload.DeltaRecorded(
      RelicAcquired(actor, relic, recoverSite)))
    assertEquals(steps(4).ops, Vector[CoreOperation](Move(
      Piece.Card(relic),
      PositionedLocation(Location.Site(recoverSite)),
      PositionedLocation(Location.PlayArea(actor)),
      resultingOrientation = Some(Orientation.FaceDown))))

  test("multi-roll success: a failed roll parks Continue/Stop; Continue rolls " +
      "again and the cumulative score reaches the difficulty"):
    val ready = recoverBoard.ready
    val pool = RecoverProcedure.recoverPool
    val tree = accepted(RecoverProcedure.afterModifiers(catalog, ready, actor))

    // First roll fails (score 0 < difficulty): parks the choice decision.
    // The supply is spent before the dice leave the hand, so a park in front
    // of them would ask the player to confirm a roll they have paid for and
    // cannot decline. The walk rolls and carries on to the first question.
    val choiceWalk = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers, scripted(lowRoll)),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(ready, choiceWalk.events)
    assertEquals(Look(stateAtChoice).supply(actor), SupplyTrack.Maximum - 1)

    // Continue -> a fresh pass pays for two more dice, rolls them, and parks
    // the relic decision the cumulative score has now earned.
    val continue = Answered(RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor)
    val relicWalk = expectParked(
      ProcedureWalker.resolve(stateAtChoice, tree, choiceWalk.tree, continue,
        noPowers, scripted(highRoll)),
      Vector("2", "0"))
    // The answer, the Supply payment and the roll.
    assertEquals(relicWalk.events.size, 3)
    assertEquals(recordedSteps(relicWalk.events).head.payload,
      ChoicePayload(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor))
    assertEquals(recordedSteps(relicWalk.events)(1).ops,
      Vector[CoreOperation](SpendSupply(actor, 1)))
    val stateAtRelic = applyRecorded(stateAtChoice, relicWalk.events)
    assertEquals(Look(stateAtRelic).supply(actor), SupplyTrack.Maximum - 2)

    val answer = Answered(RecoverProcedure.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(relic)), actor)
    val finalState = Outcomes.finished(ProcedureWalker.resolve(stateAtRelic,
      tree, relicWalk.tree, answer, noPowers)).treeless

    assertEquals(Look(finalState).supply(actor), SupplyTrack.Maximum - 2)
    assertEquals(finalState.game.current.rollOutcomes(pool).score,
      DefenseDieFace.score(Vector(DefenseDieFace.Blank,
        DefenseDieFace.Blank)) + DefenseDieFace.score(
        Vector(DefenseDieFace.TwoShields, DefenseDieFace.TwoShields)))
    assertEquals(Look(finalState).player(actor).relics.map(_.id), Vector(relic))

    // Both rolls are recorded individually with their own faces.
    val rollPayloads = recordedSteps(choiceWalk.events ++ relicWalk.events)
      .map(_.payload).collect { case roll: RollPayload => roll }
    assertEquals(rollPayloads.map(_.faces), Vector(lowRoll, highRoll))

  test("stop after a failed roll ends the walk with no relic and one supply " +
      "spent"):
    val ready = recoverBoard.ready
    val tree = accepted(RecoverProcedure.afterModifiers(catalog, ready, actor))

    val choiceWalk = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers,
        scripted(lowRoll)),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(ready, choiceWalk.events)

    val stop = Answered(RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("stop")), actor)
    val stopped = Outcomes.finished(ProcedureWalker.resolve(stateAtChoice, tree,
      choiceWalk.tree, stop, noPowers))
    val finalState = stopped.treeless
    val stopEvents = stopped.events

    assertEquals(Look(finalState).supply(actor), SupplyTrack.Maximum - 1)
    assertEquals(Look(finalState).player(actor).relics, Vector.empty)
    // The facedown relic stays at the site — Stop never offers the relic
    // decision.
    assertEquals(finalState.game.current.map.sites(recoverSite).relics,
      Vector(siteRelic))
    assertEquals(finalState.game.current.walkerPending, None)
    assertEquals(finalState.game.current.rollPools,
      Map.empty[PoolKey, DicePoolState])
    // The answer alone.
    assertEquals(stopEvents.size, 1)
    assertEquals(recordedSteps(stopEvents).head.payload,
      ChoicePayload(RecoverProcedure.choiceDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Button("stop")), actor))
    assertEquals(recordedSteps(stopEvents).head.ops,
      Vector.empty[CoreOperation])

  test("an unaffordable Continue is not offered, and the pipeline rejects " +
      "it when probing is off"):
    val poor = recoverBoard.supply(actor, 1).ready
    val tree = accepted(RecoverProcedure.afterModifiers(catalog, poor, actor))

    val choiceWalk = expectParked(
      ProcedureWalker.advance(poor, tree, None, noPowers,
        scripted(lowRoll)),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(poor, choiceWalk.events)
    assertEquals(Look(stateAtChoice).supply(actor), 0)

    // Continue re-enters the loop, whose payment now precedes its Roll.
    // OperationPipeline rejects that payment before another roll is recorded.
    val continue = Answered(RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor)
    // With the search on, the unaffordable option is not offered at all.
    assertEquals(ProcedureWalker.resolve(stateAtChoice, tree, choiceWalk.tree,
      continue, noPowers).left.toOption, Some(OathViolation.InvalidEventOrder(
        s"decision ${RecoverProcedure.choiceDecisionId} does not offer the " +
          "selected option")))
    val unprobed = noPowers.copy(probing = false)
    val violation = as[OathViolation.CoreOperationRejected](rejected(
      ProcedureWalker.resolve(stateAtChoice, tree, choiceWalk.tree, continue,
        unprobed)))
    assertEquals(violation.code, "insufficient-supply")
    assert(violation.detail.contains("1 exceeds the 0 available"))

  test("a supply-zero initial roll is rejected, and resume derivation " +
      "survives zero Supply"):
    // Build owns only Recover semantics, so zero supply still builds. The
    // generic operation pipeline rejects the first payment before Roll.
    val broke = recoverBoard.supply(actor, 0).ready
    val brokeTree = accepted(RecoverProcedure.build(catalog, broke, actor))
    assertEquals(rejected(ProcedureWalker.advance(broke, brokeTree, None,
      noPowers)), OathViolation.CoreOperationRejected("insufficient-supply",
        "a supply spend of 1 exceeds the 0 available"))
    // Resume derivation must preserve the legal Stop exit at zero supply.
    accepted(RecoverProcedure.rebuild(catalog, broke, actor))

  test("resolving the relic decision with a relic the site does not hold is " +
      "rejected"):
    val ready = recoverBoard.ready
    val tree = accepted(RecoverProcedure.afterModifiers(catalog, ready, actor))

    val relicWalk = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers,
        scripted(highRoll)),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(ready, relicWalk.events)

    val wrong = Answered(RecoverProcedure.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(RelicId("no-such-relic"))),
      actor)
    // The relic is rejected because the rebuilt query, built from the live
    // site, never offered it -- not because a Recover-specific closure
    // checked the site a second time.
    val violation = as[OathViolation.InvalidEventOrder](rejected(
      ProcedureWalker.resolve(stateAtRelic, tree, relicWalk.tree, wrong,
        noPowers)))
    assert(violation.detail.contains(
      s"decision ${RecoverProcedure.relicDecisionId} does not offer the " +
        "selected option"),
      s"violation detail '${violation.detail}' should name the decision " +
        "and the undeclared option")

  test("a successful Recover with no facedown relic finishes without a " +
      "relic decision"):
    val relicless = Table.start.pawn(actor, at = recoverSite).ready

    val tree = accepted(RecoverProcedure.afterModifiers(catalog, relicless, actor))
    val finished = Outcomes.finished(ProcedureWalker.advance(relicless, tree,
      None, noPowers, scripted(highRoll)))
    val finalState = finished.treeless
    val finalEvents = finished.events

    assertEquals(finalEvents.collect {
      case step: WalkerStepRecorded if step.payload.isInstanceOf[ChoicePayload] =>
        step
    }, Vector.empty)
    assertEquals(RecoverProcedure.actorFacedownRelics(finalState, actor),
      Vector.empty)

  /** Every node reachable from `node`, including `node` itself: a `Branch`
    * is resolved by evaluating `select(state, ...)` against `state` (the
    * same way a live walk and the restriction traversal resolve it -- ruling
    * H) rather than reading its statically-empty `children`, so a node
    * gated behind a Branch's selection is only found under the `state` that
    * makes the branch select it.
    */
  private def allNodes(node: Operation, state: ReadyGame,
      actor: PlayerId): Vector[Operation] =
    val nested = node match
      case _: PrimitiveOperation => Vector.empty
      case branch: Branch =>
        branch.select(state, PendingTree(Vector.empty, Vector.empty))
          .flatMap(allNodes(_, state, actor))
      case _ => node.children.flatMap(allNodes(_, state, actor))
    node +: nested

  // -------------------------------------------------------------------------
  // The two declared queries: what the client is offered IS what the walker
  // accepts, because they are the same expression.
  // -------------------------------------------------------------------------

  test("the continue/stop decision declares exactly two labelled buttons"):
    val ready = recoverBoard.ready
    val tree = accepted(RecoverProcedure.afterModifiers(catalog, ready, actor))

    val choiceWalk = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers,
        scripted(lowRoll)),
      Vector("1", "0", "2", "0"))
    val stateAtChoice = applyRecorded(ready, choiceWalk.events)

    val decide = present(ProcedureWalker.parkedDecide(stateAtChoice, tree,
      choiceWalk.tree, noPowers), "the continue/stop decision")
    assertEquals(decide.decisionId, RecoverProcedure.choiceDecisionId)
    assertEquals(decide.owner, actor)
    assertEquals(decide.query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(DecisionOptionRef.Button("continue"), "Continue"),
      DecisionOption.Button(DecisionOptionRef.Button("stop"), "Stop")),
      heading = Some("Recover")))

  test("the relic decision declares one option per live facedown relic, and " +
      "nothing else at the site"):
    // A second facedown relic and a faceup one, so the query has something
    // to exclude rather than trivially matching.
    val second = CatalogNames.relic("Cracked Horn")
    val faceUp = CatalogNames.relic("Bandit Crown")
    val ready = recoverBoard.relicAt(second, at = recoverSite)
      .relicAt(faceUp, at = recoverSite, facedown = false).ready

    val tree = accepted(RecoverProcedure.afterModifiers(catalog, ready, actor))
    val relicWalk = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers,
        scripted(highRoll)),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(ready, relicWalk.events)

    val decide = present(ProcedureWalker.parkedDecide(stateAtRelic, tree,
      relicWalk.tree, noPowers), "the relic decision")
    assertEquals(decide.query, DecisionQuery.ChooseOne(Vector(relic,
      second).map(id =>
      DecisionOption.Relic(DecisionOptionRef.Relic(id))),
      heading = Some("Take a relic"), confirmVerb = Some("Take")))

    // And the walker accepts exactly those two, the faceup relic included in
    // neither direction.
    Vector(relic, second).foreach { id =>
      // Offered, so accepted.
      accepted(ProcedureWalker.resolve(stateAtRelic, tree, relicWalk.tree,
        Answered(RecoverProcedure.relicDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Relic(id)), actor),
        noPowers))
    }
    assertEquals(ProcedureWalker.resolve(stateAtRelic, tree, relicWalk.tree,
      Answered(RecoverProcedure.relicDecisionId,
        ChooseOneAnswer(DecisionOptionRef.Relic(faceUp)), actor),
      noPowers), Left(OathViolation.InvalidEventOrder(
        "decision recover.relic does not offer the selected option")),
      "a faceup relic is not offered and must not be accepted")

  test("a continue answer submitted after the roll already succeeded is " +
      "rejected because the rebuilt tree no longer declares that node"):
    // This is what replaced Recover's deleted `validateChoice` closure: the
    // `Branch` omits the continue/stop node once the recovery has succeeded,
    // so a stale answer finds no matching `Decide` to resume at and is
    // rejected before any query is consulted.
    val ready = recoverBoard.ready
    val tree = accepted(RecoverProcedure.afterModifiers(catalog, ready, actor))

    // The roll succeeds, so the walk parks at the RELIC decision instead.
    val relicWalk = expectParked(
      ProcedureWalker.advance(ready, tree, None, noPowers, scripted(highRoll)),
      Vector("2", "0"))
    val stateAtRelic = applyRecorded(ready, relicWalk.events)

    val stale = Answered(RecoverProcedure.choiceDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Button("continue")), actor)
    val violation = as[OathViolation.InvalidEventOrder](rejected(
      ProcedureWalker.resolve(stateAtRelic, tree, relicWalk.tree, stale,
        noPowers)))
    assert(violation.detail.contains(RecoverProcedure.choiceDecisionId) &&
      violation.detail.contains(RecoverProcedure.relicDecisionId),
      s"violation detail '${violation.detail}' should name both the " +
        "submitted decision and the one actually parked")

    // Resuming at the choice node's own former path fails too: the rebuilt
    // tree selects no child there at all.
    val formerChoicePath = relicWalk.tree.copy(at = Vector("1", "0", "2", "0"))
    assertEquals(ProcedureWalker.resolve(stateAtRelic, tree, formerChoicePath,
      stale, noPowers), Left(OathViolation.InvalidEventOrder("walker contract " +
        "violation: resume path segment '0' out of range for a node with 0 " +
        "children")))

  test("the tree windows exactly its root, its pool-opening node, its " +
      "payment, its roll, its reroll choice and its relic-moving node; every " +
      "other node carries none"):
    val ready = recoverBoard.ready
    val tree = accepted(RecoverProcedure.afterModifiers(catalog, ready, actor))
    val pool = RecoverProcedure.recoverPool

    // `ready` (fresh, no rolls yet) reaches the choice Branch's Decide but
    // not the success-only relic Branch's contents; a state whose pool
    // already scored at/above the site's difficulty reaches the relic
    // Branch's Decide and the relic-moving BuildOps instead. Together the two
    // traversals cover every node the declared tree can ever produce.
    // No Table step records a roll outcome.
    val succeededState = ready.updateCurrent(_.copy(rollOutcomes = Map(
      pool -> RollOutcome(pool, count = 2, faces = highRoll, skulls = 0,
        score = difficulty))))

    val observed = allNodes(tree, ready, actor) ++
      allNodes(tree, succeededState, actor)
    val windowed = observed.filter(_.window.isDefined).distinct

    assertEquals(windowed.map(_.window).toSet, Set[Option[PowerWindow]](
      Some(PowerWindow.RecoverActionEligibility),
      Some(PowerWindow.RecoverBeforeFirstRoll),
      Some(PowerWindow.RecoverCost),
      Some(PowerWindow.RecoverRoll),
      Some(PowerWindow.Reroll),
      Some(PowerWindow.RecoverAfterRelic)))
    assertEquals(windowed.size, 6,
      s"expected exactly 6 windowed nodes, found ${windowed.size}: $windowed")
    assert(windowed.contains(tree),
      "the Recover's root must carry RecoverActionEligibility")
    assertEquals(windowed.collect { case node: ModifyDicePool => node.window },
      Vector(Some(PowerWindow.RecoverBeforeFirstRoll)),
      "the head ModifyDicePool must carry RecoverBeforeFirstRoll")
    assertEquals(windowed.collect { case node: BuildOps => node.window },
      Vector(Some(PowerWindow.RecoverAfterRelic)),
      "exactly the relic-moving BuildOps must carry RecoverAfterRelic")
