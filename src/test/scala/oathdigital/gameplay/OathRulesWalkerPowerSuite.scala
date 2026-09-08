package oathdigital.gameplay

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerCtx, PowerResolution, PowerWindow}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{ChoicePayload, ProcedureWalker,
  WalkerCompleted, WalkerParked, WalkerPowers, WalkerStepRecorded}
import oathdigital.gameplay.OathState.Ready
import oathdigital.model._

/** Task 3 wiring at the aggregate boundary: `OathRules` gathers restrictions
  * once per command, at command entry, and rejects the whole command before
  * any node runs.
  *
  * These tests drive REAL commands (`startWalker` and a real resume) instead
  * of re-composing the check the way a unit test of
  * `ProcedureWalker.restrictionViolations` would: delete
  * `OathRules.checkRestrictions` from either call site and a `Left` here
  * becomes a walk, failing the test.
  *
  * No action declares a `window` yet (Task 4 wires Recover's), so the tree a
  * command walks is supplied through `OathRules`' `walkerTree` seam -- the
  * same injectable-default shape `campaignLosingForceRegistry` and
  * `warExhaustionRandomPort` already use, and the production default is what
  * every other walker suite exercises. The wiring under test is generic; the
  * tree only has to carry a hookable node.
  */
class OathRulesWalkerPowerSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)

  private val window: PowerWindow = PowerWindow.RecoverModifierSelection

  private val violation: OathViolation = OathViolation.RecoverUnavailable(
    "a test power forbids this action")

  /** A ready game in the Act phase. The command never reaches Recover's own
    * eligibility gates -- the injected tree source replaces them.
    */
  private def actable: (ReadyGame, PlayerId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(turn = base.game.current.turn.copy(
        phase = Phase.Act))))
    (ready, ready.game.current.turn.activePlayer)
  }

  /** `Sequence(WindowedNode(window, Vector(decide, decide)))`: two decisions,
    * so resolving the first parks again instead of completing the action and
    * the assertions stay inside the walker's own surface. Their ids are the
    * two `OathRules` maps to a client continuation.
    */
  private def hookedTree(actor: PlayerId): Operation = {
    def decide(id: String) = Decide(
      payload = ProcedureWalkerSuite.TestDecisionPayload(id),
      owner = ProcedureWalkerSuite.TestOwner(actor),
      decisionId = id)
    Sequence(ProcedureWalkerSuite.WindowedNode(window, Vector(
      decide(RecoverProcedure.choiceDecisionId),
      decide(RecoverProcedure.relicDecisionId))))
  }

  private def rules(actor: PlayerId, powers: WalkerPowers): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers,
      walkerTree = (_, _, _, _, _) => Right(hookedTree(actor)))

  private def forbidding: WalkerPowers = WalkerPowers(Vector(
    ProcedureWalkerSuite.TestRestrictionPower(PowerId("test.forbid"), window,
      (_, _) => Some(violation))))

  test("a restriction rejects a real startWalker command, appending no events") {
    val (ready, actor) = actable

    // Control: with no power the same command runs and appends events.
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted command to run, got $other")
    }
    assert(started.events.nonEmpty)
    assert(started.events.last.isInstanceOf[WalkerParked])

    // A restriction gathered at the tree's window rejects the whole command.
    // A `Left` carries no transition at all: nothing is appended and the
    // caller's state is untouched.
    assertEquals(
      rules(actor, forbidding).startWalker(Ready(ready), ActionRef.Recover,
        actor),
      Left(violation))
  }

  test("a restriction rejects a real walker resume command, appending no events") {
    val (ready, actor) = actable
    val started = rules(actor, WalkerPowers.empty)
      .startWalker(Ready(ready), ActionRef.Recover, actor) match {
      case Right(transition) => transition
      case other => fail(s"expected the unrestricted start to run, got $other")
    }
    val answer = Answered(RecoverProcedure.choiceDecisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))

    // Control: the resume is legal from this parked state.
    val resumed = rules(actor, WalkerPowers.empty)
      .resolveWalker(started.state, answer)
    val events = resumed match {
      case Right(transition) => transition.events
      case other => fail(s"expected the unrestricted resume to run, got $other")
    }
    assert(events.exists {
      case step: WalkerStepRecorded => step.payload.isInstanceOf[ChoicePayload]
      case _ => false
    })

    assertEquals(rules(actor, forbidding).resolveWalker(started.state, answer),
      Left(violation))
  }

  // -------------------------------------------------------------------------
  // Fix-round ruling I: a player-selected `StartWalker` modifier must persist
  // across a resume -- `walkerResumeContext` reads
  // `CurrentGameState.walkerModifiers` (restored from the durable
  // `WalkerParked` fact), not an empty vector, so the SAME fold applies on
  // every command of one action.
  // -------------------------------------------------------------------------

  private def insertingPower(id: PowerId, actor: PlayerId,
      resolution: PowerResolution): ProcedureWalkerSuite.TestTransformPower =
    ProcedureWalkerSuite.TestTransformPower(id, window,
      (_, ops) => AdjustSupply(actor, -1) +: ops, resolution)

  test("a player-selected modifier chosen at StartWalker is still folded on " +
      "resume, so the resumed park addresses the leaf that actually parked") {
    val (ready, actor) = actable
    val powerId = PowerId("test.insert-adjust")
    val power = insertingPower(powerId, actor, PowerResolution.PlayerSelected)
    val rulesInstance = rules(actor, WalkerPowers(Vector(power)))

    val started = rulesInstance.startWalker(Ready(ready), ActionRef.Recover,
        actor, Vector(powerId)) match {
      case Right(transition) => transition
      case other => fail(s"expected the modifier-selected start to run, got $other")
    }
    // The transform fired at start: the inserted AdjustSupply ran before the
    // first Decide, so the park sits one index deeper than the bare tree
    // would (folded index 1, not declared index 0) -- proof the modifier was
    // in effect for this command.
    val Ready(atFirstPark) = started.state: @unchecked
    assertEquals(atFirstPark.game.current.walkerPending.map(_.at),
      Some(Vector("0", "1")))
    assertEquals(atFirstPark.game.current.walkerModifiers, Vector(powerId))

    val answer = Answered(RecoverProcedure.choiceDecisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))
    // Without ruling I's fix, `walkerResumeContext` would fold this command
    // with an EMPTY modifiers vector: the AdjustSupply would no longer be
    // prepended, the folded vector would shift back by one, and this resume
    // would address the SECOND Decide instead of the first -- a decisionId
    // mismatch, rejected with InvalidEventOrder instead of resolving cleanly.
    val resumed = rulesInstance.resolveWalker(started.state, answer) match {
      case Right(transition) => transition
      case other => fail(
        s"expected the resume to address the parked Decide, got $other")
    }
    assert(resumed.events.exists {
      case step: WalkerStepRecorded => step.payload.isInstanceOf[ChoicePayload]
      case _ => false
    })
  }

  test("the persisted modifiers survive a replay of the event stream, " +
      "without the walker being re-run") {
    val (ready, actor) = actable
    val powerId = PowerId("test.insert-adjust")
    val power = insertingPower(powerId, actor, PowerResolution.PlayerSelected)

    val started = rules(actor, WalkerPowers(Vector(power)))
      .startWalker(Ready(ready), ActionRef.Recover, actor,
        Vector(powerId)) match {
      case Right(transition) => transition
      case other => fail(s"expected the modifier-selected start to run, got $other")
    }

    // Replay applies recorded facts only, through `ProcedureWalker
    // .applyRecorded` -- the identical dispatch `OathRules.evolve` uses for
    // these event types in production (spec decision 5: replay never
    // re-gathers, re-transforms, or re-walks). Reconstructing purely from
    // `started.events` must reach `walkerModifiers == Vector(powerId)`
    // without invoking the walker or the power at all.
    val replayed = started.events.foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready))) {
      case (Right(state), event: WalkerEvent) =>
        ProcedureWalker.applyRecorded(state, event)
      case (Right(state), _) => Right(state)
      case (left, _) => left
    }
    assertEquals(replayed, Right(started.state))
    val Right(Ready(replayedReady)) = replayed: @unchecked
    assertEquals(replayedReady.game.current.walkerModifiers, Vector(powerId))
  }

  test("WalkerCompleted clears walkerModifiers along with walkerPending and " +
      "walkerAction") {
    val (ready, actor) = actable
    val powerId = PowerId("test.insert-adjust")
    val power = insertingPower(powerId, actor, PowerResolution.PlayerSelected)
    val rulesInstance = rules(actor, WalkerPowers(Vector(power)))

    val started = rulesInstance.startWalker(Ready(ready), ActionRef.Recover,
        actor, Vector(powerId)) match {
      case Right(transition) => transition
      case other => fail(s"expected the modifier-selected start to run, got $other")
    }
    val Ready(atFirstPark) = started.state: @unchecked
    assertEquals(atFirstPark.game.current.walkerModifiers, Vector(powerId))

    val firstAnswer = Answered(RecoverProcedure.choiceDecisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))
    val afterFirst = rulesInstance.resolveWalker(started.state,
        firstAnswer) match {
      case Right(transition) => transition
      case other => fail(
        s"expected the first resume to park at the second Decide, got $other")
    }
    val Ready(atSecondPark) = afterFirst.state: @unchecked
    assertEquals(atSecondPark.game.current.walkerModifiers, Vector(powerId))

    val secondAnswer = Answered(RecoverProcedure.relicDecisionId,
      ProcedureWalkerSuite.TestDecisionPayload("continue"))
    val finished = rulesInstance.resolveWalker(afterFirst.state,
        secondAnswer) match {
      case Right(transition) => transition
      case other => fail(s"expected the second resume to finish the tree, got $other")
    }
    assert(finished.events.exists(_.isInstanceOf[WalkerCompleted]))
    val Ready(afterCompletion) = finished.state: @unchecked
    assertEquals(afterCompletion.game.current.walkerModifiers,
      Vector.empty[PowerId])
    assert(afterCompletion.game.current.walkerAction.isEmpty)
    assert(afterCompletion.game.current.walkerPending.isEmpty)
  }

  // -------------------------------------------------------------------------
  // The untested `validateModifiers` branch: a known, PlayerSelected power
  // id whose `applicable` returns false must reject distinctly from an
  // unknown id (already covered in GameApplicationServiceSuite), before the
  // walk ever starts.
  // -------------------------------------------------------------------------

  test("StartWalker rejects a known but inapplicable modifier id before the " +
      "walk runs") {
    val (ready, actor) = actable
    val powerId = PowerId("test.inapplicable")
    val inapplicable = new ContributingPower {
      def id: PowerId = powerId
      def source: RuleSourceRef = RuleSourceRef.GameRule(powerId.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
      override def resolution: PowerResolution = PowerResolution.PlayerSelected
      override def applicable(ctx: PowerCtx): Boolean = false
    }

    rules(actor, WalkerPowers(Vector(inapplicable)))
      .startWalker(Ready(ready), ActionRef.Recover, actor,
        Vector(powerId)) match {
      case Left(rejection: OathViolation.InvalidEventOrder) =>
        assert(rejection.detail.contains("is not applicable"),
          s"violation detail '${rejection.detail}' should mention " +
            "inapplicability")
      case other => fail(s"expected an InvalidEventOrder rejection, got $other")
    }
  }
}
