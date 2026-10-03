package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{PowerCtx, Transform}
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  WalkerOutcome, WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
// ProcedureWalkerFixture's ready, with S3 cleared, replaces the fixture's.
import oathdigital.model.TestGameFixtures.{ready as _, _}
import oathdigital.testkit.PowerStubs
import oathdigital.testkit.Outcomes.{finished, recorded}
import oathdigital.testkit.WalkerStubs._
import oathdigital.testkit.ProcedureWalkerFixture._

/** ProcedureWalker's windowed nodes: power contributions gather and fold at a
  * window, a leaf records its windows' contributions, restrictions reject at
  * command entry, and parks resolve against the folded tree.
  */
class ProcedureWalkerWindowSuite extends munit.FunSuite:
  // -------------------------------------------------------------------------
  // Task 3: power contributions -- gather/fold at a windowed node, inherited
  // attribution, restriction rejection at command entry, and the
  // replay-never-consults-contributions guarantee. Recover's own tree carries
  // no window yet (Task 4), so these trees are built ad hoc with
  // `WalkerStubs.WindowedNode` (composites) and a `Decide` carrying a
  // `window` (the one leaf kind that can declare one today).
  // -------------------------------------------------------------------------

  private val testWindow: PowerWindow = PowerWindow.RecoverModifierSelection
  private val otherWindow: PowerWindow = PowerWindow.RecoverBeforeFirstRoll

  test("CardPlayed visits existing played-card window without recording hook"):
    val hook = CardPlayedFaceup(adviser.id,
      RuleSourceRef.Adviser(actor, adviser.id))
    assertEquals(hook.children, Vector.empty[Operation])
    assertEquals(hook.window, Some(PowerWindow.ActionCardPlayedFaceup))
    val power = transformPower("test.played", PowerWindow.ActionCardPlayedFaceup)(
      (_, ops) => SpendSupply(actor, 1) +: ops)
    val FinishedSteps(state, steps) = finishedSteps(ProcedureWalker.advance(
      ready, Sequence(hook), None, WalkerPowers(Vector(power))))
    assertEquals(supplyOf(state), SupplyTrack.Maximum - 1)
    assertEquals(steps.map(_.ops), Vector(Vector[CoreOperation](
      SpendSupply(actor, 1))))

  private def transformPower(id: String, hook: PowerWindow)(
      fn: (PowerCtx, Vector[Operation]) => Vector[Operation])
      : TestTransformPower =
    TestTransformPower(PowerId(id), hook, fn)

  /** A finished walk's state and the steps it recorded. */
  private final case class FinishedSteps(state: ReadyGame,
      steps: Vector[WalkerStepRecorded])

  private def finishedSteps(outcome: Either[OathViolation, WalkerOutcome])
      : FinishedSteps =
    val done = finished(outcome)
    FinishedSteps(done.treeless, done.events.map(recorded))

  test("a windowed composite records one event per folded child at the " +
      "child's own node id, whether or not a transform fired"):
    val powerId = PowerId("test.prepend-move")
    val power = transformPower(powerId.value, testWindow)(
      (_, ops) => movePawnToS3 +: ops)
    val windowed = WindowedNode(testWindow, Vector(spendOneSupply))
    val tree: Operation = Sequence(windowed)

    // No power fires: one event per declared child, at the child's node id.
    val FinishedSteps(bare, bareSteps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, noPowers))
    assertEquals(bareSteps.map(_.nodeId), Vector("0.0"))
    assertEquals(bareSteps.map(_.ops),
      Vector(Vector[CoreOperation](spendOneSupply)))
    assertEquals(bareSteps.map(_.contributions), Vector(Vector.empty[PowerId]))
    assertEquals(supplyOf(bare), SupplyTrack.Maximum - 1)

    // A transform fires: SAME shape, one event per folded child at its own
    // node id -- the composite never collapses its children into one event
    // and never emits an event of its own.
    val FinishedSteps(finalState, steps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))))
    assertEquals(steps.map(_.nodeId), Vector("0.0", "0.1"))
    assertEquals(steps.map(_.ops), Vector(Vector[CoreOperation](movePawnToS3),
      Vector[CoreOperation](spendOneSupply)))
    assertEquals(steps.map(_.contributions), Vector.fill(2)(Vector(powerId)))
    assertEquals(pawnSiteOf(finalState), Some(sites(2)))
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)

  test("a Repeat of plain deltas inside a windowed composite still loops and " +
      "still evaluates its guard every pass"):
    // Every leaf under the fold is a plain delta, so a batching collapse that
    // classified the folded vector by flattening it would run the body ONCE
    // with the guard never evaluated -- the loop silently gone.
    var guardCalls = 0
    val guard: (ReadyGame, PendingTree) => Boolean = (state, _) => {
      guardCalls += 1
      supplyOf(state) >= SupplyTrack.Maximum - 2
    }
    val power = transformPower("test.prepend-move", testWindow)(
      (_, ops) => movePawnToS3 +: ops)
    val windowed = WindowedNode(testWindow,
      Vector(Repeat(guard, spendOneSupply)))
    val tree: Operation = Sequence(windowed)

    val FinishedSteps(finalState, steps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))))
    // Supply 7 -> 6 -> 5 -> 4: three passes, four guard evaluations.
    assertEquals(guardCalls, 4)
    assertEquals(steps.map(_.ops),
      Vector[Vector[CoreOperation]](Vector(movePawnToS3),
      Vector(spendOneSupply), Vector(spendOneSupply), Vector(spendOneSupply)))
    assertEquals(steps.map(_.nodeId),
      Vector("0.0", "0.1.0", "0.1.0", "0.1.0"))
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 3)
    assertEquals(pawnSiteOf(finalState), Some(sites(2)))

  test("a Branch inside a windowed composite still has select called and its " +
      "selection walked"):
    // `Branch.children` is statically empty, so a batching collapse that
    // flattened the folded vector would drop the branch entirely.
    var selects = 0
    val branch = Branch { (_: ReadyGame, _: PendingTree) =>
      selects += 1
      Vector[Operation](spendOneSupply)
    }
    val power = transformPower("test.prepend-move", testWindow)(
      (_, ops) => movePawnToS3 +: ops)
    val windowed = WindowedNode(testWindow, Vector(branch))
    val tree: Operation = Sequence(windowed)

    val FinishedSteps(finalState, steps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))))
    assertEquals(selects, 1)
    assertEquals(steps.map(_.ops), Vector(Vector[CoreOperation](movePawnToS3),
      Vector[CoreOperation](spendOneSupply)))
    assertEquals(steps.map(_.nodeId), Vector("0.0", "0.1.0"))
    assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
    assertEquals(pawnSiteOf(finalState), Some(sites(2)))

  test("a windowed leaf whose transform yields a Decide parks at it, and the " +
      "resume applies the same fold"):
    val powerId = PowerId("test.pay-before-decide")
    val power = transformPower(powerId.value, testWindow)(
      (_, ops) => spendOneSupply +: ops)
    val hooked = decide.copy(window = Some(testWindow))
    val tree: Operation = Sequence(hooked)
    val powers = WalkerPowers(Vector(power))

    // The fold turns the leaf into [SpendSupply, Decide]: the delta runs and
    // the walk parks on the Decide the transform left in place (this raised a
    // contract violation before the folded vector was walked as children).
    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        powers) match
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the folded Decide, got $other")
    assertEquals(parked.at, Vector("0", "1"))
    assertEquals(parkEvents.size, 1)
    val paid = recorded(parkEvents.head)
    assertEquals(paid.nodeId, "0.0")
    assertEquals(paid.ops, Vector[CoreOperation](spendOneSupply))
    assertEquals(paid.contributions, Vector(powerId))

    // Resuming re-derives the tree and re-applies the SAME fold, so the park
    // position still addresses the Decide.
    val parkedState = applyEvents(ready, parkEvents)
    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(parkedState, tree, parked, answer, powers) match
      case Right(WalkerOutcome.Finished(finalState, events)) =>
        assertEquals(events.size, 1)
        val step = recorded(events.head)
        assertEquals(step.nodeId, "0.1")
        assert(step.payload.isInstanceOf[ChoicePayload])
        assertEquals(step.contributions, Vector(powerId))
        assertEquals(supplyOf(finalState), SupplyTrack.Maximum - 1)
      case other => fail(s"expected the answered resume to finish, got $other")

  test("a leaf records every enclosing window's contributions, its own " +
      "window's included, de-duplicated"):
    val outerId = PowerId("test.outer")
    val innerId = PowerId("test.inner")
    val keep: (PowerCtx, Vector[Operation]) => Vector[Operation] =
      (_, ops) => ops
    // `outer` speaks at BOTH windows, `inner` only at the leaf's.
    val outer = PowerStubs.ContributingStub(outerId, Map(
      testWindow -> Vector(Transform(keep)),
      otherWindow -> Vector(Transform(keep))))
    val inner = PowerStubs.ContributingStub(innerId, Map(
      otherWindow -> Vector(Transform(keep))))
    val powers = WalkerPowers(Vector(outer, inner))
    val hooked = decide.copy(window = Some(otherWindow))
    val windowed = WindowedNode(testWindow,
      Vector(spendOneSupply, hooked))
    val tree: Operation = Sequence(windowed)

    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        powers) match
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the hooked Decide, got $other")
    // A leaf with no window of its own records the enclosing composite's
    // gather order.
    assertEquals(recorded(parkEvents.head).contributions, Vector(outerId))
    assertEquals(parked.at, Vector("0", "1", "0"))

    val parkedState = applyEvents(ready, parkEvents)
    val answer = Answered(decide.decisionId,
      ChooseOneAnswer(continueOption), actor)
    ProcedureWalker.resolve(parkedState, tree, parked, answer, powers) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        // The leaf's own window gathers `outer` a second time and `inner` for
        // the first: inherited order first, own order after, first occurrence
        // winning.
        assertEquals(recorded(events.head).contributions,
          Vector(outerId, innerId))
      case other => fail(s"expected the answered resume to finish, got $other")

  test("a Restriction's violation is collected before any node runs"):
    val violation: OathViolation = OathViolation.InvalidEventOrder(
      "test restriction forbids this action")
    val power = TestRestrictionPower(
      PowerId("test.forbid"), testWindow, (_, _) => Some(violation))
    val windowed = WindowedNode(testWindow, Vector(spendOneSupply))
    val tree: Operation = Sequence(windowed)
    val powers = WalkerPowers(Vector(power))

    val violations = ProcedureWalker.restrictionViolations(tree, powers,
      ready, actor)
    assertEquals(violations, Vector(violation))

  test("a Restriction declared inside a Branch's selected children is " +
      "collected"):
    // `Branch.children` is statically empty, so a traversal that read it
    // would never see this window at all.
    val violation: OathViolation = OathViolation.InvalidEventOrder(
      "test restriction forbids this branch")
    val power = TestRestrictionPower(
      PowerId("test.forbid"), testWindow, (_, _) => Some(violation))
    val branch = Branch((_: ReadyGame, _: PendingTree) => Vector[Operation](
      WindowedNode(testWindow, Vector(spendOneSupply))))
    val tree: Operation = Sequence(branch)

    assertEquals(ProcedureWalker.restrictionViolations(tree,
      WalkerPowers(Vector(power)), ready, actor), Vector(violation))

  test("replay of contributions-carrying events reaches the same state as " +
      "the live walk when no powers are present at replay"):
    val powerId = PowerId("test.prepend-move")
    val power = transformPower(powerId.value, testWindow)(
      (_, ops) => movePawnToS3 +: ops)
    val windowed = WindowedNode(testWindow, Vector(spendOneSupply))
    val tree: Operation = Sequence(windowed)

    val FinishedSteps(finalState, steps) = finishedSteps(
      ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(power))))
    assertEquals(steps.map(_.contributions), Vector.fill(2)(Vector(powerId)))

    // `applyRecorded` (replay) takes no `WalkerPowers` at all -- it applies
    // `ops` only and never re-gathers or re-transforms (spec decision 5).
    // Folding the SAME events through it, starting fresh from `ready`, must
    // reach the SAME state even though no power is available to replay:
    // `contributions` is an audit fact, not a replay input.
    val replayed = steps.foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready))):
      case (Right(state), event) => ProcedureWalker.applyRecorded(state, event)
      case (left, _) => left
    assertEquals(replayed, Right(OathState.Ready(finalState)))

  // -------------------------------------------------------------------------
  // Fix-round ruling J: `leafAt`/`resolveAt` (behind `parkedRoll`/
  // `parkedDecide`) must resolve a park position against the FOLDED tree, not
  // the declared one -- an inserting `Transform` at a windowed composite
  // shifts a later sibling's index in the fold without moving it in the
  // composite's own declared `children`. `OathRulesWalker.checkAnswerable`
  // is the only caller;
  // these tests drive `parkedRoll`/`parkedDecide` directly, synthetically,
  // exactly like Task 3's own `WindowedNode`/`TestTransformPower` doubles --
  // no dependency on Recover or the catalog.
  // -------------------------------------------------------------------------

  test("parkedDecide resolves an inserting transform's shifted index, not " +
      "the windowed composite's declared (unfolded) children"):
    val powerId = PowerId("test.insert-before-decide-park")
    val power = transformPower(powerId.value, testWindow)(
      (_, ops) => spendOneSupply +: ops)
    // Declared children of `windowed` are `Vector(decide)` -- one element, at
    // declared index 0.
    val windowed = WindowedNode(testWindow, Vector(decide))
    val tree: Operation = Sequence(windowed)
    val powers = WalkerPowers(Vector(power))

    // The live walk folds `spendOneSupply` in FIRST, so the real park is at
    // folded index 1 ("0.1"), one deeper than the declared "0.0" -- the exact
    // shape Task 5's first inserting power (Catacombs, at the root window)
    // produces.
    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        powers) match
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the folded Decide, got $other")
    assertEquals(parked.at, Vector("0", "1"))
    assertEquals(parkEvents.size, 1)

    val parkedState = applyEvents(ready, parkEvents)

    // Before ruling J's fix, `leafAt` indexed `windowed.children` (the
    // DECLARED, unfolded `Vector(decide)`) directly: index 1 was out of
    // range there, so this returned `None` even though the walk is
    // legitimately parked on a real Decide -- `OathRulesWalker.checkAnswerable`
    // would have rejected a live client's resume with "parked walker
    // position is neither a Roll nor a Decide".
    assertEquals(ProcedureWalker.parkedDecide(parkedState, tree, parked,
      powers), Some(decide))
    assertEquals(ProcedureWalker.parkedRoll(parkedState, tree, parked,
      powers), None)

  test("parkedRoll resolves an inserting transform's shifted index on a " +
      "windowed composite whose folded Roll sits one index deeper"):
    val powerId = PowerId("test.insert-before-roll-park")
    val power = transformPower(powerId.value, testWindow)(
      (_, ops) => spendOneSupply +: ops)
    // Declared children are `Vector(ModifyDicePool(...), defenseRoll)` -- the
    // Roll at declared index 1.
    val windowed = WindowedNode(testWindow,
      Vector(ModifyDicePool(recoverPool, 2), defenseRoll))
    val tree: Operation = Sequence(windowed)
    val powers = WalkerPowers(Vector(power))

    // Folded: [spendOneSupply, ModifyDicePool, Roll] -- the Roll now sits at
    // folded index 2, not its declared index 1.
    val (parked, parkEvents) = ProcedureWalker.advance(ready, tree, None,
        powers) match
      case Right(WalkerOutcome.Parked(pending, events)) => (pending, events)
      case other => fail(s"expected a park at the folded Roll, got $other")
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
